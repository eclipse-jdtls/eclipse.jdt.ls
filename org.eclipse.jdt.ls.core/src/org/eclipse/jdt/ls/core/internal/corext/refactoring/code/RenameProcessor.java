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

package org.eclipse.jdt.ls.core.internal.corext.refactoring.code;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
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

	public void renameOccurrences(WorkspaceEdit edit, IJavaElement element, String newName, IProgressMonitor monitor) throws JavaModelException, CoreException {
		if (element == null || !canRename(element)) {
			return;
		}

		int oldNameLen = element.getElementName().length();

		SearchPattern pattern = SearchPattern.createPattern(element, IJavaSearchConstants.ALL_OCCURRENCES);
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
					TextEdit repalceEdit = new ReplaceEdit(match.getOffset(), oldNameLen, newName);
					convert(edit, compilationUnit, repalceEdit);
					// Location location = JDTUtils.toLocation(compilationUnit, match.getOffset(), match.getLength());
					// result.add(match);
				}
			}
		}, monitor);

	}

	private void convert(WorkspaceEdit root, ICompilationUnit unit, TextEdit edits) {
		TextEditConverter converter = new TextEditConverter(unit, edits);
		String uri = JDTUtils.getFileURI(unit);
		Map<String, List<org.eclipse.lsp4j.TextEdit>> changes = root.getChanges();
		if (changes.containsKey(uri)) {
			changes.get(uri).addAll(converter.convert());
		} else {
			root.getChanges().put(uri, converter.convert());
		}
	}

	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES);
	}

	private boolean canRename(IJavaElement element) {
		if (element instanceof IPackageFragment) {
			return false;
		}
		ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
		return compilationUnit != null;
	}
}
