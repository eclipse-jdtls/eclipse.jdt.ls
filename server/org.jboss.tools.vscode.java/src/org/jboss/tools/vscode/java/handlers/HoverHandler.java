package org.jboss.tools.vscode.java.handlers;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.jboss.tools.vscode.java.HoverInfoProvider;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class HoverHandler extends AbstractRequestHandler {
	
	private static final String REQ_HOVER = "textDocument/hover";

	public HoverHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_HOVER.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		ICompilationUnit unit = resolveCompilationUnit(request);
		int[] position = JsonRpcHelpers.readTextDocumentPosition(request);
	
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		Map<String,Object> result = new HashMap<String,Object>();		
		
		String hover = computeHover(unit ,position[0],position[1]);
		if (hover != null && hover.length() > 0) {
//			Map<String,Object> markedString = new HashMap<String,Object>();
//			markedString.put("language","html");
//			markedString.put("value",hover);
			result.put("contents",hover);
			response.setResult(result);
		}
		return response;
	}

	@Override
	public void process(JSONRPC2Notification request) {
		// not needed
	}
	 
	public String computeHover(ICompilationUnit unit, int line, int column) {
		HoverInfoProvider provider = new HoverInfoProvider(unit);
		return provider.computeHover(line,column);
		
	}	
}
