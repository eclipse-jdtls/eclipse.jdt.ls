/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;

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
import org.jboss.tools.langs.Location;
import org.jboss.tools.langs.ReferenceParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

public class ReferencesHandler implements RequestHandler<ReferenceParams, List<Location>>{

	public ReferencesHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_REFERENCES.getMethod().equals(request);
	}

	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES);
	}

	@Override
	public List<org.jboss.tools.langs.Location> handle(org.jboss.tools.langs.ReferenceParams param, CancelMonitor cm) {
		SearchEngine engine = new SearchEngine();

		try {
			IJavaElement elementToSearch = JDTUtils.findElementAtSelection(JDTUtils.resolveTypeRoot(param.getTextDocument().getUri()),
					param.getPosition().getLine().intValue(),
					param.getPosition().getCharacter().intValue());

			if(elementToSearch == null)
				return Collections.emptyList();

			SearchPattern pattern = SearchPattern.createPattern(elementToSearch, IJavaSearchConstants.REFERENCES);
			List<org.jboss.tools.langs.Location> locations = new ArrayList<>();
			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					createSearchScope(), new SearchRequestor() {

				@Override
				public void acceptSearchMatch(SearchMatch match) throws CoreException {
					Object o = match.getElement();
					if (o instanceof IJavaElement) {
						IJavaElement element = (IJavaElement) o;
						ICompilationUnit compilationUnit = (ICompilationUnit) element
								.getAncestor(IJavaElement.COMPILATION_UNIT);
						Location location = null;
						if (compilationUnit != null) {
							location = JDTUtils.toLocation(compilationUnit, match.getOffset(),
									match.getLength());
						}
						else{
							IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
							if (cf != null && cf.getSourceRange() != null) {
								location = JDTUtils.toLocation(cf, match.getOffset(), match.getLength());
							}
						}
						if (location != null )
							locations.add(location);

					}

				}
			}, new NullProgressMonitor());

			return locations;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Find references failure ", e);
		}
		return null;
	}

}



