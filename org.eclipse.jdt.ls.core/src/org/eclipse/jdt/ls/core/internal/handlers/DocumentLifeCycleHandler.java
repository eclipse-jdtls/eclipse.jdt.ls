/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.SharedASTProvider;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.Severity;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public class DocumentLifeCycleHandler {

	private JavaClientConnection connection;
	private PreferenceManager preferenceManager;
	private ProjectsManager projectsManager;

	private SharedASTProvider sharedASTProvider;
	private WorkspaceJob validationTimer;

	private Set<ICompilationUnit> toReconcile = new HashSet<>();

	public DocumentLifeCycleHandler(JavaClientConnection connection, PreferenceManager preferenceManager, ProjectsManager projectsManager, boolean delayValidation) {
		this.connection = connection;
		this.preferenceManager = preferenceManager;
		this.projectsManager = projectsManager;
		this.sharedASTProvider = SharedASTProvider.getInstance();
		if (delayValidation) {
			this.validationTimer = new WorkspaceJob("Validate documents") {
				@Override
				public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
					return performValidation(monitor);
				}
			};
			this.validationTimer.setRule(ResourcesPlugin.getWorkspace().getRoot());
		}
	}

	private void triggerValidation(ICompilationUnit cu) throws JavaModelException {
		synchronized (toReconcile) {
			toReconcile.add(cu);
		}
		if (validationTimer != null) {
			validationTimer.cancel();
			validationTimer.schedule(400);
		} else {
			performValidation(new NullProgressMonitor());
		}

	}

	private IStatus performValidation(IProgressMonitor monitor) throws JavaModelException {
		long start = System.currentTimeMillis();

		List<ICompilationUnit> cusToReconcile = new ArrayList<>();
		synchronized (toReconcile) {
			cusToReconcile.addAll(toReconcile);
			toReconcile.clear();
		}
		if (cusToReconcile.isEmpty()) {
			return Status.OK_STATUS;
		}

		// first reconcile all units with content changes
		SubMonitor progress = SubMonitor.convert(monitor, cusToReconcile.size() + 1);
		for (ICompilationUnit cu : cusToReconcile) {
			cu.reconcile(ICompilationUnit.NO_AST, true, null, progress.newChild(1));
		}
		this.sharedASTProvider.invalidateAll();

		List<ICompilationUnit> toValidate = Arrays.asList(JavaCore.getWorkingCopies(null));
		List<CompilationUnit> astRoots = this.sharedASTProvider.getASTs(toValidate, monitor);
		for (CompilationUnit astRoot : astRoots) {
			// report errors, even if there are no problems in the file: The client need to know that they got fixed.
			DiagnosticsHandler handler = new DiagnosticsHandler(connection, (ICompilationUnit) astRoot.getTypeRoot());
			handler.beginReporting();

			for (IProblem problem : astRoot.getProblems()) {
				handler.acceptProblem(problem);
			}
			handler.endReporting();
		}
		JavaLanguageServerPlugin.logInfo("Reconciled " + toReconcile.size() + ", validated: " + toValidate.size() + ". Took " + (System.currentTimeMillis() - start) + " ms");
		return Status.OK_STATUS;
	}

	public void didClose(DidCloseTextDocumentParams params) {
		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					handleClosed(params);
				}
			}, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Handle document close ", e);
		}
	}

	public void didOpen(DidOpenTextDocumentParams params) {
		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					handleOpen(params);
				}
			}, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Handle document open ", e);
		}
	}

	public void didChange(DidChangeTextDocumentParams params) {
		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					handleChanged(params);
				}
			}, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Handle document change ", e);
		}
	}

	public void didSave(DidSaveTextDocumentParams params) {
		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					handleSaved(params);
				}
			}, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Handle document save ", e);
		}
	}

	public void handleOpen(DidOpenTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		if (unit == null || unit.getResource() == null) {
			return;
		}
		try {
			// The open event can happen before the workspace element added event when a new file is added.
			// checks if the underlying resource exists and refreshes to sync the newly created file.
			if(!unit.getResource().isAccessible()){
				try {
					unit.getResource().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
				} catch (CoreException e) {
					// ignored
				}
			}
			//Resources belonging to the default project can only report syntax errors, because the project classpath is incomplete
			IProject project = unit.getResource().getProject();
			boolean reportOnlySyntaxErrors = project.equals(JavaLanguageServerPlugin.getProjectsManager().getDefaultProject());
			if (reportOnlySyntaxErrors) {
				Severity severity = preferenceManager.getPreferences(project).getIncompleteClasspathSeverity();
				String msg = "Classpath is incomplete. Only syntax errors will be reported";
				JavaLanguageServerPlugin.logInfo(msg +" for "+uri);
				if (severity.compareTo(Preferences.Severity.ignore) > 0){
					ActionableNotification ignoreIncompleteClasspath = new ActionableNotification()
							.withSeverity(severity.toMessageType())
							.withMessage(msg)
							.withCommands(Arrays.asList(
									new Command("More Information", "java.ignoreIncompleteClasspath.help", null),
									new Command("Don't Show Again", "java.ignoreIncompleteClasspath", null)
									));
					connection.sendActionableNotification(ignoreIncompleteClasspath);
				}
			}

			//			DiagnosticsHandler problemRequestor = new DiagnosticsHandler(connection, unit.getResource(), reportOnlySyntaxErrors);
			unit.becomeWorkingCopy(new NullProgressMonitor());
			IBuffer buffer = unit.getBuffer();
			String newContent = params.getTextDocument().getText();
			if (buffer != null && !buffer.getContents().equals(newContent)) {
				buffer.setContents(newContent);
			}
			triggerValidation(unit);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error while opening document", e);
		}
	}

	public void handleChanged(DidChangeTextDocumentParams params) {
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());

		if (unit == null || !unit.isWorkingCopy() || params.getContentChanges().isEmpty()) {
			return;
		}

		try {
			sharedASTProvider.invalidate(unit);
			List<TextDocumentContentChangeEvent> contentChanges = params.getContentChanges();
			for (TextDocumentContentChangeEvent changeEvent : contentChanges) {

				Range range = changeEvent.getRange();
				int length;

				if (range != null) {
					length = changeEvent.getRangeLength().intValue();
				} else {
					// range is optional and if not given, the whole file content is replaced
					length = unit.getSource().length();
					range = JDTUtils.toRange(unit, 0, length);
				}

				int startOffset = JsonRpcHelpers.toOffset(unit.getBuffer(), range.getStart().getLine(), range.getStart().getCharacter());

				TextEdit edit = null;
				String text = changeEvent.getText();
				if (length == 0) {
					edit = new InsertEdit(startOffset, text);
				} else if (text.isEmpty()){
					edit = new DeleteEdit(startOffset, length);
				} else {
					edit = new ReplaceEdit(startOffset, length, text);
				}
				IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
				edit.apply(document, TextEdit.NONE);
			}
			triggerValidation(unit);
		} catch (JavaModelException | MalformedTreeException | BadLocationException e) {
			JavaLanguageServerPlugin.logException("Error while handling document change", e);
		}
	}

	public void handleClosed(DidCloseTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		if (unit == null) {
			return;
		}
		try {
			sharedASTProvider.invalidate(unit);
			unit.discardWorkingCopy();
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Error while handling document close", e);
		}
	}

	public void handleSaved(DidSaveTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		IFileBuffer fileBuffer = FileBuffers.getTextFileBufferManager().getFileBuffer(unit.getPath(), LocationKind.IFILE);
		if (fileBuffer != null) {
			fileBuffer.setDirty(false);
		}
		if (unit != null && unit.isWorkingCopy()) {
			projectsManager.fileChanged(uri, CHANGE_TYPE.CHANGED);
		}
	}

}
