/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
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
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;

public final class ReferencesHandler {

	private final PreferenceManager preferenceManager;

	public ReferencesHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		int scope = IJavaSearchScope.SOURCES;
		if (preferenceManager.isClientSupportsClassFileContent()) {
			scope |= IJavaSearchScope.APPLICATION_LIBRARIES;
		}
		return SearchEngine.createJavaSearchScope(projects, scope);
	}

	public List<Location> findReferences(ReferenceParams param, IProgressMonitor monitor) {

		final List<Location> locations = new ArrayList<>();
		try {
			IJavaElement elementToSearch = JDTUtils.findElementAtSelection(JDTUtils.resolveTypeRoot(param.getTextDocument().getUri()), param.getPosition().getLine(), param.getPosition().getCharacter(), this.preferenceManager, monitor);

			if (elementToSearch == null) {
				return locations;
			}

			boolean includeClassFiles = preferenceManager.isClientSupportsClassFileContent();
			SearchEngine engine = new SearchEngine();
			SearchPattern pattern = SearchPattern.createPattern(elementToSearch, IJavaSearchConstants.REFERENCES);

			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, createSearchScope(), new SearchRequestor() {

				@Override
				public void acceptSearchMatch(SearchMatch match) throws CoreException {
					Object o = match.getElement();
					if (o instanceof IJavaElement) {
						IJavaElement element = (IJavaElement) o;
						ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
						Location location = null;
						if (compilationUnit != null) {
							location = JDTUtils.toLocation(compilationUnit, match.getOffset(), match.getLength());
						} else if (includeClassFiles) {
							IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
							if (cf != null && cf.getSourceRange() != null) {
								location = JDTUtils.toLocation(cf, match.getOffset(), match.getLength());
							}
						}
						if (location != null) {
							locations.add(location);
						}
					}
				}
			}, monitor);

		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Find references failure ", e);
		}
		return locations;
	}

}