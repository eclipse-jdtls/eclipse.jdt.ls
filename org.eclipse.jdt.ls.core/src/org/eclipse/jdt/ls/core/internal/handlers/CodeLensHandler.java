/*******************************************************************************
 * Copyright (c) 2016-2018 Red Hat Inc. and others.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

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
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.google.gson.JsonArray;

public class CodeLensHandler {

	private static final String JAVA_SHOW_REFERENCES_COMMAND = "java.show.references";
	private static final String JAVA_SHOW_IMPLEMENTATIONS_COMMAND = "java.show.implementations";
	public static final String IMPLEMENTATION_TYPE = "implementations";
	public static final String REFERENCES_TYPE = "references";

	private final PreferenceManager preferenceManager;

	public CodeLensHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public CodeLens resolve(CodeLens lens, IProgressMonitor monitor) {
		if (lens == null) {
			return null;
		}
		//Note that codelens resolution is honored if the request was emitted
		//before disabling codelenses in the preferences, else invalid codeLenses
		//(i.e. having no commands) would be returned.
		final JsonArray data = (JsonArray) lens.getData();
		final String type = JSONUtility.toModel(data.get(2), String.class);
		final Position position = JSONUtility.toModel(data.get(1), Position.class);
		final String uri = JSONUtility.toModel(data.get(0), String.class);

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
				IJavaElement element = JDTUtils.findElementAtSelection(typeRoot, position.getLine(), position.getCharacter(), this.preferenceManager, monitor);
				if (REFERENCES_TYPE.equals(type)) {
					try {
						locations = findReferences(element, monitor);
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
				} else if (IMPLEMENTATION_TYPE.equals(type)) {
					if (element instanceof IType typeElement) {
						try {
							IDocument document = JsonRpcHelpers.toDocument(typeRoot.getBuffer());
							int offset = document.getLineOffset(position.getLine()) + position.getCharacter();
							locations = findImplementations(typeRoot, typeElement, offset, monitor);
						} catch (CoreException | BadLocationException e) {
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

	private List<Location> findImplementations(ITypeRoot root, IType type, int offset, IProgressMonitor monitor) throws CoreException {
		//java.lang.Object is a special case. We need to minimize heavy cost of I/O,
		// by avoiding opening all files from the Object hierarchy
		boolean useDefaultLocation = "java.lang.Object".equals(type.getFullyQualifiedName());
		ImplementationToLocationMapper mapper = new ImplementationToLocationMapper(preferenceManager.isClientSupportsClassFileContent(), useDefaultLocation);
		ImplementationCollector<Location> searcher = new ImplementationCollector<>(root, new Region(offset, 0), type, mapper);
		return searcher.findImplementations(monitor);
	}

	private List<Location> findReferences(IJavaElement element, IProgressMonitor monitor)
			throws JavaModelException, CoreException {
		if (element == null) {
			return Collections.emptyList();
		}
		SearchPattern pattern = SearchPattern.createPattern(element, IJavaSearchConstants.REFERENCES);
		final List<Location> result = new ArrayList<>();
		SearchEngine engine = new SearchEngine();
		engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, createSearchScope(), new SearchRequestor() {

			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				if (match.getAccuracy() == SearchMatch.A_INACCURATE) {
					return;
				}
				Object o = match.getElement();
				if (o instanceof IJavaElement element) {
					ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
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
			LinkedHashSet<CodeLens> lenses = new LinkedHashSet<>(elements.length);
			collectCodeLenses(typeRoot, elements, lenses, monitor);
			if (monitor.isCanceled()) {
				lenses.clear();
			}
			return new ArrayList<>(lenses);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting code lenses for" + unit.getElementName(), e);
		}
		return Collections.emptyList();
	}

	private void collectCodeLenses(ITypeRoot typeRoot, IJavaElement[] elements, Collection<CodeLens> lenses,
			IProgressMonitor monitor)
			throws JavaModelException {
		for (IJavaElement element : elements) {
			if (monitor.isCanceled()) {
				return;
			}
			if (element.getElementType() == IJavaElement.TYPE) {
				collectCodeLenses(typeRoot, ((IType) element).getChildren(), lenses, monitor);
			} else if (element.getElementType() == IJavaElement.METHOD) {
				if (JDTUtils.isHiddenGeneratedElement(element)) {
					continue;
				}
				//ignore element if method range overlaps the type range, happens for generated bytcode, i.e. with lombok
				IJavaElement parentType = element.getAncestor(IJavaElement.TYPE);
				if (parentType != null && overlaps(((ISourceReference) parentType).getNameRange(), ((ISourceReference) element).getNameRange())) {
					continue;
				}
			} else {//neither a type nor a method, we bail
				continue;
			}

			if (preferenceManager.getPreferences().isReferencesCodeLensEnabled()) {
				CodeLens lens = getCodeLens(REFERENCES_TYPE, element, typeRoot);
				if (lens != null) {
					lenses.add(lens);
				}
			}
			if (preferenceManager.getPreferences().isImplementationsCodeLensEnabled() && element instanceof IType type) {
				if (type.isInterface() || Flags.isAbstract(type.getFlags())) {
					CodeLens lens = getCodeLens(IMPLEMENTATION_TYPE, element, typeRoot);
					if (lens != null) {
						lenses.add(lens);
					}
				}
			}
		}
	}

	private boolean overlaps(ISourceRange typeRange, ISourceRange methodRange) {
		if (typeRange == null || methodRange == null) {
			return false;
		}
		//method range is overlapping if it appears before or actually overlaps the type's range
		return methodRange.getOffset() < typeRange.getOffset() || methodRange.getOffset() >= typeRange.getOffset() && methodRange.getOffset() <= (typeRange.getOffset() + typeRange.getLength());
	}

	private CodeLens getCodeLens(String type, IJavaElement element, ITypeRoot typeRoot) throws JavaModelException {
		ISourceRange r = ((ISourceReference) element).getNameRange();
		if (r == null) {
			return null;
		}
		CodeLens lens = new CodeLens();
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
