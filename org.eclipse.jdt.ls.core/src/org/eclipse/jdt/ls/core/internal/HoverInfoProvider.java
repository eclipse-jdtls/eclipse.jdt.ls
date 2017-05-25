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
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.jdt.ls.core.internal.javadoc.JavadocContentAccess;
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

	public HoverInfoProvider(ITypeRoot aUnit) {
		this.unit = aUnit;
	}

	public List<Either<String, MarkedString>> computeHover(int line, int column) {
		List<Either<String, MarkedString>> res = new LinkedList<>();
		try {
			IJavaElement[] elements = JDTUtils.findElementsAtSelection(unit, line, column);
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
			MarkedString signature = this.computeSignature(curr);
			if (signature != null) {
				res.add(Either.forRight(signature));
			}
			String javadoc = computeJavadocHover(curr);
			if (javadoc != null) {
				res.add(Either.forLeft(javadoc));
			}

		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error computing hover", e);
		}
		return res;
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
			Reader r = JavadocContentAccess.getMarkdownContentReader((IPackageFragment) element, true);
			if(r == null ) {
				return null;
			}
			return getString(r);
		} else {
			return null;
		}

		IBuffer buf= member.getOpenable().getBuffer();
		if (buf == null) {
			return null; // no source attachment found
		}

		Reader r = JavadocContentAccess.getMarkdownContentReader(member);
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
}
