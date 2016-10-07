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
package org.jboss.tools.vscode.java.internal;

import java.io.IOException;
import java.io.Reader;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.jboss.tools.vscode.java.internal.handlers.JsonRpcHelpers;

public class HoverInfoProvider {

	private final ITypeRoot unit;
	public HoverInfoProvider(ITypeRoot aUnit) {
		this.unit = aUnit;
	}

	public String computeHover(int line, int column) {
		try {
			IJavaElement[] elements = unit.codeSelect(JsonRpcHelpers.toOffset(unit.getBuffer(),line,column),0);
			if(elements == null) {
				return null;
			}
			IJavaElement curr = null;
			if (elements.length != 1) {
				// they could be package fragments.
				// We need to select the one that matches the package fragment of the current unit
<<<<<<< HEAD
				IJavaElement found = null;
				IPackageFragment packageFragment = (IPackageFragment) unit.getParent();
				loop: for (IJavaElement element : elements) {
					if (packageFragment.equals(element)) {
						found = element;
						break loop;
					}
				}
=======
				IPackageFragment packageFragment = (IPackageFragment) unit.getParent();
				IJavaElement found =
						Stream
						.of(elements)
						.filter(e -> e.equals(packageFragment))
						.findFirst()
						.orElse(null);
>>>>>>> e944b4f2041bd206d80d53861dc1579cbec6d266
				if (found == null) {
					// this would be a binary package fragment
					return computeJavadocHover(elements[0]);
				}
				curr = found;
			} else {
				curr = elements[0];
			}
			return computeJavadocHover(curr);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	private String computeJavadocHover(IJavaElement element) throws CoreException {
		IMember member;
		if (element instanceof ILocalVariable) {
			member= ((ILocalVariable) element).getDeclaringMember();
		} else if (element instanceof ITypeParameter) {
			member= ((ITypeParameter) element).getDeclaringMember();
		} else if (element instanceof IMember) {
			member= (IMember) element;
		} else if (element instanceof IPackageFragment) {
			Reader r = JavadocContentAccess.getHTMLContentReader((IPackageFragment) element, true);
			if(r == null ) return null;
			return getString(r);
		} else {
			return null;
		}

		IBuffer buf= member.getOpenable().getBuffer();
		if (buf == null) {
			return null; // no source attachment found
		}

		ISourceRange javadocRange= member.getJavadocRange();
		if(javadocRange == null ) return null;
		Reader r = JavadocContentAccess.getHTMLContentReader(member,true,true);
		if(r == null ) return null;
		return getString(r);
	}

	/**
	 * Gets the reader content as a String
	 *
	 * @param reader the reader
	 * @return the reader content as string
	 */
	private static String getString(Reader reader) {
		StringBuilder buf= new StringBuilder();
		char[] buffer= new char[1024];
		int count;
		try {
			while ((count= reader.read(buffer)) != -1)
				buf.append(buffer, 0, count);
		} catch (IOException e) {
			return null;
		}
		return buf.toString();
	}
}
