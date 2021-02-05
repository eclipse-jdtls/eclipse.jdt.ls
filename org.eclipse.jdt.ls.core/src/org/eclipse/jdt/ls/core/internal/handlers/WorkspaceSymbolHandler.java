/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbolParams;

public class WorkspaceSymbolHandler{

	public static List<SymbolInformation> search(String query, IProgressMonitor monitor) {
		return search(query, 0, null, false, monitor);
	}

	public static List<SymbolInformation> search(String query, String projectName, boolean sourceOnly, IProgressMonitor monitor) {
		return search(query, 0, projectName, sourceOnly, monitor);
	}

	public static List<SymbolInformation> search(String query, int maxResults, String projectName, boolean sourceOnly, IProgressMonitor monitor) {
		ArrayList<SymbolInformation> symbols = new ArrayList<>();
		if (StringUtils.isBlank(query)) {
			return symbols;
		}

		try {
			monitor.beginTask("Searching the types...", 100);
			IJavaSearchScope searchScope = createSearchScope(projectName, sourceOnly);
			int typeMatchRule = SearchPattern.R_CAMELCASE_MATCH;
			if (query.contains("*") || query.contains("?")) {
				typeMatchRule |= SearchPattern.R_PATTERN_MATCH;
			}
			new SearchEngine().searchAllTypeNames(null, SearchPattern.R_PATTERN_MATCH, query.trim().toCharArray(), typeMatchRule, IJavaSearchConstants.TYPE, searchScope, new TypeNameMatchRequestor() {

				@Override
				public void acceptTypeNameMatch(TypeNameMatch match) {
					try {
						if (maxResults > 0 && symbols.size() >= maxResults) {
							return;
						}
						Location location = null;
						try {
							if (!sourceOnly && match.getType().isBinary()) {
								location = JDTUtils.toLocation(match.getType().getClassFile());
							} else if (!match.getType().isBinary()) {
								location = JDTUtils.toLocation(match.getType());
							}
						} catch (Exception e) {
							JavaLanguageServerPlugin.logException("Unable to determine location for " + match.getSimpleTypeName(), e);
							return;
						}

						if (location != null && match.getSimpleTypeName() != null && !match.getSimpleTypeName().isEmpty()) {
							SymbolInformation symbolInformation = new SymbolInformation();
							symbolInformation.setContainerName(match.getTypeContainerName());
							symbolInformation.setName(match.getSimpleTypeName());
							symbolInformation.setKind(mapKind(match));
							symbolInformation.setLocation(location);
							symbols.add(symbolInformation);
							if (maxResults > 0 && symbols.size() >= maxResults) {
								monitor.setCanceled(true);
							}
						}
					} catch (Exception e) {
						JavaLanguageServerPlugin.logException("Unable to determine location for " + match.getSimpleTypeName(), e);
						return;
					}
				}

				private SymbolKind mapKind(TypeNameMatch match) {
					int flags = match.getModifiers();
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
		} catch (Exception e) {
			if (e instanceof OperationCanceledException) {
				// ignore.
			} else {
				JavaLanguageServerPlugin.logException("Problem getting search for" + query, e);
			}
		} finally {
			monitor.done();
		}

		return symbols;
	}

	private static IJavaSearchScope createSearchScope(String projectName, boolean sourceOnly) throws JavaModelException {
		IJavaProject[] targetProjects;
		IJavaProject project = ProjectUtils.getJavaProject(projectName);
		if (project != null) {
			targetProjects = new IJavaProject[] { project };
		} else {
			targetProjects = ProjectUtils.getJavaProjects();
		}

		int scope = IJavaSearchScope.REFERENCED_PROJECTS | IJavaSearchScope.SOURCES;
		PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (!sourceOnly && preferenceManager != null && preferenceManager.isClientSupportsClassFileContent()) {
			scope |= IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES;
		}

		return SearchEngine.createJavaSearchScope(targetProjects, scope);
	}

	public static class SearchSymbolParams extends WorkspaceSymbolParams {
		public String projectName;
		public boolean sourceOnly;
		public int maxResults;

		public SearchSymbolParams(String query, String projectName) {
			super(query);
			this.projectName = projectName;
		}
	}
}