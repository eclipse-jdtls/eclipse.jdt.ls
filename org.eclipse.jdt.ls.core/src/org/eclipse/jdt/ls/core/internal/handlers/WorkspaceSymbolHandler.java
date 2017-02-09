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
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;

public class WorkspaceSymbolHandler{

	List<SymbolInformation> search(String query) {
		try {
			ArrayList<SymbolInformation> symbols = new ArrayList<>();

			new SearchEngine().searchAllTypeNames(null,SearchPattern.R_PATTERN_MATCH, query.toCharArray(), SearchPattern.R_PREFIX_MATCH,IJavaSearchConstants.TYPE, createSearchScope(),new TypeNameMatchRequestor() {

				@Override
				public void acceptTypeNameMatch(TypeNameMatch match) {
					SymbolInformation symbolInformation = new SymbolInformation();
					symbolInformation.setContainerName(match.getTypeContainerName());
					symbolInformation.setName(match.getSimpleTypeName());
					symbolInformation.setKind(DocumentSymbolHandler.mapKind(match.getType()));
					Location location = new Location();
					location.setUri(match.getType().getResource().getLocationURI().toString());
					location.setRange(new Range(new Position(0,0), new Position(0, 0)));
					symbolInformation.setLocation(location);
					symbols.add(symbolInformation);
				}
			}, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, new NullProgressMonitor());

			return symbols;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting search for" +  query, e);
		}
		return Collections.emptyList();
	}

	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES);
	}

}