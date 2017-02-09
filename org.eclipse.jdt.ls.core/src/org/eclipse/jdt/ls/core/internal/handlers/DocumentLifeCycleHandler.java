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

import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
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


	void didClose(DidCloseTextDocumentParams params){
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

	void didOpen(DidOpenTextDocumentParams params){
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

	void didChange(DidChangeTextDocumentParams params){
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

	void didSave(DidSaveTextDocumentParams params){
	}

	public DocumentLifeCycleHandler(JavaClientConnection connection, PreferenceManager preferenceManager) {
		this.connection = connection;
		this.preferenceManager = preferenceManager;
	}

	private void handleOpen(DidOpenTextDocumentParams params) {
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
			boolean reportOnlySyntaxErrors = unit.getResource().getProject().equals(JavaLanguageServerPlugin.getProjectsManager().getDefaultProject());
			if (reportOnlySyntaxErrors) {
				Severity severity = preferenceManager.getPreferences().getIncompleteClasspathSeverity();
				String msg = "Classpath is incomplete. Only syntax errors will be reported";
				JavaLanguageServerPlugin.logInfo(msg +" for "+uri);
				if (severity.compareTo(Preferences.Severity.ignore) > 0){
					ActionableNotification ignoreIncompleteClasspath = new ActionableNotification()
							.withSeverity(severity.toMessageType())
							.withMessage(msg)
							.withCommands(Collections.singletonList(
									new Command("Don't show again", "java.ignoreIncompleteClasspath", null)
									));
					connection.sendActionableNotification(ignoreIncompleteClasspath);
				}
			}

			//			DiagnosticsHandler problemRequestor = new DiagnosticsHandler(connection, unit.getResource(), reportOnlySyntaxErrors);
			unit.becomeWorkingCopy(new NullProgressMonitor());
			IBuffer buffer = unit.getBuffer();
			if(buffer != null) {
				buffer.setContents(params.getTextDocument().getText());
			}

			// TODO: wire up cancellation.
			unit.reconcile(ICompilationUnit.NO_AST, true/*don't force problem detection*/, false, JavaLanguageServerPlugin.getInstance().getWorkingCopyOwner(), null/*no progress monitor*/);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Creating working copy ",e);
		}
	}

	private void handleChanged(DidChangeTextDocumentParams params) {
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());

		if (unit == null || !unit.isWorkingCopy()) {
			return;
		}

		try {
			List<TextDocumentContentChangeEvent> contentChanges = params.getContentChanges();
			for (TextDocumentContentChangeEvent changeEvent : contentChanges) {

				Range range = changeEvent.getRange();
				int startOffset = JsonRpcHelpers.toOffset(unit.getBuffer(), range.getStart().getLine(), range.getStart().getCharacter());
				int length = changeEvent.getRangeLength().intValue();

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
			unit.reconcile(ICompilationUnit.NO_AST, true, false, JavaLanguageServerPlugin.getInstance().getWorkingCopyOwner(), null);
		} catch (JavaModelException | MalformedTreeException | BadLocationException e) {
			JavaLanguageServerPlugin.logException("Failed to apply changes",e);
		}
	}

	private void handleClosed(DidCloseTextDocumentParams params) {
		JavaLanguageServerPlugin.logInfo("DocumentLifeCycleHandler.handleClosed");
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		if (unit == null) {
			return;
		}
		try {
			unit.discardWorkingCopy();
		} catch (CoreException e) {
		}
	}
}
