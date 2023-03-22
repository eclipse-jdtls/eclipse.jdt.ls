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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.MethodNameMatch;
import org.eclipse.jdt.core.search.MethodNameMatchRequestor;
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
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.WorkspaceSymbolParams;

public class WorkspaceSymbolHandler {

	public static List<SymbolInformation> search(String query, IProgressMonitor monitor) {
		return search(query, 0, null, false, monitor);
	}

	public static List<SymbolInformation> search(String query, String projectName, boolean sourceOnly, IProgressMonitor monitor) {
		return search(query, 0, projectName, sourceOnly, monitor);
	}

	public static List<SymbolInformation> search(String query, int maxResults, String projectName, boolean sourceOnly, IProgressMonitor monitor) {
		Set<SymbolInformation> symbols = new HashSet<>();
		if (StringUtils.isBlank(query)) {
			return new ArrayList<>(symbols);
		}

		try {
			monitor.beginTask("Searching the types...", 100);
			IJavaSearchScope searchScope = createSearchScope(projectName, sourceOnly);

			String tQuery = query.trim();
			String qualifierName = null;
			String typeName = tQuery;
			int qualifierMatchRule = SearchPattern.R_PATTERN_MATCH;

			int qualIndex = tQuery.lastIndexOf('.');
			if (qualIndex != -1) {
				qualifierName = tQuery.substring(0, qualIndex);
				typeName = tQuery.substring(qualIndex + 1);
				qualifierMatchRule = SearchPattern.R_CAMELCASE_MATCH;
				if (qualifierName.contains("*") || qualifierName.contains("?")) {
					qualifierMatchRule = SearchPattern.R_PATTERN_MATCH;
				}
			}

			int typeMatchRule = SearchPattern.R_CAMELCASE_MATCH;
			if (typeName.contains("*") || typeName.contains("?")) {
				typeMatchRule = SearchPattern.R_PATTERN_MATCH;
			}


			PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();

			SearchEngine engine = new SearchEngine();
			boolean isSymbolTagSupported = preferenceManager != null && preferenceManager.getClientPreferences().isSymbolTagSupported();
			WorkspaceSymbolTypeRequestor typeRequestor = new WorkspaceSymbolTypeRequestor(symbols, maxResults, sourceOnly, isSymbolTagSupported, monitor);
			if (!typeName.isEmpty()) {
				// search for qualifier = qualifierName, type = typeName
				engine.searchAllTypeNames(qualifierName == null ? null : qualifierName.toCharArray(), qualifierMatchRule, typeName.toCharArray(), typeMatchRule, IJavaSearchConstants.TYPE, searchScope,typeRequestor , IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, monitor);
			}
			// search for qualifier = qualiferName.typeName, type = null
			engine.searchAllTypeNames(tQuery.toCharArray(), qualifierMatchRule, null, typeMatchRule, IJavaSearchConstants.TYPE, searchScope, typeRequestor, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, monitor);

			if (preferenceManager != null && preferenceManager.getPreferences().isIncludeSourceMethodDeclarations()) {
				monitor.beginTask("Searching methods...", 100);
				IJavaSearchScope nonSourceSearchScope = createSearchScope(projectName, true);
				WorkspaceSymbolMethodRequestor methodRequestor = new WorkspaceSymbolMethodRequestor(symbols, maxResults, isSymbolTagSupported, monitor);
				engine.searchAllMethodNames(null, SearchPattern.R_PATTERN_MATCH, query.trim().toCharArray(), typeMatchRule, nonSourceSearchScope, methodRequestor, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, monitor);
			}
		} catch (Exception e) {
			if (e instanceof OperationCanceledException) {
				// ignore.
			} else {
				JavaLanguageServerPlugin.logException("Problem getting search for" + query, e);
			}
		} finally {
			monitor.done();
		}

		return new ArrayList<>(symbols);
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

	private static class WorkspaceSymbolTypeRequestor extends TypeNameMatchRequestor {
		private Set<SymbolInformation> symbols;
		private int maxResults;
		private boolean sourceOnly;
		private boolean isSymbolTagSupported;
		private IProgressMonitor monitor;

		public WorkspaceSymbolTypeRequestor(Set<SymbolInformation> symbols, int maxResults, boolean sourceOnly, boolean isSymbolTagSupported, IProgressMonitor monitor) {
			this.symbols = symbols;
			this.maxResults = maxResults;
			this.sourceOnly = sourceOnly;
			this.isSymbolTagSupported = isSymbolTagSupported;
			this.monitor = monitor;
		}

		@Override
		public void acceptTypeNameMatch(TypeNameMatch match) {
			try {
				if (maxResults > 0 && symbols.size() >= maxResults) {
					monitor.setCanceled(true);
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
					if (Flags.isDeprecated(match.getType().getFlags())) {
						if (isSymbolTagSupported) {
							symbolInformation.setTags(List.of(SymbolTag.Deprecated));
						} else {
							symbolInformation.setDeprecated(true);
						}
					}
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
	}

	private static class WorkspaceSymbolMethodRequestor extends MethodNameMatchRequestor {
		private Set<SymbolInformation> symbols;
		private int maxResults;
		private boolean isSymbolTagSupported;
		private IProgressMonitor monitor;

		public WorkspaceSymbolMethodRequestor(Set<SymbolInformation> symbols, int maxResults, boolean isSymbolTagSupported, IProgressMonitor monitor) {
			this.symbols = symbols;
			this.maxResults = maxResults;
			this.isSymbolTagSupported = isSymbolTagSupported;
			this.monitor = monitor;
		}

		@Override
		public void acceptMethodNameMatch(MethodNameMatch match) {
			try {
				if (maxResults > 0 && symbols.size() >= maxResults) {
					monitor.setCanceled(true);
					return;
				}

				Location location = null;
				try {
					location = JDTUtils.toLocation(match.getMethod());
				} catch (Exception e) {
					JavaLanguageServerPlugin.logException("Unable to determine location for " + match.getMethod().getElementName(), e);
					return;
				}

				if (location != null && match.getMethod().getElementName() != null && !match.getMethod().getElementName().isEmpty()) {
					SymbolInformation symbolInformation = new SymbolInformation();
					symbolInformation.setContainerName(match.getMethod().getDeclaringType().getFullyQualifiedName());
					symbolInformation.setName(match.getMethod().getElementName());
					symbolInformation.setKind(SymbolKind.Method);
					if (Flags.isDeprecated(match.getMethod().getFlags())) {
						if (isSymbolTagSupported) {
							symbolInformation.setTags(List.of(SymbolTag.Deprecated));
						} else {
							symbolInformation.setDeprecated(true);
						}
					}
					symbolInformation.setLocation(location);
					symbols.add(symbolInformation);
					if (maxResults > 0 && symbols.size() >= maxResults) {
						monitor.setCanceled(true);
					}
				}
			} catch (Exception e) {
				JavaLanguageServerPlugin.logException("Unable to determine location for " + match.getMethod().getElementName(), e);
				return;
			}
		}
	}
}
