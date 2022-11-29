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
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
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
		ITypeRoot typeRoot = null;
		try {
			boolean returnCompilationUnit = preferenceManager == null ? false : preferenceManager.isClientSupportsClassFileContent() && (preferenceManager.getPreferences().isIncludeDecompiledSources());
			typeRoot = JDTUtils.resolveTypeRoot(param.getTextDocument().getUri(), returnCompilationUnit, monitor);
			if (typeRoot == null) {
				return locations;
			}
			IJavaElement elementToSearch = JDTUtils.findElementAtSelection(typeRoot, param.getPosition().getLine(), param.getPosition().getCharacter(), this.preferenceManager, monitor);
			if (elementToSearch == null) {
				int offset = JsonRpcHelpers.toOffset(typeRoot.getBuffer(), param.getPosition().getLine(), param.getPosition().getCharacter());
				elementToSearch = typeRoot.getElementAt(offset);
			}
			if (elementToSearch == null) {
				return locations;
			}
			search(elementToSearch, locations, monitor, param.getContext().isIncludeDeclaration());
			if (monitor.isCanceled()) {
				return Collections.emptyList();
			}
			if (preferenceManager.getPreferences().isIncludeAccessors() && elementToSearch instanceof IField field) { // IField
				IMethod getter = GetterSetterUtil.getGetter(field);
				if (getter != null) {
					search(getter, locations, monitor, false);
				}
				if (monitor.isCanceled()) {
					return Collections.emptyList();
				}
				IMethod setter = GetterSetterUtil.getSetter(field);
				if (setter != null) {
					search(setter, locations, monitor, false);
				}
				if (monitor.isCanceled()) {
					return Collections.emptyList();
				}
				String builderName = getBuilderName(field);
				IType builder = field.getJavaProject().findType(builderName);
				if (monitor.isCanceled()) {
					return Collections.emptyList();
				}
				if (builder != null) {
					String fieldSignature = field.getTypeSignature();
					for (IMethod method : builder.getMethods()) {
						String[] parameters = method.getParameterTypes();
						if (parameters.length == 1 && field.getElementName().equals(method.getElementName()) && fieldSignature.equals(parameters[0])) {
							search(method, locations, monitor, false);
						}
					}
				}
				if (monitor.isCanceled()) {
					return Collections.emptyList();
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Find references failure ", e);
		} finally {
			JDTUtils.discardClassFileWorkingCopy(typeRoot);
		}
		return locations;
	}

	private String getBuilderName(IField field) {
		IType declaringType = field.getDeclaringType();
		IAnnotation annotation = declaringType.getAnnotation("Builder");
		if (annotation == null || !annotation.exists()) {
			annotation = declaringType.getAnnotation("lombok.Builder");
		}
		if (annotation != null && annotation.exists()) {
			try {
				for (IMemberValuePair pair : annotation.getMemberValuePairs()) {
					if (pair.getValueKind() == IMemberValuePair.K_STRING) {
						String memberName = pair.getMemberName();
						Object value = pair.getValue();
						if ("builderClassName".equals(memberName) && value instanceof String stringValue && !stringValue.isEmpty()) {
							return declaringType.getFullyQualifiedName() + "." + stringValue;
						}
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		return declaringType.getFullyQualifiedName() + "." + declaringType.getElementName() + "Builder";
	}

	private void search(IJavaElement elementToSearch, final List<Location> locations, IProgressMonitor monitor, boolean isIncludeDeclaration) throws CoreException, JavaModelException {
		boolean includeClassFiles = preferenceManager.isClientSupportsClassFileContent();
		boolean includeDecompiledSources = preferenceManager.getPreferences().isIncludeDecompiledSources();
		SearchEngine engine = new SearchEngine();
		SearchPattern pattern = SearchPattern.createPattern(elementToSearch, IJavaSearchConstants.REFERENCES);
		if (isIncludeDeclaration) {
			SearchPattern patternDecl = SearchPattern.createPattern(elementToSearch, IJavaSearchConstants.DECLARATIONS);
			pattern = SearchPattern.createOrPattern(pattern, patternDecl);
		}
		engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, createSearchScope(), new SearchRequestor() {

			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				if (match.getAccuracy() == SearchMatch.A_INACCURATE) {
					return;
				}
				Object o = match.getElement();
				if (o instanceof IJavaElement element) {
					ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
					if (compilationUnit != null) {
						Location location = JDTUtils.toLocation(compilationUnit, match.getOffset(), match.getLength());
						locations.add(location);
					} else if (includeClassFiles) {
						IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
						if (cf != null && cf.getSourceRange() != null) {
							Location location = JDTUtils.toLocation(cf, match.getOffset(), match.getLength());
							locations.add(location);
						} else if (includeDecompiledSources && cf != null) {
							List<Location> result = JDTUtils.searchDecompiledSources(element, cf, false, false, monitor);
							locations.addAll(result);
						}
					}

				}
			}
		}, monitor);
	}

}