package org.jboss.tools.vscode.java.handlers;

import java.util.HashMap;
import java.util.Map;

import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.managers.DocumentsManager;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class HoverHandler implements RequestHandler{
	
	private static final String REQ_HOVER = "textDocument/hover";
	private final DocumentsManager dm;
	public HoverHandler(DocumentsManager manager) {
		this.dm = manager;
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_HOVER.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		String uri = (String) request.getNamedParams().get("uri");
		
		int[] position = JsonRpcHelpers.readTextDocumentPosition(request);
		String hover = dm.computeHover(uri,position[0],position[1]);
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		Map<String,Object> result = new HashMap<String,Object>();
//		Map<String,Object> markedString = new HashMap<String,Object>();
//		markedString.put("language","html");
//		markedString.put("value",hover);
		result.put("contents",hover);
		response.setResult(result);
		return response;
	}

	@Override
	public void process(JSONRPC2Notification request) {
		// not needed
		
	}

}
