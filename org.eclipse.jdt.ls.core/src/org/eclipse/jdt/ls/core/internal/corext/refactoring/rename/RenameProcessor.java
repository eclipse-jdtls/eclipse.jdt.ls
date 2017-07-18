/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corext.refactoring.rename;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
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
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public class RenameProcessor {

	protected IJavaElement fElement;

	public RenameProcessor(IJavaElement selectedElement) {
		fElement = selectedElement;
	}

	public void renameOccurrences(WorkspaceEdit edit, String newName, IProgressMonitor monitor) throws JavaModelException, CoreException {
		if (fElement == null || !canRename(fElement)) {
			return;
		}

		int oldNameLen = fElement.getElementName().length();

		IJavaElement[] elementsToSearch = null;

		if (fElement instanceof IMethod) {
			elementsToSearch = RippleMethodFinder.getRelatedMethods((IMethod) fElement, monitor, null);
		} else {
			elementsToSearch = new IJavaElement[] { fElement };
		}

		SearchPattern pattern = createOccurrenceSearchPattern(elementsToSearch);
		if (pattern == null) {
			return;
		}
		SearchEngine engine = new SearchEngine();
		engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, createSearchScope(), new SearchRequestor() {

			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				Object o = match.getElement();
				if (o instanceof IJavaElement) {
					IJavaElement element = (IJavaElement) o;
					ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
					if (compilationUnit == null) {
						return;
					}
					TextEdit replaceEdit = new ReplaceEdit(match.getOffset(), oldNameLen, newName);
					convert(edit, compilationUnit, replaceEdit);
				}
			}
		}, monitor);

	}

	protected SearchPattern createOccurrenceSearchPattern(IJavaElement[] elements) {
		if (elements == null || elements.length == 0) {
			return null;
		}
		Set<IJavaElement> set = new HashSet<>(Arrays.asList(elements));
		Iterator<IJavaElement> iter = set.iterator();
		IJavaElement first = iter.next();
		SearchPattern pattern = SearchPattern.createPattern(first, IJavaSearchConstants.ALL_OCCURRENCES);
		if (pattern == null) {
			throw new IllegalArgumentException("Invalid java element: " + first.getHandleIdentifier() + "\n" + first.toString());
		}
		while (iter.hasNext()) {
			IJavaElement each = iter.next();
			SearchPattern nextPattern = SearchPattern.createPattern(each, IJavaSearchConstants.ALL_OCCURRENCES);
			if (nextPattern == null) {
				throw new IllegalArgumentException("Invalid java element: " + each.getHandleIdentifier() + "\n" + each.toString());
			}
			pattern = SearchPattern.createOrPattern(pattern, nextPattern);
		}
		return pattern;
	}

	protected void convert(WorkspaceEdit root, ICompilationUnit unit, TextEdit edits) {
		TextEditConverter converter = new TextEditConverter(unit, edits);
		String uri = JDTUtils.getFileURI(unit);
		Map<String, List<org.eclipse.lsp4j.TextEdit>> changes = root.getChanges();
		if (changes.containsKey(uri)) {
			changes.get(uri).addAll(converter.convert());
		} else {
			changes.put(uri, converter.convert());
		}
	}

	protected IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES);
	}

	protected boolean canRename(IJavaElement element) {
		if (element instanceof IPackageFragment) {
			return false;
		}
		ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
		return compilationUnit != null;
	}
}
