package org.jboss.tools.vscode.java.handlers;

import java.util.List;
import java.util.Map;

import org.jboss.tools.vscode.ipc.JsonRpcConnection;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.managers.DocumentsManager;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class DocumentLifeCycleHandler implements RequestHandler{
	
	private static final String REQ_OPENED = "textDocument/didOpen";
	private static final String REQ_CLOSED = "textDocument/didClose";
	private static final String REQ_CHANGED = "textDocument/didChange";
	
	
	private DocumentsManager dm;

	public DocumentLifeCycleHandler(DocumentsManager manager) {
		dm = manager;
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
		JsonRpcConnection.log("DocumentLifeCycleHandler.process");
		String uri = JsonRpcHelpers.readTextDocumentUri(request);
		if(REQ_OPENED.equals(request.getMethod())){
			dm.openDocument(uri);
		}
		if(REQ_CLOSED.equals(request.getMethod())){
			dm.closeDocument(uri);
		}
		if(REQ_CHANGED.equals(request.getMethod())){
			handleChanged(request);
		}
	}

	/**
	 * @param request
	 */
	private void handleChanged(JSONRPC2Notification request) {
		String uri = JsonRpcHelpers.readTextDocumentUri(request);
		List<Object> contentChanges = (List<Object>) request.getNamedParams().get("contentChanges");
		for (Object object : contentChanges) {
			Map<String, Object> change = (Map<String, Object>) object;
			String text = (String) change.get("text");
			Number length = (Number) change.get("rangeLength");
			Map<String, Object> range = (Map<String, Object>) change.get("range");
			Map<String, Object> start = (Map<String, Object>) range.get("start");
			Number line = (Number) start.get("line");
			Number charn = (Number) start.get("character"); 
			dm.updateDocument(uri,line.intValue(), charn.intValue(),length.intValue(),text);
		}
	}
	
}
