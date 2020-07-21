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

import java.util.Arrays;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public enum TokenModifier {
	STATIC("static") {
		@Override
		protected boolean applies(IBinding binding) {
			return Modifier.isStatic(binding.getModifiers());
		}
	},
	FINAL("readonly") {
		@Override
		protected boolean applies(IBinding binding) {
			return Modifier.isFinal(binding.getModifiers());
		}
	},
	DEPRECATED("deprecated") {
		@Override
		protected boolean applies(IBinding binding) {
			return binding.isDeprecated();
		}
	},
	PUBLIC("public") {
		@Override
		protected boolean applies(IBinding binding) {
			return Modifier.isPublic(binding.getModifiers());
		}
	},
	PRIVATE("private") {
		@Override
		protected boolean applies(IBinding binding) {
			return Modifier.isPrivate(binding.getModifiers());
		}
	},
	PROTECTED("protected") {
		@Override
		protected boolean applies(IBinding binding) {
			return Modifier.isProtected(binding.getModifiers());
		}
	},
	ABSTRACT("abstract") {
		@Override
		protected boolean applies(IBinding binding) {
			return Modifier.isAbstract(binding.getModifiers());
		}
	},
	DECLARATION("declaration") {
		@Override
		protected boolean applies(SimpleName simpleName) {
			return isDeclaration(simpleName);
		}
	};

	/**
	 * This is the name of the token modifier given to the client, so it
	 * should be as generic as possible and follow the "standard" (see below)
	 * token modifier names where applicable. For example, the generic name of
	 * the final modifier should be "readonly", since it has essentially the same
	 * meaning. This makes life easier for theme authors, since
	 * they don't need to think about Java-specific terminology.
	 *
	 * @see https://code.visualstudio.com/api/language-extensions/semantic-highlight-guide#semantic-token-classification
	 */
	private String genericName;

	TokenModifier(String genericName) {
		this.genericName = genericName;
	}

	@Override
	public String toString() {
		return genericName;
	}

	/**
	* Returns an array of all the semantic token modifiers that apply to a binding.
	* Used when the desired binding can't be found by calling
	* {@link SimpleName#resolveBinding()}, as is the case
	* for the simple name of a constructor invocation.
	*
	* @param binding A binding.
	* @return An array of all the applicable modifiers for the binding.
	*
	* @apiNote The declaration modifier never applies to a binding, use
	* {@link #getApplicableModifiers(SimpleName)} to check for declarations.
	*/
	public static TokenModifier[] getApplicableModifiers(IBinding binding) {
		if (binding == null) return new TokenModifier[0];

		return Arrays.stream(TokenModifier.values())
			.filter(tm -> tm.applies(binding))
			.toArray(size -> new TokenModifier[size]);
	}

	/**
	* Returns an array of all the semantic token modifiers that apply to a simple name.
	*
	* @param simpleName A simple name.
	* @return An array of all the applicable modifiers for the simple name.
	*/
	public static TokenModifier[] getApplicableModifiers(SimpleName simpleName) {
		if (simpleName == null) return new TokenModifier[0];

		return Arrays.stream(TokenModifier.values())
			.filter(tm -> tm.applies(simpleName))
			.toArray(size -> new TokenModifier[size]);
	}

	protected boolean applies(IBinding binding) {
		return false;
	}

	protected boolean applies(SimpleName simpleName) {
		return applies(simpleName.resolveBinding());
	}

	/**
	* Returns whether a simple name represents a name that is being defined,
	* as opposed to one being referenced. This method behaves exactly like
	* {@link SimpleName#isDeclaration()} except that it also returns true
	* for constructor declarations.
	*
	* @param simpleName A simple name.
	* @return {@code true} if this node declares a name, and {@code false} otherwise.
	*
	* @see SimpleName#isDeclaration()
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
		return false;
	}
}
