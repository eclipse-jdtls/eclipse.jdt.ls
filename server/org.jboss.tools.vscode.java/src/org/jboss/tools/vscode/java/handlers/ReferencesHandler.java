package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.managers.DocumentsManager;
import org.jboss.tools.vscode.java.model.SymbolInformation;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class ReferencesHandler implements RequestHandler {
	public static final String REQ_REFERENCES = "textDocument/references";
	private DocumentsManager dm;

	public ReferencesHandler(DocumentsManager manager) {
		this.dm = manager;
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_REFERENCES.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		ReferenceParams params = readParams(request);
		SearchEngine engine = new SearchEngine();

		IJavaElement elementToSearch = dm.findElementAtSelection(params.uri, params.position.line,
				params.position.character);

		SearchPattern pattern = SearchPattern.createPattern(elementToSearch, IJavaSearchConstants.REFERENCES);
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		try { 
			final List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					SearchEngine.createWorkspaceScope(), new SearchRequestor() {

						@Override
						public void acceptSearchMatch(SearchMatch match) throws CoreException {
							Object o = match.getElement();
							if (o instanceof IJavaElement) {
								IJavaElement element = (IJavaElement) o;
								Map<String, Object> outlineItem = new HashMap<String, Object>();
								outlineItem.put("name", element.getElementName());
								outlineItem.put("kind", SymbolInformation.mapKind(element));
								if (element.getParent() != null) {
									outlineItem.put("containerName", element.getParent().getElementName());
								}
								Map<String, Object> l = new HashMap<String, Object>();
								l.put("uri", match.getResource().getLocationURI().toString());
								l.put("range", JsonRpcHelpers.convertRange(0, 0, 0, 0));
								outlineItem.put("location", l);
								result.add(outlineItem);

							}

						}
					}, new NullProgressMonitor());

			response.setResult(result);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return response;
	}

	@SuppressWarnings("unchecked")
	private ReferenceParams readParams(JSONRPC2Request request) {
		ReferenceParams result = new ReferenceParams();
		Map<String, Object> params = request.getNamedParams();
		result.uri = (String) params.get("uri");
		result.languageId = (String) params.get("languageId");
		result.context = readContext((Map<String, Object>) params.get("context"));
		result.position = readPosition((Map<String, Object>) params.get("position"));
		return result;
	}

	private Position readPosition(Map<String, Object> context) {
		Position result = new Position();
		result.line = ((Number) context.get("line")).intValue();
		result.character = ((Number) context.get("character")).intValue();
		return result;
	}

	private ReferenceContext readContext(Map<String, Object> context) {
		ReferenceContext result = new ReferenceContext();
		result.includeDeclaration = (boolean) context.get("includeDeclaration");
		return result;

	}

	@Override
	public void process(JSONRPC2Notification request) {
		// not implemented
	}

}

class TextDocumentIdentifier {
	/**
	 * The text document's uri.
	 */
	String uri;
	/**
	 * The text document's language identifier
	 */
	String languageId;
}

class TextDocumentPosition extends TextDocumentIdentifier {
	/**
	 * The position inside the text document.
	 */
	Position position;
}

class ReferenceParams extends TextDocumentPosition {
	ReferenceContext context;
}

class ReferenceContext {
	/**
	 * Include the declaration of the current symbol.
	 */
	boolean includeDeclaration;
}

class Position {
	/**
	 * Line position in a document (zero-based).
	 */
	int line;
	/**
	 * Character offset on a line in a document (zero-based).
	 */
	int character;
}
