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

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.ui.javaeditor.HighlightedPositionCore;
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.DocumentAdapter;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.highlighting.SemanticHighlightingService;
import org.eclipse.jdt.ls.core.internal.highlighting.SemanticHighlightingService.HighlightedPositionDiffContext;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.Severity;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import com.google.common.collect.Iterables;

public class DocumentLifeCycleHandler {

	public static final String DOCUMENT_LIFE_CYCLE_JOBS = "DocumentLifeCycleJobs";
	private JavaClientConnection connection;
	private PreferenceManager preferenceManager;
	private ProjectsManager projectsManager;

	private CoreASTProvider sharedASTProvider;
	private WorkspaceJob validationTimer;
	private Set<ICompilationUnit> toReconcile = new HashSet<>();
	private SemanticHighlightingService semanticHighlightingService;

	public DocumentLifeCycleHandler(JavaClientConnection connection, PreferenceManager preferenceManager, ProjectsManager projectsManager, boolean delayValidation) {
		this.connection = connection;
		this.preferenceManager = preferenceManager;
		this.projectsManager = projectsManager;
		this.sharedASTProvider = CoreASTProvider.getInstance();
		this.semanticHighlightingService = new SemanticHighlightingService(this.connection, this.sharedASTProvider, this.preferenceManager);
		if (delayValidation) {
			this.validationTimer = new WorkspaceJob("Validate documents") {
				@Override
				public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
					return performValidation(monitor);
				}

				/* (non-Javadoc)
				 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
				 */
				@Override
				public boolean belongsTo(Object family) {
					return DOCUMENT_LIFE_CYCLE_JOBS.equals(family);
				}
			};
			this.validationTimer.setRule(ResourcesPlugin.getWorkspace().getRoot());
		}
	}

	private void triggerValidation(ICompilationUnit cu) throws JavaModelException {
		triggerValidation(cu, 400);
	}

	private void triggerValidation(ICompilationUnit cu, long delay) throws JavaModelException {
		synchronized (toReconcile) {
			toReconcile.add(cu);
			sharedASTProvider.setActiveJavaElement(cu);
		}
		if (validationTimer != null) {
			validationTimer.cancel();
			validationTimer.schedule(delay);
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
		this.sharedASTProvider.disposeAST();
		List<ICompilationUnit> toValidate = Arrays.asList(JavaCore.getWorkingCopies(null));
		List<CompilationUnit> astRoots = new ArrayList<>();
		for (ICompilationUnit rootToValidate : toValidate) {
			CompilationUnit astRoot = this.sharedASTProvider.getAST(rootToValidate, CoreASTProvider.WAIT_YES, monitor);
			astRoots.add(astRoot);
		}
		for (CompilationUnit astRoot : astRoots) {
			// report errors, even if there are no problems in the file: The client need to know that they got fixed.
			ICompilationUnit unit = (ICompilationUnit) astRoot.getTypeRoot();
			publishDiagnostics(unit, progress.newChild(1));
		}
		JavaLanguageServerPlugin.logInfo("Reconciled " + toReconcile.size() + ", validated: " + toValidate.size() + ". Took " + (System.currentTimeMillis() - start) + " ms");
		return Status.OK_STATUS;
	}

	private void publishDiagnostics(ICompilationUnit unit, IProgressMonitor monitor) throws JavaModelException {
		final DiagnosticsHandler handler = new DiagnosticsHandler(connection, unit);
		WorkingCopyOwner wcOwner = new WorkingCopyOwner() {

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.core.WorkingCopyOwner#createBuffer(org.eclipse.jdt.core.ICompilationUnit)
			 */
			@Override
			public IBuffer createBuffer(ICompilationUnit workingCopy) {
				ICompilationUnit original = workingCopy.getPrimary();
				IResource resource = original.getResource();
				if (resource instanceof IFile) {
					return new DocumentAdapter(workingCopy, (IFile) resource);
				}
				return DocumentAdapter.Null;
			}

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.core.WorkingCopyOwner#getProblemRequestor(org.eclipse.jdt.core.ICompilationUnit)
			 */
			@Override
			public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) {
				return handler;
			}

		};
		unit.reconcile(ICompilationUnit.NO_AST, true, wcOwner, monitor);
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
		if (unit == null || unit.getResource() == null || unit.getResource().isDerived()) {
			return;
		}
		try {
			// The open event can happen before the workspace element added event when a new file is added.
			// checks if the underlying resource exists and refreshes to sync the newly created file.
			if (!unit.getResource().isAccessible()) {
				try {
					unit.getResource().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
					if (unit.getResource().exists()) {
						IJavaElement parent = unit.getParent();
						if (parent instanceof IPackageFragment) {
							IPackageFragment pkg = (IPackageFragment) parent;
							unit = pkg.createCompilationUnit(unit.getElementName(), unit.getSource(), true, new NullProgressMonitor());
						}
					}
				} catch (CoreException e) {
					// ignored
				}
			}
			IProject project = unit.getResource().getProject();
			// Resources belonging to the default project can only report syntax errors, because the project classpath is incomplete
			boolean isDefaultProject = project.equals(JavaLanguageServerPlugin.getProjectsManager().getDefaultProject());
			if (isDefaultProject || !JDTUtils.isOnClassPath(unit)) {
				Severity severity = preferenceManager.getPreferences(project).getIncompleteClasspathSeverity();
				String msg;
				if (isDefaultProject) {
					msg = "Classpath is incomplete. Only syntax errors will be reported";
				} else {
					msg = unit.getElementName() + " isn't on the classpath. Only syntax errors will be reported";
				}
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
			installSemanticHighlightings(unit);
			// see https://github.com/redhat-developer/vscode-java/issues/274
			checkPackageDeclaration(uri, unit);
		} catch (JavaModelException | BadPositionCategoryException e) {
			JavaLanguageServerPlugin.logException("Error while opening document. URI: " + uri, e);
		}
	}

	public void handleChanged(DidChangeTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);

		if (unit == null || !unit.isWorkingCopy() || params.getContentChanges().isEmpty() || unit.getResource().isDerived()) {
			return;
		}

		try {
			if (unit.equals(sharedASTProvider.getActiveJavaElement())) {
				sharedASTProvider.disposeAST();
			}
			List<TextDocumentContentChangeEvent> contentChanges = params.getContentChanges();
			List<HighlightedPositionDiffContext> diffContexts = newArrayList();
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
				} else if (text.isEmpty()) {
					edit = new DeleteEdit(startOffset, length);
				} else {
					edit = new ReplaceEdit(startOffset, length, text);
				}

				// Avoid any computation if the `SemanticHighlightingService#isEnabled` is `false`.
				if (semanticHighlightingService.isEnabled()) {
					IDocument oldState = new Document(unit.getBuffer().getContents());
					IDocument newState = JsonRpcHelpers.toDocument(unit.getBuffer());
					//@formatter:off
					List<HighlightedPositionCore> oldPositions = diffContexts.isEmpty()
						? semanticHighlightingService.getHighlightedPositions(uri)
						: Iterables.getLast(diffContexts).newPositions;
					//@formatter:on
					edit.apply(newState, TextEdit.NONE);
					// This is a must. Make the document immutable.
					// Otherwise, any consecutive `newStates` get out-of-sync due to the shared buffer from the compilation unit.
					newState = new Document(newState.get());
					List<HighlightedPositionCore> newPositions = semanticHighlightingService.calculateHighlightedPositions(unit, true);
					DocumentEvent event = new DocumentEvent(newState, startOffset, length, text);
					diffContexts.add(new HighlightedPositionDiffContext(oldState, event, oldPositions, newPositions));
				} else {
					IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
					edit.apply(document, TextEdit.NONE);
				}

			}
			triggerValidation(unit);
			updateSemanticHighlightings(params.getTextDocument(), diffContexts);
		} catch (JavaModelException | MalformedTreeException | BadLocationException | BadPositionCategoryException e) {
			JavaLanguageServerPlugin.logException("Error while handling document change. URI: " + uri, e);
		}
	}

	public void handleClosed(DidCloseTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		if (unit == null) {
			return;
		}
		try {
			synchronized (toReconcile) {
				toReconcile.remove(unit);
			}
			if (JDTUtils.isDefaultProject(unit) || !JDTUtils.isOnClassPath(unit) || unit.getResource().isDerived()) {
				new DiagnosticsHandler(connection, unit).clearDiagnostics();
			} else if (unit.hasUnsavedChanges()) {
				unit.discardWorkingCopy();
				unit.becomeWorkingCopy(new NullProgressMonitor());
				publishDiagnostics(unit, new NullProgressMonitor());
			}
			if (unit.equals(sharedASTProvider.getActiveJavaElement())) {
				sharedASTProvider.disposeAST();
			}
			unit.discardWorkingCopy();
			if (JDTUtils.isDefaultProject(unit)) {
				File f = new File(unit.getUnderlyingResource().getLocationURI());
				if (!f.exists()) {
					unit.delete(true, null);
				}
			}
			uninstallSemanticHighlightings(uri);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Error while handling document close. URI: " + uri, e);
		}
	}

	public void handleSaved(DidSaveTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		if (unit == null) {
			JavaLanguageServerPlugin.logError(uri + " does not resolve to a ICompilationUnit");
			return;
		}
		// see https://github.com/redhat-developer/vscode-java/issues/274
		unit = checkPackageDeclaration(uri, unit);
		if (unit.isWorkingCopy()) {
			try {
				projectsManager.fileChanged(uri, CHANGE_TYPE.CHANGED);
				unit.discardWorkingCopy();
				unit.becomeWorkingCopy(new NullProgressMonitor());
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Error while handling document save. URI: " + uri, e);
			}
		}
	}

	private ICompilationUnit checkPackageDeclaration(String uri, ICompilationUnit unit) {
		if (unit.getResource() != null && unit.getJavaProject() != null && unit.getJavaProject().getProject().getName().equals(ProjectsManager.DEFAULT_PROJECT_NAME)) {
			try {
				CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
				IProblem[] problems = astRoot.getProblems();
				for (IProblem problem : problems) {
					if (problem.getID() == IProblem.PackageIsNotExpectedPackage) {
						IResource file = unit.getResource();
						boolean toRemove = file.isLinked();
						if (toRemove) {
							IPath path = file.getParent().getProjectRelativePath();
							if (path.segmentCount() > 0 && JDTUtils.SRC.equals(path.segments()[0])) {
								String packageNameResource = path.removeFirstSegments(1).toString().replace(JDTUtils.PATH_SEPARATOR, JDTUtils.PERIOD);
								path = file.getLocation();
								if (path != null && path.segmentCount() > 0) {
									path = path.removeLastSegments(1);
									String pathStr = path.toString().replace(JDTUtils.PATH_SEPARATOR, JDTUtils.PERIOD);
									if (pathStr.endsWith(packageNameResource)) {
										toRemove = false;
									}
								}
							}
						}
						if (toRemove) {
							file.delete(true, new NullProgressMonitor());
							if (unit.equals(sharedASTProvider.getActiveJavaElement())) {
								sharedASTProvider.disposeAST();
							}
							unit.discardWorkingCopy();
							unit = JDTUtils.resolveCompilationUnit(uri);
							unit.becomeWorkingCopy(new NullProgressMonitor());
							triggerValidation(unit);
						}
						break;
					}
				}

			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		return unit;
	}

	protected void installSemanticHighlightings(ICompilationUnit unit) throws JavaModelException, BadPositionCategoryException {
		this.semanticHighlightingService.install(unit);
	}

	protected void uninstallSemanticHighlightings(String uri) {
		this.semanticHighlightingService.uninstall(uri);
	}

	protected void updateSemanticHighlightings(VersionedTextDocumentIdentifier textDocument, List<HighlightedPositionDiffContext> diffContexts) throws BadLocationException, BadPositionCategoryException, JavaModelException {
		this.semanticHighlightingService.update(textDocument, diffContexts);
	}

}
