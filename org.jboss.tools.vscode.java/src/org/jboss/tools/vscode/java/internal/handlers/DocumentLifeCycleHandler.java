/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;

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
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.jboss.tools.langs.DidChangeTextDocumentParams;
import org.jboss.tools.langs.DidCloseTextDocumentParams;
import org.jboss.tools.langs.DidOpenTextDocumentParams;
import org.jboss.tools.langs.DidSaveTextDocumentParams;
import org.jboss.tools.langs.Range;
import org.jboss.tools.langs.TextDocumentContentChangeEvent;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.MessageType;
import org.jboss.tools.vscode.internal.ipc.NotificationHandler;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaClientConnection;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

public class DocumentLifeCycleHandler {

	private JavaClientConnection connection;

	public class ClosedHandler implements NotificationHandler<DidCloseTextDocumentParams, Object>{
		@Override
		public boolean canHandle(String request) {
			return LSPMethods.DOCUMENT_CLOSED.getMethod().equals(request);
		}

		@Override
		public Object handle(DidCloseTextDocumentParams param) {
			try {
				ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
					@Override
					public void run(IProgressMonitor monitor) throws CoreException {
						handleClosed(param);
					}
				}, new NullProgressMonitor());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Handle document close ", e);
			}
			return null;
		}

	}

	public class OpenHandler implements NotificationHandler<DidOpenTextDocumentParams, Object>{

		@Override
		public boolean canHandle(String request) {
			return LSPMethods.DOCUMENT_OPENED.getMethod().equals(request);
		}

		@Override
		public Object handle(DidOpenTextDocumentParams param) {
			try {
				ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
					@Override
					public void run(IProgressMonitor monitor) throws CoreException {
						handleOpen(param);
					}
				}, new NullProgressMonitor());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Handle document open ", e);
			}
			return null;
		}
	}

	public class ChangeHandler implements NotificationHandler<DidChangeTextDocumentParams, Object>{

		@Override
		public boolean canHandle(String request) {
			return LSPMethods.DOCUMENT_CHANGED.getMethod().equals(request);
		}

		@Override
		public Object handle(DidChangeTextDocumentParams param) {
			try {
				ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
					@Override
					public void run(IProgressMonitor monitor) throws CoreException {
						handleChanged(param);
					}
				}, new NullProgressMonitor());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Handle document open ", e);
			}
			return null;
		}
	}

	public class SaveHandler implements NotificationHandler<DidSaveTextDocumentParams, Object>{

		@Override
		public boolean canHandle(String request) {
			return LSPMethods.DOCUMENT_SAVED.getMethod().equals(request);
		}

		@Override
		public Object handle(DidSaveTextDocumentParams param) {
			// Nothing to do just keeping the clients happy with a response
			return null;
		}
	}

	public DocumentLifeCycleHandler(JavaClientConnection connection) {
		this.connection = connection;
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
				connection.showNotificationMessage(MessageType.Warning, "Classpath is incomplete. Only syntax errors will be reported.");
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
			MultiTextEdit root = new MultiTextEdit();
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
				root.addChild(edit);
			}

			if (root.hasChildren()) {
				unit.applyTextEdit(root, new NullProgressMonitor());
				unit.reconcile(ICompilationUnit.NO_AST, true, false, JavaLanguageServerPlugin.getInstance().getWorkingCopyOwner(), null);
			}
		} catch (JavaModelException e) {
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
