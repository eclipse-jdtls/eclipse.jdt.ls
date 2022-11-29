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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.lsp4j.SemanticTokenModifiers;

public enum TokenModifier {
	// Standard LSP token modifiers, see https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens
	ABSTRACT(SemanticTokenModifiers.Abstract),
	STATIC(SemanticTokenModifiers.Static),
	FINAL(SemanticTokenModifiers.Readonly),
	DEPRECATED(SemanticTokenModifiers.Deprecated),
	DECLARATION(SemanticTokenModifiers.Declaration),
	DOCUMENTATION(SemanticTokenModifiers.Documentation),

	// Custom token modifiers
	PUBLIC("public"),
	PRIVATE("private"),
	PROTECTED("protected"),
	NATIVE("native"),
	GENERIC("generic"),
	TYPE_ARGUMENT("typeArgument"),
	IMPORT_DECLARATION("importDeclaration"),
	CONSTRUCTOR("constructor");

	/**
	 * This is the name of the token modifier given to the client, so it
	 * should be as generic as possible and follow the standard LSP (see below)
	 * token modifier names where applicable. For example, the generic name of the
	 * {@link #FINAL} modifier is "readonly", since it has similar meaning.
	 * Using standardized names makes life easier for theme authors, since
	 * they don't need to know about language-specific terminology.
	 *
	 * @see https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens
	 */
	private final String genericName;

	/**
	 * The bitmask for this semantic token modifier.
	 * Use bitwise OR to combine with other token modifiers.
	 */
	public final int bitmask = 1 << ordinal();

	/**
	 * The inverse bitmask for this semantic token modifier.
	 * Use bitwise AND to remove from other token modifiers.
	 */
	public final int inverseBitmask = ~bitmask;

	TokenModifier(String genericName) {
		this.genericName = genericName;
	}

	@Override
	public String toString() {
		return genericName;
	}

	/**
	 * Returns the bitwise OR of all the semantic token modifiers that apply
	 * based on the binding's {@link Modifier}s and wheter or not it is deprecated.
	 *
	 * @param binding A binding.
	 * @return The bitwise OR of the applicable modifiers for the binding.
	 */
	public static int checkJavaModifiers(IBinding binding) {
		if (binding == null) {
			return 0;
		}

		int modifiers = 0;
		int bindingModifiers = binding.getModifiers();
		if (Modifier.isPublic(bindingModifiers)) {
			modifiers |= PUBLIC.bitmask;
		}
		if (Modifier.isPrivate(bindingModifiers)) {
			modifiers |= PRIVATE.bitmask;
		}
		if (Modifier.isProtected(bindingModifiers)) {
			modifiers |= PROTECTED.bitmask;
		}
		if (Modifier.isAbstract(bindingModifiers)) {
			modifiers |= ABSTRACT.bitmask;
		}
		if (Modifier.isStatic(bindingModifiers)) {
			modifiers |= STATIC.bitmask;
		}
		if (Modifier.isFinal(bindingModifiers)) {
			modifiers |= FINAL.bitmask;
		}
		if (Modifier.isNative(bindingModifiers)) {
			modifiers |= NATIVE.bitmask;
		}
		if (binding.isDeprecated()) {
			modifiers |= DEPRECATED.bitmask;
		}
		return modifiers;
	}

	/**
	 * Checks whether or not a binding represents a constructor.
	 *
	 * @param binding A binding.
	 * @return A bitmask with the {@link #CONSTRUCTOR} bit set accordingly.
	 */
	public static int checkConstructor(IBinding binding) {
		if (binding instanceof IMethodBinding methodBinding && methodBinding.isConstructor()) {
			return CONSTRUCTOR.bitmask;
		}
		return 0;
	}

	/**
	 * Checks whether or not a binding represents a generic type or method.
	 *
	 * @param binding A binding.
	 * @return A bitmask with the {@link #GENERIC} bit set accordingly.
	 */
	public static int checkGeneric(IBinding binding) {
		if (binding instanceof ITypeBinding typeBinding) {
			if (typeBinding.isGenericType() || typeBinding.isParameterizedType()) {
				return GENERIC.bitmask;
			}
		}
		if (binding instanceof IMethodBinding methodBinding) {
			if (methodBinding.isGenericMethod() || methodBinding.isParameterizedMethod()) {
				return GENERIC.bitmask;
			}
			return checkGeneric(methodBinding.getDeclaringClass());
		}
		return 0;
	}

	/**
	 * Checks whether or not a binding represents a declaration.
	 *
	 * @param binding A binding.
	 * @return A bitmask with the {@link #DECLARATION} bit set accordingly.
	 */
	public static int checkDeclaration(SimpleName simpleName) {
		if (isDeclaration(simpleName)) {
			return DECLARATION.bitmask;
		}
		return 0;
	}

	/**
	* Returns whether a simple name represents a name that is being defined,
	* as opposed to one being referenced.
	*
	* @param simpleName A simple name.
	* @return {@code true} if this node declares a name, and {@code false} otherwise.
	*/
	private static boolean isDeclaration(SimpleName simpleName) {
		StructuralPropertyDescriptor d = simpleName.getLocationInParent();
		if (d == null) {
			return false;
		}
		ASTNode parent = simpleName.getParent();
		if (parent instanceof TypeDeclaration) {
			return (d == TypeDeclaration.NAME_PROPERTY);
		}
		if (parent instanceof MethodDeclaration) {
			return (d == MethodDeclaration.NAME_PROPERTY);
		}
		if (parent instanceof SingleVariableDeclaration) {
			return (d == SingleVariableDeclaration.NAME_PROPERTY);
		}
		if (parent instanceof VariableDeclarationFragment) {
			return (d == VariableDeclarationFragment.NAME_PROPERTY);
		}
		if (parent instanceof EnumDeclaration) {
			return (d == EnumDeclaration.NAME_PROPERTY);
		}
		if (parent instanceof EnumConstantDeclaration) {
			return (d == EnumConstantDeclaration.NAME_PROPERTY);
		}
		if (parent instanceof TypeParameter) {
			return (d == TypeParameter.NAME_PROPERTY);
		}
		if (parent instanceof AnnotationTypeDeclaration) {
			return (d == AnnotationTypeDeclaration.NAME_PROPERTY);
		}
		if (parent instanceof AnnotationTypeMemberDeclaration) {
			return (d == AnnotationTypeMemberDeclaration.NAME_PROPERTY);
		}
		if (parent instanceof RecordDeclaration) {
			return (d == RecordDeclaration.NAME_PROPERTY);
		}
		return false;
	}
}
