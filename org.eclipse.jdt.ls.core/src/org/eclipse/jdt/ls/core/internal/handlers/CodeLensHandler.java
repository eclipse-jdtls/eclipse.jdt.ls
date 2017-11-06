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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
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
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

public class CodeLensHandler {

	private static final String JAVA_SHOW_REFERENCES_COMMAND = "java.show.references";
	private static final String JAVA_SHOW_IMPLEMENTATIONS_COMMAND = "java.show.implementations";
	private static final String IMPLEMENTATION_TYPE = "implementations";
	private static final String REFERENCES_TYPE = "references";

	private final PreferenceManager preferenceManager;

	public CodeLensHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	@SuppressWarnings("unchecked")
	public CodeLens resolve(CodeLens lens, IProgressMonitor monitor) {
		if (lens == null) {
			return null;
		}
		//Note that codelens resolution is honored if the request was emitted
		//before disabling codelenses in the preferences, else invalid codeLenses
		//(i.e. having no commands) would be returned.
		List<Object> data = (List<Object>) lens.getData();
		String type = (String) data.get(2);
		Map<String, Object> position = (Map<String, Object>) data.get(1);
		String uri = (String) data.get(0);
		String label = null;
		String command = null;
		List<Location> locations = null;
		if (REFERENCES_TYPE.equals(type)) {
			label = "reference";
			command = JAVA_SHOW_REFERENCES_COMMAND;
		} else if (IMPLEMENTATION_TYPE.equals(type)) {
			label = "implementation";
			command = JAVA_SHOW_IMPLEMENTATIONS_COMMAND;
		}
		try {
			ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
			if (typeRoot != null) {
				IJavaElement element = JDTUtils.findElementAtSelection(typeRoot, ((Double) position.get("line")).intValue(), ((Double) position.get("character")).intValue(), this.preferenceManager, monitor);
				if (REFERENCES_TYPE.equals(type)) {
					try {
						locations = findReferences(element, monitor);
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
				} else if (IMPLEMENTATION_TYPE.equals(type)) {
					if (element instanceof IType) {
						try {
							locations = findImplementations((IType) element, monitor);
						} catch (CoreException e) {
							JavaLanguageServerPlugin.logException(e.getMessage(), e);
						}
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem resolving code lens", e);
		}
		if (locations == null) {
			locations = Collections.emptyList();
		}
		if (label != null && command != null) {
			int size = locations.size();
			Command c = new Command(size + " " + label + ((size == 1) ? "" : "s"), command, Arrays.asList(uri, position, locations));
			lens.setCommand(c);
		}
		return lens;
	}

	private List<Location> findImplementations(IType type, IProgressMonitor monitor) throws JavaModelException {
		IType[] results = type.newTypeHierarchy(monitor).getAllSubtypes(type);
		final List<Location> result = new ArrayList<>();
		for (IType t : results) {
			ICompilationUnit compilationUnit = (ICompilationUnit) t.getAncestor(IJavaElement.COMPILATION_UNIT);
			if (compilationUnit == null) {
				continue;
			}
			Location location = JDTUtils.toLocation(t);
			result.add(location);
		}
		return result;
	}

	private List<Location> findReferences(IJavaElement element, IProgressMonitor monitor)
			throws JavaModelException, CoreException {
		if (element == null) {
			return Collections.emptyList();
		}
		SearchPattern pattern = SearchPattern.createPattern(element, IJavaSearchConstants.REFERENCES);
		final List<Location> result = new ArrayList<>();
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
					Location location = JDTUtils.toLocation(compilationUnit, match.getOffset(), match.getLength());
					result.add(location);
				}
			}
		}, monitor);

		return result;
	}

	public List<CodeLens> getCodeLensSymbols(String uri, IProgressMonitor monitor) {
		if (!preferenceManager.getPreferences().isCodeLensEnabled()) {
			return Collections.emptyList();
		}
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		IClassFile classFile = null;
		if (unit == null) {
			classFile = JDTUtils.resolveClassFile(uri);
			if (classFile == null) {
				return Collections.emptyList();
			}
		} else {
			if (!unit.getResource().exists() || monitor.isCanceled()) {
				return Collections.emptyList();
			}
		}
		try {
			ITypeRoot typeRoot = unit != null ? unit : classFile;
			IJavaElement[] elements = typeRoot.getChildren();
			ArrayList<CodeLens> lenses = new ArrayList<>(elements.length);
			collectCodeLenses(typeRoot, elements, lenses, monitor);
			if (monitor.isCanceled()) {
				lenses.clear();
			}
			return lenses;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting code lenses for" + unit.getElementName(), e);
		}
		return Collections.emptyList();
	}

	private void collectCodeLenses(ITypeRoot typeRoot, IJavaElement[] elements, ArrayList<CodeLens> lenses,
			IProgressMonitor monitor)
			throws JavaModelException {
		for (IJavaElement element : elements) {
			if (monitor.isCanceled()) {
				return;
			}
			if (element.getElementType() == IJavaElement.TYPE) {
				collectCodeLenses(typeRoot, ((IType) element).getChildren(), lenses, monitor);
			} else if (element.getElementType() != IJavaElement.METHOD || JDTUtils.isHiddenGeneratedElement(element)) {
				continue;
			}

			if (preferenceManager.getPreferences().isReferencesCodeLensEnabled()) {
				CodeLens lens = getCodeLens(REFERENCES_TYPE, element, typeRoot);
				lenses.add(lens);
			}
			if (preferenceManager.getPreferences().isImplementationsCodeLensEnabled() && element instanceof IType) {
				IType type = (IType) element;
				if (type.isInterface() || Flags.isAbstract(type.getFlags())) {
					CodeLens lens = getCodeLens(IMPLEMENTATION_TYPE, element, typeRoot);
					lenses.add(lens);
				}
			}
		}
	}

	private CodeLens getCodeLens(String type, IJavaElement element, ITypeRoot typeRoot) throws JavaModelException {
		CodeLens lens = new CodeLens();
		ISourceRange r = ((ISourceReference) element).getNameRange();
		final Range range = JDTUtils.toRange(typeRoot, r.getOffset(), r.getLength());
		lens.setRange(range);
		String uri = ResourceUtils.toClientUri(JDTUtils.toUri(typeRoot));
		lens.setData(Arrays.asList(uri, range.getStart(), type));
		return lens;
	}

	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES);
	}
}
