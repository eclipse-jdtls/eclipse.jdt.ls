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
import org.jboss.tools.langs.Location;
import org.jboss.tools.langs.Position;
import org.jboss.tools.langs.Range;
import org.jboss.tools.langs.SymbolInformation;
import org.jboss.tools.langs.WorkspaceSymbolParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

public class WorkspaceSymbolHandler implements RequestHandler<WorkspaceSymbolParams, List<SymbolInformation>> {

	public WorkspaceSymbolHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.WORKSPACE_SYMBOL.getMethod().equals(request);
	}

	private List<SymbolInformation> search(String query) {
		try {
			ArrayList<SymbolInformation> symbols = new ArrayList<SymbolInformation>();
			
			new SearchEngine().searchAllTypeNames(null,SearchPattern.R_PATTERN_MATCH, query.toCharArray(), SearchPattern.R_PREFIX_MATCH,IJavaSearchConstants.TYPE, createSearchScope(),new TypeNameMatchRequestor() {
				
				@Override
				public void acceptTypeNameMatch(TypeNameMatch match) {
					SymbolInformation symbolInformation = new SymbolInformation();
					symbolInformation.setContainerName(match.getTypeContainerName());
					symbolInformation.setName(match.getSimpleTypeName());
					symbolInformation.setKind(new Double(DocumentSymbolHandler.mapKind(match.getType())));
					Location location = new Location();
					location.setUri(match.getType().getResource().getLocationURI().toString());
					location.setRange(new Range().withEnd(new Position().withLine(Double.valueOf(0)).withCharacter(Double.valueOf(0)))
							.withStart(new Position().withLine(Double.valueOf(0)).withCharacter(Double.valueOf(0))));
					symbols.add(symbolInformation.withLocation(location));
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
	

	@Override
	public List<org.jboss.tools.langs.SymbolInformation> handle(WorkspaceSymbolParams param) {
		return this.search(param.getQuery());
	}
}