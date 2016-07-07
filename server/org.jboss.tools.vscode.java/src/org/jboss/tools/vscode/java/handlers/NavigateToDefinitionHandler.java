package org.jboss.tools.vscode.java.handlers;

import java.util.HashMap;
import java.util.Map;

import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.managers.DocumentsManager;
import org.jboss.tools.vscode.java.model.Location;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class NavigateToDefinitionHandler implements RequestHandler{

	private static final String REQ_DEFINITION = "textDocument/definition";
	private final DocumentsManager dm;
	
	public NavigateToDefinitionHandler(DocumentsManager manager) {
		this.dm = manager;
	}
	
	@Override
	public boolean canHandle(String request) {
		return REQ_DEFINITION.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		String uri = JsonRpcHelpers.readTextDocumentUri(request);
		int[] position = JsonRpcHelpers.readTextDocumentPosition(request);
		Location l = dm.computeDefinitonNavigation(uri,position[0],position[1] );
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		Map<String,Object> result = new HashMap<String,Object>();
		if(l != null){
			result.put("uri",l.getUri());
			result.put("range", convertRange(l.getLine(),l.getColumn(),l.getEndLine(),l.getEndColumn()));
		}
		response.setResult(result);
		return response;
	}

	@Override
	public void process(JSONRPC2Notification request) {
		// not implemented
		
	}
	
	private Map<String, Object> convertRange(int startLine, int startCol, int endLine, int endCol) {
		Map<String, Object> range = new HashMap<String, Object>();
		Map<String, Object> start = new HashMap<String, Object>();
		Map<String, Object> end = new HashMap<String, Object>();
		if(startLine >-1)
			start.put("line",startLine);
		if(startCol > -1)
			start.put("character", startCol);
		if(endCol >-1)
			end.put("character",endCol);
		if(endLine > -1)
			end.put("line",endLine);
		range.put("start",start);
		range.put("end",end);
		return range;
	}

}
