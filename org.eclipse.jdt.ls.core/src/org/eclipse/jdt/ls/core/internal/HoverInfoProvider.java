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
package org.eclipse.jdt.ls.core.internal;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.jdt.ls.core.internal.javadoc.JavadocContentAccess2;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.google.common.io.CharStreams;

public class HoverInfoProvider {

	private static final long LABEL_FLAGS=
			JavaElementLabels.ALL_FULLY_QUALIFIED
			| JavaElementLabels.M_PRE_RETURNTYPE
			| JavaElementLabels.M_PARAMETER_ANNOTATIONS
			| JavaElementLabels.M_PARAMETER_TYPES
			| JavaElementLabels.M_PARAMETER_NAMES
			| JavaElementLabels.M_EXCEPTIONS
			| JavaElementLabels.F_PRE_TYPE_SIGNATURE
			| JavaElementLabels.M_PRE_TYPE_PARAMETERS
			| JavaElementLabels.T_TYPE_PARAMETERS
			| JavaElementLabels.USE_RESOLVED;

	private static final long LOCAL_VARIABLE_FLAGS= LABEL_FLAGS & ~JavaElementLabels.F_FULLY_QUALIFIED | JavaElementLabels.F_POST_QUALIFIED;

	private static final long COMMON_SIGNATURE_FLAGS = LABEL_FLAGS & ~JavaElementLabels.ALL_FULLY_QUALIFIED
			| JavaElementLabels.T_FULLY_QUALIFIED | JavaElementLabels.M_FULLY_QUALIFIED;

	private static final String LANGUAGE_ID = "java";

	private final ITypeRoot unit;

	private final PreferenceManager preferenceManager;

	public HoverInfoProvider(ITypeRoot aUnit, PreferenceManager preferenceManager) {
		this.unit = aUnit;
		this.preferenceManager = preferenceManager;
	}

	public List<Either<String, MarkedString>> computeHover(int line, int column, IProgressMonitor monitor) {
		List<Either<String, MarkedString>> res = new LinkedList<>();
		try {
			IJavaElement[] elements = JDTUtils.findElementsAtSelection(unit, line, column, this.preferenceManager, monitor);
			if(elements == null || elements.length == 0) {
				res.add(Either.forLeft(""));
				return res;
			}
			IJavaElement curr = null;
			if (elements.length != 1) {
				// they could be package fragments.
				// We need to select the one that matches the package fragment of the current unit
				IPackageFragment packageFragment = (IPackageFragment) unit.getParent();
				IJavaElement found =
						Stream
						.of(elements)
						.filter(e -> e.equals(packageFragment))
						.findFirst()
						.orElse(null);
				if (found == null) {
					// this would be a binary package fragment
					curr = elements[0];
				} else {
					curr = found;
				}
			} else {
				curr = elements[0];
			}
			boolean resolved = isResolved(curr, monitor);
			if (resolved) {
				MarkedString signature = this.computeSignature(curr);
				if (signature != null) {
					res.add(Either.forRight(signature));
				}
				String javadoc = computeJavadocHover(curr);
				if (javadoc != null) {
					res.add(Either.forLeft(javadoc));
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error computing hover", e);
		}
		return res;
	}

	private boolean isResolved(IJavaElement element, IProgressMonitor monitor) throws CoreException {
		if (!(unit instanceof ICompilationUnit)) {
			return true;
		}
		if (element == null) {
			return false;
		}
		if (element.getElementType() != IJavaElement.TYPE) {
			return true;
		}
		SearchPattern pattern = SearchPattern.createPattern(element, IJavaSearchConstants.ALL_OCCURRENCES);
		final boolean[] res = new boolean[1];
		res[0] = false;
		SearchEngine engine = new SearchEngine();
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { unit }, IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES);
		try {
			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope, new SearchRequestor() {

				@Override
				public void acceptSearchMatch(SearchMatch match) throws CoreException {
					if (match.getAccuracy() == SearchMatch.A_INACCURATE) {
						return;
					}
					Object o = match.getElement();
					if (o instanceof IJavaElement) {
						IJavaElement element = (IJavaElement) o;
						if (element.getElementType() == IJavaElement.TYPE) {
							res[0] = true;
							return;
						}
						ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
						if (compilationUnit == null) {
							return;
						}
						res[0] = true;
						throw new HoverException();
					}
				}
			}, monitor);
		} catch (HoverException | OperationCanceledException e) {
			// ignore
		}
		return res[0];
	}

	private MarkedString computeSignature(IJavaElement element)  {
		if (element == null) {
			return null;
		}
		String elementLabel = null;
		if (element instanceof ILocalVariable) {
			elementLabel = JavaElementLabels.getElementLabel(element,LOCAL_VARIABLE_FLAGS);
		} else {
			elementLabel = JavaElementLabels.getElementLabel(element,COMMON_SIGNATURE_FLAGS);
		}

		return new MarkedString(LANGUAGE_ID, elementLabel);
	}


	private String computeJavadocHover(IJavaElement element) throws CoreException {
		IMember member;
		if (element instanceof ITypeParameter) {
			member= ((ITypeParameter) element).getDeclaringMember();
		} else if (element instanceof IMember) {
			member= (IMember) element;
		} else if (element instanceof IPackageFragment) {
			Reader r = JavadocContentAccess2.getMarkdownContentReader(element);
			if(r == null ) {
				return null;
			}
			return getString(r);
		} else {
			return null;
		}

		Reader r = JavadocContentAccess2.getMarkdownContentReader(member);
		if(r == null ) {
			return null;
		}
		return getString(r);
	}

	/**
	 * Gets the reader content as a String
	 *
	 * @param reader the reader
	 * @return the reader content as string
	 */
	private static String getString(Reader reader) {
		try {
			return CharStreams.toString(reader);
		} catch (IOException ignored) {
			//meh
		}
		return null;
	}

	private class HoverException extends CoreException {

		private static final long serialVersionUID = 1L;

		public HoverException() {
			super(new Status(IStatus.OK, JavaLanguageServerPlugin.PLUGIN_ID, ""));
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}

	}
}
