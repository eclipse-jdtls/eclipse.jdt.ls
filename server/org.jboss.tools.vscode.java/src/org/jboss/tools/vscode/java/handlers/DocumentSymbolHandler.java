package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.managers.DocumentsManager;
import org.jboss.tools.vscode.java.model.SymbolInformation;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class DocumentSymbolHandler implements RequestHandler {

	private static final String  REQ_DOC_SYMBOL = "textDocument/documentSymbol";
	private final DocumentsManager dm;
	
	public DocumentSymbolHandler(DocumentsManager manager) {
		this.dm = manager;
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_DOC_SYMBOL.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		String uri = JsonRpcHelpers.readTextDocumentUri(request);
		SymbolInformation[] elements  = dm.getOutline(uri);
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
		for ( SymbolInformation element : elements ) {
			Map<String,Object> outlineItem = new HashMap<String,Object>();
			outlineItem.put("name", element.getName());
			outlineItem.put("kind", element.getKind());
			if(element.getContainerName() != null ){
				outlineItem.put("containerName", element.getContainerName());
			}
			Map<String,Object> l = new HashMap<String,Object>();
			l.put("uri",uri);
			l.put("range",JsonRpcHelpers.convertRange(element.getLocation().getLine(),
					element.getLocation().getColumn(),
					element.getLocation().getEndLine(),
					element.getLocation().getEndColumn()));
			outlineItem.put("location",l);
			result.add(outlineItem);
			
		}
		response.setResult(result);
		return response;
	}



	@Override
	public void process(JSONRPC2Notification request) {

	}

}
