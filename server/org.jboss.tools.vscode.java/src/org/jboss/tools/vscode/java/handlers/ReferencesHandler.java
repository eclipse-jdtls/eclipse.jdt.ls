package org.jboss.tools.vscode.java.handlers;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;

public class ReferencesHandler extends AbstractRequestHandler implements RequestHandler<org.jboss.tools.langs.ReferenceParams, List<org.jboss.tools.langs.Location>>{

	public ReferencesHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_REFERENCES.getMethod().equals(request);
	}


	private IJavaElement findElementAtSelection(ICompilationUnit unit, int line, int column) throws JavaModelException {
		IJavaElement[] elements = unit.codeSelect(JsonRpcHelpers.toOffset(unit.getBuffer(), line, column), 0);

		if (elements == null || elements.length != 1)
			return null;
		return elements[0];

	}	

	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES);
	}

	@Override
	public List<org.jboss.tools.langs.Location> handle(org.jboss.tools.langs.ReferenceParams param) {
		SearchEngine engine = new SearchEngine();

		try {
			IJavaElement elementToSearch = findElementAtSelection(resolveCompilationUnit(param.getTextDocument().getUri()),
					param.getPosition().getLine().intValue(),
					param.getPosition().getCharacter().intValue());
			
			if(elementToSearch == null) 
				return Collections.emptyList();

			SearchPattern pattern = SearchPattern.createPattern(elementToSearch, IJavaSearchConstants.REFERENCES);
			List<org.jboss.tools.langs.Location> locations = new ArrayList<org.jboss.tools.langs.Location>();
			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					createSearchScope(), new SearchRequestor() {

						@Override
						public void acceptSearchMatch(SearchMatch match) throws CoreException {
							Object o = match.getElement();
							if (o instanceof IJavaElement) {
								IJavaElement element = (IJavaElement) o;
								ICompilationUnit compilationUnit = (ICompilationUnit) element
										.getAncestor(IJavaElement.COMPILATION_UNIT);
								org.jboss.tools.langs.Location location = null;
								if (compilationUnit != null) {
									location = toLocation(compilationUnit, match.getOffset(),
											match.getLength());
								}
								else{
									IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
									if (cf != null) {
										try {
											location = toLocation(cf, match.getOffset(), match.getLength());
										} catch (URISyntaxException e) {
										}
									}
								}
								if (location != null )
									locations.add(location);

							}

						}
					}, new NullProgressMonitor());

			return locations;
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}



