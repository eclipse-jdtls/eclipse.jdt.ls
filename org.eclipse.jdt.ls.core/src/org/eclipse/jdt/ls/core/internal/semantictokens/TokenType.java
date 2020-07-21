/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *     0dinD - Semantic highlighting improvements - https://github.com/eclipse/eclipse.jdt.ls/pull/1501
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.semantictokens;

import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public enum TokenType {
	PACKAGE("namespace"),
	CLASS("class"),
	INTERFACE("interface"),
	ENUM("enum"),
	ENUM_MEMBER("enumMember"),
	TYPE("type"),
	TYPE_PARAMETER("typeParameter"),
	ANNOTATION("annotation"),
	ANNOTATION_MEMBER("annotationMember"),
	METHOD("function"),
	PROPERTY("property"),
	VARIABLE("variable"),
	PARAMETER("parameter");

	/**
	 * This is the name of the token type given to the client, so it
	 * should be as generic as possible and follow the "standard" (see below)
	 * token type names where applicable. For example, the generic name of a
	 * method type should be "function", since methods are essentially functions,
	 * but declared on a class. This makes life easier for theme authors, since
	 * they don't need to think about Java-specific terminology.
	 *
	 * @see https://code.visualstudio.com/api/language-extensions/semantic-highlight-guide#semantic-token-classification
	 */
	private String genericName;

	TokenType(String genericName) {
		this.genericName = genericName;
	}

	@Override
	public String toString() {
		return genericName;
	}

	/**
	* Returns the semantic token type that applies to a binding, or
	* {@code null} if there is no token type that applies to the binding.
	*
	* @param binding A binding.
	* @return The semantic token type that applies to the binding, or
	* {@code null} if there is no token type that applies to the binding.
	*/
	public static TokenType getApplicableType(IBinding binding) {
		if (binding == null) {
			return null;
		}

		switch (binding.getKind()) {
			case IBinding.VARIABLE: {
				IVariableBinding variableBinding = (IVariableBinding) binding;
				if (variableBinding.isEnumConstant()) {
					return TokenType.ENUM_MEMBER;
				}
				if (variableBinding.isField()) {
					return TokenType.PROPERTY;
				}
				if (variableBinding.isParameter()) {
					return TokenType.PARAMETER;
				}
				return TokenType.VARIABLE;
			}
			case IBinding.METHOD: {
				IMethodBinding methodBinding = (IMethodBinding) binding;
				if (methodBinding.isAnnotationMember()) {
					return TokenType.ANNOTATION_MEMBER;
				}
				return TokenType.METHOD;
			}
			case IBinding.TYPE: {
				ITypeBinding typeBinding = (ITypeBinding) binding;
				if (typeBinding.isTypeVariable()) {
					return TokenType.TYPE_PARAMETER;
				}
				if (typeBinding.isAnnotation()) {
					return TokenType.ANNOTATION;
				}
				if (typeBinding.isClass()) {
					return TokenType.CLASS;
				}
				if (typeBinding.isInterface()) {
					return TokenType.INTERFACE;
				}
				if (typeBinding.isEnum()) {
					return TokenType.ENUM;
				}
				return TokenType.TYPE;
			}
			case IBinding.PACKAGE: {
				return TokenType.PACKAGE;
			}
			default:
			return null;
		}
	}
}
