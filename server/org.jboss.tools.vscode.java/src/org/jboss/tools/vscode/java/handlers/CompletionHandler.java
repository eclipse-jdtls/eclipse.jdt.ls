package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.tools.vscode.ipc.MessageType;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.managers.DocumentsManager;
import org.jboss.tools.vscode.java.model.CodeCompletionItem;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class CompletionHandler implements RequestHandler {
	private static final String REQ_COMPLETION = "textDocument/completion";
	private final DocumentsManager dm;
	
	public CompletionHandler(DocumentsManager manager) {
		this.dm = manager;
	}
	
	@Override
	public boolean canHandle(String request) {
		return REQ_COMPLETION.equals(request); 
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		String uri = JsonRpcHelpers.readTextDocumentUri(request);
		int[] position = JsonRpcHelpers.readTextDocumentPosition(request);
		List<CodeCompletionItem> proposals = this.dm.computeContentAssist(uri,position[0],position[1]);
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
		for (CodeCompletionItem p : proposals) {
			Map<String,Object> completionItem = new HashMap<String,Object>();
			completionItem.put("label",p.getLabel());
			completionItem.put("kind", p.getKind());
			completionItem.put("insertText",p.getInsertText());
			result.add(completionItem);
		}
		response.setResult(result);
		JavaLanguageServerPlugin.log(MessageType.Info, "Completion request completed");
		return response;
	}
	
	@Override
	public void process(JSONRPC2Notification request) {
		//not implemented
	}
}
