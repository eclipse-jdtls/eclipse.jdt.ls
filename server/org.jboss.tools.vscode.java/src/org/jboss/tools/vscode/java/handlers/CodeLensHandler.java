package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.model.Location;
import org.jboss.tools.vscode.java.model.Range;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class CodeLensHandler extends AbstractRequestHandler {
	public static final String REQ_CODE_LENSE = "textDocument/codeLens";
	public static final String REQ_CODE_LENSE_RESOLVE = "codeLens/resolve";

	public CodeLensHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_CODE_LENSE.equals(request) || REQ_CODE_LENSE_RESOLVE.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		if (REQ_CODE_LENSE.equals(request.getMethod())) {
			ICompilationUnit unit = this.resolveCompilationUnit(request);
			List<Map<String, Object>> result = getCodeLensSymbols(unit);
			response.setResult(result);
		} else {
			Map<String, Object> result = resolve(request.getNamedParams());
			response.setResult(result);
		}
		return response;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> resolve(Map<String, Object> namedParams) {
		try {
			HashMap<String, Object> lens = new HashMap<String, Object>();
			lens.putAll(namedParams);
			List<Object> data = (List<Object>) lens.get("data");
			String uri = (String) data.get(0);
			Map<String, Object> position = (Map<String, Object>) data.get(1);

			ICompilationUnit unit = resolveCompilationUnit(uri);
			IJavaElement element = findElementAtSelection(unit, ((Long) position.get("line")).intValue(), ((Long) position.get("character")).intValue());
			List<Map<String, Object>> locations = findReferences(element);
			int nReferences = locations.size();
			Map<String, Object> command = new HashMap<String, Object>();
			command.put("title", nReferences == 1 ? "1 reference" : nReferences + " references");
			command.put("command", "java.show.references");
			command.put("arguments", Arrays.asList(uri, position, locations));
			lens.put("command", command);
			return lens;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem resolving code lens", e);
		}
		return namedParams;
	}

	private IJavaElement findElementAtSelection(ICompilationUnit unit, int line, int column) throws JavaModelException {
		IJavaElement[] elements = unit.codeSelect(JsonRpcHelpers.toOffset(unit.getBuffer(), line, column), 0);

		if (elements == null || elements.length != 1)
			return null;
		return elements[0];

	}

	private List<Map<String, Object>> findReferences(IJavaElement element) throws JavaModelException, CoreException {
		SearchPattern pattern = SearchPattern.createPattern(element, IJavaSearchConstants.REFERENCES);
		final List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		SearchEngine engine = new SearchEngine();
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
							Location location = getLocation(compilationUnit, match.getOffset(), match.getLength());
							Map<String, Object> l = new HashMap<String, Object>();
							l.put("uri", location.getUri());
							l.put("range", JsonRpcHelpers.convertRange(location.getLine(), location.getColumn(),
									location.getEndLine(), location.getEndColumn()));
							result.add(l);

						}

					}
				}, new NullProgressMonitor());

		return result;
	}

	private List<Map<String, Object>> getCodeLensSymbols(ICompilationUnit unit) {
		try {
			IJavaElement[] elements = unit.getChildren();
			ArrayList<Map<String, Object>> lenses = new ArrayList<Map<String, Object>>(elements.length);
			collectChildren(unit, elements, lenses);
			return lenses;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting code lenses for" + unit.getElementName(), e);
		}
		return Collections.emptyList();
	}

	private void collectChildren(ICompilationUnit unit, IJavaElement[] elements, ArrayList<Map<String, Object>> lenses)
			throws JavaModelException {
		for (IJavaElement element : elements) {
			if (element.getElementType() == IJavaElement.TYPE) {
				collectChildren(unit, ((IType) element).getChildren(), lenses);
			} else if (element.getElementType() != IJavaElement.METHOD) {
				continue;
			}
			ISourceRange r = ((ISourceReference) element).getNameRange();
			Range range = getRange(unit, r.getOffset(), r.getLength());

			HashMap<String, Object> lens = new HashMap<String, Object>();
			lens.put("range", range.convertForRPC());
			lens.put("data", Arrays.asList(getFileURI(unit), range.start.convertForRPC()));

			lenses.add(lens);
		}
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

class CodeLensParams {
	/**
	 * The text document's uri.
	 */
	String uri;
}
