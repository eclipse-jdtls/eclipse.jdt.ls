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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;

public class WorkspaceSymbolHandler{

	public List<SymbolInformation> search(String query, IProgressMonitor monitor) {
		if (query == null || query.trim().isEmpty()) {
			return Collections.emptyList();
		}

		try {
			ArrayList<SymbolInformation> symbols = new ArrayList<>();
			new SearchEngine().searchAllTypeNames(null,SearchPattern.R_PATTERN_MATCH, query.toCharArray(), SearchPattern.R_CAMELCASE_MATCH, IJavaSearchConstants.TYPE, createSearchScope(),new TypeNameMatchRequestor() {

				@Override
				public void acceptTypeNameMatch(TypeNameMatch match) {
					SymbolInformation symbolInformation = new SymbolInformation();
					symbolInformation.setContainerName(match.getTypeContainerName());
					symbolInformation.setName(match.getSimpleTypeName());
					symbolInformation.setKind(mapKind(match));
					Location location;
					try {
						if (match.getType().isBinary()) {
							location = JDTUtils.toLocation(match.getType().getClassFile());
						}  else {
							location = JDTUtils.toLocation(match.getType());
						}
					} catch (Exception e) {
						JavaLanguageServerPlugin.logException("Unable to determine location for " +  match.getSimpleTypeName(), e);
						return;
					}
					symbolInformation.setLocation(location);
					symbols.add(symbolInformation);
				}

				private SymbolKind mapKind(TypeNameMatch match) {
					int flags= match.getModifiers();
					if (Flags.isInterface(flags)) {
						return SymbolKind.Interface;
					}
					if (Flags.isAnnotation(flags)) {
						return SymbolKind.Property;
					}
					if (Flags.isEnum(flags)) {
						return SymbolKind.Enum;
					}
					return SymbolKind.Class;
				}
			}, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, monitor);
			return symbols;
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Problem getting search for" +  query, e);
		}
		return Collections.emptyList();
	}

	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES);
	}

}