package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder;
import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder.OccurrenceLocation;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.managers.DocumentsManager;
import org.jboss.tools.vscode.java.model.DocumentHighlight;
import org.jboss.tools.vscode.java.model.Location;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class DocumentHighlightHandler implements RequestHandler {
	public static final String REQ_DOCUMENT_HIGHLIGHT= "textDocument/documentHighlight";
	private final DocumentsManager dm;
	
	public DocumentHighlightHandler(DocumentsManager manager) {
		this.dm = manager;
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_DOCUMENT_HIGHLIGHT.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		String uri = JsonRpcHelpers.readTextDocumentUri(request);
		int[] position = JsonRpcHelpers.readTextDocumentPosition(request);
		List<DocumentHighlight> computeOccurrences = dm.computeOccurrences(uri, position[0], position[1]);
		List<Map<String, Object>> result= new ArrayList<>();
		for (DocumentHighlight occurrence : computeOccurrences) {
			result.add(occurrence.convertForRPC());
		}
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		response.setResult(result);
		return response;
	}

	private Map<String, Object> convert(OccurrenceLocation occurrence) {
		Map<String, Object> result= new HashMap<>();
		if ((occurrence.getFlags() | IOccurrencesFinder.F_WRITE_OCCURRENCE) == IOccurrencesFinder.F_WRITE_OCCURRENCE) {
			result.put("kind",3);
		} else if ((occurrence.getFlags() | IOccurrencesFinder.F_READ_OCCURRENCE) == IOccurrencesFinder.F_READ_OCCURRENCE) {
			result.put("kind", 2);
		} 
		return result;
	}

	@Override
	public void process(JSONRPC2Notification request) {
		// not implemented
	}

}
