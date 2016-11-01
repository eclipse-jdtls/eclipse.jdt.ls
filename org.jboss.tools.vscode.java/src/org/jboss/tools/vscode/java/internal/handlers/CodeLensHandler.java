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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.jboss.tools.langs.CodeLens;
import org.jboss.tools.langs.CodeLensParams;
import org.jboss.tools.langs.Command;
import org.jboss.tools.langs.Location;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

public class CodeLensHandler {

	public class CodeLensProvider implements RequestHandler<CodeLensParams, List<CodeLens>>{

		@Override
		public boolean canHandle(String request) {
			return LSPMethods.DOCUMENT_CODELENS.getMethod().equals(request);
		}

		@Override
		public List<CodeLens> handle(CodeLensParams param,CancelMonitor cm) {
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(param.getTextDocument().getUri());
			return getCodeLensSymbols(unit);
		}

	}

	public class CodeLensResolver implements RequestHandler<CodeLens, CodeLens>{

		@Override
		public boolean canHandle(String request) {
			return LSPMethods.CODELENS_RESOLVE.getMethod().equals(request);
		}

		@Override
		public CodeLens handle(CodeLens param, CancelMonitor cm) {
			return resolve(param);
		}
	}


	@SuppressWarnings("unchecked")
	private  CodeLens resolve(CodeLens lens) {
		try {
			List<Object> data = (List<Object>) lens.getData();
			String uri = (String) data.get(0);
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
			if (unit == null) {
				return lens;
			}
			Map<String, Object> position = (Map<String, Object>) data.get(1);
			IJavaElement element = JDTUtils.findElementAtSelection(unit,  ((Double)position.get("line")).intValue(), ((Double)position.get("character")).intValue());
			List<Location> locations = findReferences(element);
			int nReferences = locations.size();
			lens.setCommand(new Command().withTitle(nReferences == 1 ? "1 reference" : nReferences + " references")
					.withCommand("java.show.references")
					.withArguments(Arrays.asList(uri, position, locations)));
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem resolving code lens", e);
		}
		return lens;
	}

	private List<Location> findReferences(IJavaElement element) throws JavaModelException, CoreException {
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
		}, new NullProgressMonitor());

		return result;
	}

	private List<CodeLens> getCodeLensSymbols(ICompilationUnit unit) {
		if(unit == null || !unit.getResource().exists()) return Collections.emptyList();
		try {
			IJavaElement[] elements = unit.getChildren();
			ArrayList<CodeLens> lenses = new ArrayList<>(elements.length);
			collectChildren(unit, elements, lenses);
			return lenses;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting code lenses for" + unit.getElementName(), e);
		}
		return Collections.emptyList();
	}

	private void collectChildren(ICompilationUnit unit, IJavaElement[] elements, ArrayList<CodeLens> lenses)
			throws JavaModelException {
		for (IJavaElement element : elements) {
			if (element.getElementType() == IJavaElement.TYPE) {
				collectChildren(unit, ((IType) element).getChildren(), lenses);
			} else if (element.getElementType() != IJavaElement.METHOD) {
				continue;
			}

			CodeLens lens = new CodeLens();
			ISourceRange r = ((ISourceReference) element).getNameRange();
			final org.jboss.tools.langs.Range range = JDTUtils.toRange(unit, r.getOffset(), r.getLength());
			lens.setRange(range);
			lens.setData(Arrays.asList(JDTUtils.getFileURI(unit), range.getStart()));
			lenses.add(lens);
		}
	}

	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES);
	}
}
