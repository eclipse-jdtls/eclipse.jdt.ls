package org.jboss.tools.vscode.java.handlers;

import java.util.List;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IDocument;
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
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.JDTUtils;
import org.jboss.tools.vscode.java.JavaClientConnection;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;

public class DocumentLifeCycleHandler {
	
	private JavaClientConnection connection;
	
	public class ClosedHandler implements RequestHandler<DidCloseTextDocumentParams, Object>{
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
	
	public class OpenHandler implements RequestHandler<DidOpenTextDocumentParams, Object>{

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
	
	public class ChangeHandler implements RequestHandler<DidChangeTextDocumentParams, Object>{

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
	
	public class SaveHandler implements RequestHandler<DidSaveTextDocumentParams, Object>{

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
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return;
		}
		try {
			// The open event can happen before the workspace element added event when a new file is added. 
			// checks if the underlying resource exists and refreshes to sync the newly created file.
			if(unit.getResource() != null && !unit.getResource().isAccessible()){
				try {
					unit.getResource().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
				} catch (CoreException e) {
					// ignored
				}
			}
			
			IBuffer buffer = unit.getBuffer();
			if(buffer != null)
				buffer.setContents(params.getTextDocument().getText());

			// TODO: wire up cancellation.
			unit.becomeWorkingCopy(new DiagnosticsHandler(connection, unit.getUnderlyingResource()), null);
			unit.reconcile();
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Creating working copy ",e);
		}
	}

	private void handleChanged(DidChangeTextDocumentParams params) {
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		
		if (!unit.isWorkingCopy()) {
			return;
		}
		
		ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
		ITextFileBuffer buffer = manager.getTextFileBuffer(unit.getResource().getFullPath(), LocationKind.IFILE);
		IDocument document = buffer.getDocument();
		try {			
			MultiTextEdit root = new MultiTextEdit();
			List<TextDocumentContentChangeEvent> contentChanges = params.getContentChanges();
			for (TextDocumentContentChangeEvent changeEvent : contentChanges) {
				
				Range range = changeEvent.getRange();
				
				int startOffset = document.getLineOffset(range.getStart().getLine().intValue()) + range.getStart().getCharacter().intValue(); 
				int endOffset = document.getLineOffset(range.getEnd().getLine().intValue()) + range.getEnd().getCharacter().intValue(); 
				int length = endOffset - startOffset;
				
				TextEdit edit = null;
				if (length == 0) {
					edit = new InsertEdit(startOffset, changeEvent.getText());
				} else if (changeEvent.getText().length() == 0) {
					edit = new DeleteEdit(startOffset, length);
				} else {
					edit = new ReplaceEdit(startOffset, length, changeEvent.getText());
				}
				root.addChild(edit);
			}
		
			if (root.hasChildren()) {
				root.apply(document);
				unit.reconcile();					
			}
		} catch (JavaModelException e) {			
		} catch (org.eclipse.jface.text.BadLocationException e) {
		}
	}

	private void handleClosed(DidCloseTextDocumentParams params) {
		JavaLanguageServerPlugin.logInfo("DocumentLifeCycleHandler.handleClosed");
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return;
		}
		try {
			unit.discardWorkingCopy();
		} catch (JavaModelException e) {
		}
	}
}
