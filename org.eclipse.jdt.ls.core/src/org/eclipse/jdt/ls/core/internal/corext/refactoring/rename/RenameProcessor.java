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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.MethodDeclarationMatch;
import org.eclipse.jdt.core.search.MethodReferenceMatch;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.ResourceChange;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public class RenameProcessor {

	protected IJavaElement fElement;

	private IJavaProject fProjectCache;
	private IScanner fScannerCache;

	protected PreferenceManager preferenceManager;

	public RenameProcessor(IJavaElement selectedElement, PreferenceManager preferenceManager) {
		fElement = selectedElement;
		this.preferenceManager = preferenceManager;
	}

	public void renameOccurrences(WorkspaceEdit edit, String newName, IProgressMonitor monitor) throws CoreException {
		if (fElement == null || !canRename()) {
			return;
		}

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
					TextEdit replaceEdit = collectMatch(match, element, compilationUnit, newName);
					if (replaceEdit != null) {
						convert(edit, compilationUnit, replaceEdit);
					}
				}
			}
		}, monitor);

	}

	protected SearchPattern createOccurrenceSearchPattern(IJavaElement[] elements) throws CoreException {
		if (elements == null || elements.length == 0) {
			return null;
		}
		Set<IJavaElement> set = new HashSet<>(Arrays.asList(elements));
		Iterator<IJavaElement> iter = set.iterator();
		IJavaElement first = iter.next();
		SearchPattern pattern = SearchPattern.createPattern(first, IJavaSearchConstants.ALL_OCCURRENCES);
		if (pattern == null) {
			throw new CoreException(Status.CANCEL_STATUS);
		}
		while (iter.hasNext()) {
			IJavaElement each = iter.next();
			SearchPattern nextPattern = SearchPattern.createPattern(each, IJavaSearchConstants.ALL_OCCURRENCES);
			if (nextPattern == null) {
				throw new CoreException(Status.CANCEL_STATUS);
			}
			pattern = SearchPattern.createOrPattern(pattern, nextPattern);
		}
		return pattern;
	}

	protected void convert(WorkspaceEdit root, ICompilationUnit unit, TextEdit edits) {
		TextEditConverter converter = new TextEditConverter(unit, edits);
		String uri = JDTUtils.toURI(unit);
		if (preferenceManager.getClientPreferences().isWorkspaceEditResourceChangesSupported()) {
			List<Either<ResourceChange, TextDocumentEdit>> changes = root.getResourceChanges();
			if (changes == null) {
				changes = new LinkedList<>();
				root.setResourceChanges(changes);
			}
			changes.add(Either.forRight(converter.convertToTextDocumentEdit(0)));
		} else {
			Map<String, List<org.eclipse.lsp4j.TextEdit>> changes = root.getChanges();
			if (changes.containsKey(uri)) {
				changes.get(uri).addAll(converter.convert());
			} else {
				changes.put(uri, converter.convert());
			}
		}
	}

	protected IJavaSearchScope createSearchScope() throws JavaModelException {
		return SearchEngine.createWorkspaceScope();
	}

	protected boolean canRename() throws CoreException {
		if (fElement instanceof IPackageFragment) {
			return false;
		}
		ICompilationUnit compilationUnit = (ICompilationUnit) fElement.getAncestor(IJavaElement.COMPILATION_UNIT);
		return compilationUnit != null;
	}

	private TextEdit collectMatch(SearchMatch match, IJavaElement element, ICompilationUnit unit, String newName) throws IndexOutOfBoundsException, JavaModelException {
		if (match instanceof MethodReferenceMatch && ((MethodReferenceMatch) match).isSuperInvocation() && match.getAccuracy() == SearchMatch.A_INACCURATE) {
			return null;
		}

		if (!(element instanceof IMethod) || match.isImplicit()) {
			return new ReplaceEdit(match.getOffset(), match.getLength(), newName);
		}

		int start = match.getOffset();
		int length = match.getLength();
		String matchText = unit.getBuffer().getText(start, length);

		//direct match:
		if (newName.equals(matchText)) {
			return new ReplaceEdit(match.getOffset(), match.getLength(), newName);
		}

		// lambda expression
		if (match instanceof MethodDeclarationMatch && match.getElement() instanceof IMethod && ((IMethod) match.getElement()).isLambdaMethod()) {
			// don't touch the lambda
			return null;
		}

		//Not a standard reference -- use scanner to find last identifier token before left parenthesis:
		IScanner scanner = getScanner(unit);
		scanner.setSource(matchText.toCharArray());
		int simpleNameStart = -1;
		int simpleNameEnd = -1;
		try {
			int token = scanner.getNextToken();
			while (token != ITerminalSymbols.TokenNameEOF && token != ITerminalSymbols.TokenNameLPAREN) { // reference in code includes arguments in parentheses
				if (token == ITerminalSymbols.TokenNameIdentifier) {
					simpleNameStart = scanner.getCurrentTokenStartPosition();
					simpleNameEnd = scanner.getCurrentTokenEndPosition();
				}
				token = scanner.getNextToken();
			}
		} catch (InvalidInputException e) {
			//ignore
		}
		if (simpleNameStart != -1) {
			match.setOffset(start + simpleNameStart);
			match.setLength(simpleNameEnd + 1 - simpleNameStart);
		}
		return new ReplaceEdit(match.getOffset(), match.getLength(), newName);
	}

	protected IScanner getScanner(ICompilationUnit unit) {
		IJavaProject project = unit.getJavaProject();
		if (project.equals(fProjectCache)) {
			return fScannerCache;
		}

		fProjectCache = project;
		String sourceLevel = project.getOption(JavaCore.COMPILER_SOURCE, true);
		String complianceLevel = project.getOption(JavaCore.COMPILER_COMPLIANCE, true);
		fScannerCache = ToolFactory.createScanner(false, false, false, sourceLevel, complianceLevel);
		return fScannerCache;
	}
}
