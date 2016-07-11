package org.jboss.tools.vscode.java.handlers;

import java.util.List;
import java.util.Map;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.jboss.tools.vscode.ipc.JsonRpcConnection;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class DocumentLifeCycleHandler extends AbstractRequestHandler {
	
	private static final String REQ_OPENED = "textDocument/didOpen";
	private static final String REQ_CLOSED = "textDocument/didClose";
	private static final String REQ_CHANGED = "textDocument/didChange";
	
	
	private JsonRpcConnection connection;
	
	public DocumentLifeCycleHandler(JsonRpcConnection connection) {
		this.connection = connection;
	}
	
	@Override
	public boolean canHandle(String request) {
		return REQ_OPENED.equals(request) 
				|| REQ_CLOSED.equals(request)
				|| REQ_CHANGED.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		return JsonRpcHelpers.methodNotFound(request);
	}

	@Override
	public void process(JSONRPC2Notification request) {
		JavaLanguageServerPlugin.logInfo("DocumentLifeCycleHandler.process");
		if(REQ_OPENED.equals(request.getMethod())) {
			handleOpen(request);
		}
		if(REQ_CLOSED.equals(request.getMethod())) {
			handleClosed(request);
		}
		if(REQ_CHANGED.equals(request.getMethod())) {
			handleChanged(request);
		}
	}
	
	private void handleOpen(JSONRPC2Notification request) {
		ICompilationUnit unit = resolveCompilationUnit(request);
		if (unit == null) {
			return;
		}
		try {
			// ToDo wire up cancelation.
			unit.becomeWorkingCopy(new DiagnosticsHandler(connection, unit.getUnderlyingResource()), null);
			unit.reconcile();
		} catch (JavaModelException e) {
		}
	}

	private void handleChanged(JSONRPC2Notification notification) {
		ICompilationUnit unit = resolveCompilationUnit(notification);
		
		if (!unit.isWorkingCopy()) {
			return;
		}
		
		ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
		ITextFileBuffer buffer = manager.getTextFileBuffer(unit.getResource().getFullPath(), LocationKind.IFILE);
		IDocument document = buffer.getDocument();
		
		try {			
			MultiTextEdit root = new MultiTextEdit();
			
			List<Object> contentChanges = (List<Object>) notification.getNamedParams().get("contentChanges");
			for (Object object : contentChanges) {
				Map<String, Object> change = (Map<String, Object>) object;
				String text = (String) change.get("text");
				Map<String, Object> range = (Map<String, Object>) change.get("range");
				Map<String, Object> start = (Map<String, Object>) range.get("start");
				Map<String, Object> end = (Map<String, Object>) range.get("end");
				
				Number startLine = (Number) start.get("line");
				Number startChar = (Number) start.get("character");
				Number endLine = (Number) end.get("line");
				Number endChar = (Number) end.get("character");
				
				int startOffset = document.getLineOffset(startLine.intValue()) + startChar.intValue(); 
				int endOffset = document.getLineOffset(endLine.intValue()) + endChar.intValue(); 
				int length = endOffset - startOffset;
				
				TextEdit edit = null;
				if (length == 0) {
					edit = new InsertEdit(startOffset, text);
				} else if (text.length() == 0) {
					edit = new DeleteEdit(startOffset, length);
				} else {
					edit = new ReplaceEdit(startOffset, length, text);
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
		
	private void handleClosed(JSONRPC2Notification notification) {
		ICompilationUnit unit = resolveCompilationUnit(notification);
		if (unit == null) {
			return;
		}
		try {
			unit.discardWorkingCopy();			
		} catch (JavaModelException e) {
		}
	}
}
