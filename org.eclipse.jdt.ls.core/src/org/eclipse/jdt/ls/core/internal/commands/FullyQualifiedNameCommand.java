/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.commands;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class FullyQualifiedNameCommand {

	public String getFullyQualifiedName(TextDocumentPositionParams params, IProgressMonitor monitor) {

		if (params == null) {
			return null;
		}

		TextDocumentIdentifier textDocument = params.getTextDocument();
		Position position = params.getPosition();

		if (textDocument == null || position == null) {
			return null;
		}

		try {
			ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(textDocument.getUri());

			if (typeRoot == null) {
				return null;
			}

			IJavaElement element = JDTUtils.findElementAtSelection(typeRoot, position.getLine(), position.getCharacter(), JavaLanguageServerPlugin.getPreferencesManager(), monitor);

			return getFullyQualifiedName(element);
		} catch (JavaModelException e) {
			return null;
		}

	}

	private String getFullyQualifiedName(IJavaElement element) throws JavaModelException {

		if (element instanceof IType type) {
			return type.getFullyQualifiedName();
		}

		if (element instanceof IMethod method) {
			IType declaringType = method.getDeclaringType();
			return declaringType == null ? method.getElementName() : declaringType.getFullyQualifiedName() + "." + method.getElementName();
		}

		if (element instanceof IField field) {
			IType declaringType = field.getDeclaringType();
			return declaringType == null ? field.getElementName() : declaringType.getFullyQualifiedName() + "." + field.getElementName();
		}

		if (element instanceof IPackageFragment packageFragment) {
			return packageFragment.getElementName();
		}

		if (element instanceof ILocalVariable variable) {
			IJavaElement parent = variable.getParent();
			String parentName = getFullyQualifiedName(parent);
			return parentName == null ? variable.getElementName() : parentName + "." + variable.getElementName();
		}

		return null;
	}

}
