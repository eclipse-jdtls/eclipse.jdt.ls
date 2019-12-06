/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.core.manipulation.BindingLabelProviderCore;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;

/**
 * JDT-UI-internal helper methods to find AST nodes or bindings.
 *
 * @see JDTUIHelperClasses
 */
public class ASTResolving extends org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving {
	public static String getMethodSignature(IMethodBinding binding) {
		return BindingLabelProviderCore.getBindingLabel(binding, BindingLabelProviderCore.DEFAULT_TEXTFLAGS);
	}

	public static String getMethodSignature(String name, ITypeBinding[] params, boolean isVarArgs) {
		StringBuffer buf= new StringBuffer();
		buf.append(name).append('(');
		for (int i= 0; i < params.length; i++) {
			if (i > 0) {
				buf.append(JavaElementLabels.COMMA_STRING);
			}
			if (isVarArgs && i == params.length - 1) {
				buf.append(getTypeSignature(params[i].getElementType()));
				buf.append("..."); //$NON-NLS-1$
			} else {
				buf.append(getTypeSignature(params[i]));
			}
		}
		buf.append(')');
		return BasicElementLabels.getJavaElementName(buf.toString());
	}

	public static String getTypeSignature(ITypeBinding type) {
		return BindingLabelProviderCore.getBindingLabel(type, BindingLabelProviderCore.DEFAULT_TEXTFLAGS);
	}

	public static Type guessTypeForReference(AST ast, ASTNode node) {
		ASTNode parent= node.getParent();
		while (parent != null) {
			switch (parent.getNodeType()) {
				case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
					if (((VariableDeclarationFragment) parent).getInitializer() == node) {
						return ASTNodeFactory.newType(ast, (VariableDeclaration) parent);
					}
					return null;
				case ASTNode.SINGLE_VARIABLE_DECLARATION:
					if (((VariableDeclarationFragment) parent).getInitializer() == node) {
						return ASTNodeFactory.newType(ast, (VariableDeclaration) parent);
					}
					return null;
				case ASTNode.ARRAY_ACCESS:
					if (!((ArrayAccess) parent).getIndex().equals(node)) {
						Type type = guessTypeForReference(ast, parent);
						if (type != null) {
							return ASTNodeFactory.newArrayType(type);
						}
					}
					return null;
				case ASTNode.FIELD_ACCESS:
					if (node.equals(((FieldAccess) parent).getName())) {
						node = parent;
						parent = parent.getParent();
					} else {
						return null;
					}
					break;
				case ASTNode.SUPER_FIELD_ACCESS:
				case ASTNode.PARENTHESIZED_EXPRESSION:
					node= parent;
					parent= parent.getParent();
					break;
				case ASTNode.QUALIFIED_NAME:
					if (node.equals(((QualifiedName) parent).getName())) {
						node = parent;
						parent = parent.getParent();
					} else {
						return null;
					}
					break;
				default:
					return null;
			}
		}
		return null;
	}
}
