package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.jboss.tools.vscode.java.model.Location;
import org.jboss.tools.vscode.java.model.Position;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class ReferencesHandler extends AbstractRequestHandler {
	public static final String REQ_REFERENCES = "textDocument/references";

	public ReferencesHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_REFERENCES.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		ReferenceParams params = readParams(request);
		SearchEngine engine = new SearchEngine();

		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		try {
			IJavaElement elementToSearch = findElementAtSelection(resolveCompilationUnit(request), params.position.line,
					params.position.character);

			SearchPattern pattern = SearchPattern.createPattern(elementToSearch, IJavaSearchConstants.REFERENCES);
			final List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					createSearchScope(), new SearchRequestor() {

						@Override
						public void acceptSearchMatch(SearchMatch match) throws CoreException {
							Object o = match.getElement();
							if (o instanceof IJavaElement) {
								IJavaElement element = (IJavaElement) o;
								ICompilationUnit compilationUnit = (ICompilationUnit) element
										.getAncestor(IJavaElement.COMPILATION_UNIT);
								if (compilationUnit == null) {
									return;
								}
								Location location = getLocation(compilationUnit, match.getOffset(),
										match.getLength());
								Map<String, Object> l = new HashMap<String, Object>();
								l.put("uri", location.getUri());
								l.put("range", JsonRpcHelpers.convertRange(location.getLine(), location.getColumn(),
										location.getEndLine(), location.getEndColumn()));
								result.add(l);

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

	private IJavaElement findElementAtSelection(ICompilationUnit unit, int line, int column) throws JavaModelException {
		IJavaElement[] elements = unit.codeSelect(JsonRpcHelpers.toOffset(unit.getBuffer(), line, column), 0);

		if (elements == null || elements.length != 1)
			return null;
		return elements[0];

	}	
	
	@SuppressWarnings("unchecked")
	private ReferenceParams readParams(JSONRPC2Request request) {
		ReferenceParams result = new ReferenceParams();

		Map<String, Object> params = request.getNamedParams();
		Map<String, Object> textDocument = (Map<String, Object>) params.get("textDocument");
		result.uri = (String) textDocument.get("uri");
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

	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES);
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

