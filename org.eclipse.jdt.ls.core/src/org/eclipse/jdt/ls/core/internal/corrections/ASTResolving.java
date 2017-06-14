/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/ASTResolving.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WildcardType;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.ls.core.internal.BindingLabelProvider;
import org.eclipse.jdt.ls.core.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;



/**
 * JDT-UI-internal helper methods to find AST nodes or bindings.
 *
 * @see JDTUIHelperClasses
 */
public class ASTResolving extends org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving {
	public static String getMethodSignature(IMethodBinding binding) {
		return BindingLabelProvider.getBindingLabel(binding, BindingLabelProvider.DEFAULT_TEXTFLAGS);
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

	public static int getPossibleTypeKinds(ASTNode node, boolean is50OrHigher) {
		int kinds= internalGetPossibleTypeKinds(node);
		if (!is50OrHigher) {
			kinds &= (SimilarElementsRequestor.INTERFACES | SimilarElementsRequestor.CLASSES);
		}
		return kinds;
	}

	public static String getTypeSignature(ITypeBinding type) {
		return BindingLabelProvider.getBindingLabel(type, BindingLabelProvider.DEFAULT_TEXTFLAGS);
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
					Type type= guessTypeForReference(ast, parent);
					if (type != null) {
						return ASTNodeFactory.newArrayType(type);
					}
				}
				return null;
			case ASTNode.FIELD_ACCESS:
				if (node.equals(((FieldAccess) parent).getName())) {
					node= parent;
					parent= parent.getParent();
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
					node= parent;
					parent= parent.getParent();
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

	private static int internalGetPossibleTypeKinds(ASTNode node) {
		int kind= SimilarElementsRequestor.ALL_TYPES;

		int mask= SimilarElementsRequestor.ALL_TYPES | SimilarElementsRequestor.VOIDTYPE;

		ASTNode parent= node.getParent();
		while (parent instanceof QualifiedName) {
			if (node.getLocationInParent() == QualifiedName.QUALIFIER_PROPERTY) {
				return SimilarElementsRequestor.REF_TYPES;
			}
			node= parent;
			parent= parent.getParent();
			mask= SimilarElementsRequestor.REF_TYPES;
		}
		while (parent instanceof Type) {
			if (parent instanceof QualifiedType) {
				if (node.getLocationInParent() == QualifiedType.QUALIFIER_PROPERTY) {
					return mask & (SimilarElementsRequestor.REF_TYPES);
				}
				mask&= SimilarElementsRequestor.REF_TYPES;
			} else if (parent instanceof NameQualifiedType) {
				if (node.getLocationInParent() == NameQualifiedType.QUALIFIER_PROPERTY) {
					return mask & (SimilarElementsRequestor.REF_TYPES);
				}
				mask&= SimilarElementsRequestor.REF_TYPES;
			} else if (parent instanceof ParameterizedType) {
				if (node.getLocationInParent() == ParameterizedType.TYPE_ARGUMENTS_PROPERTY) {
					return mask & SimilarElementsRequestor.REF_TYPES_AND_VAR;
				}
				mask&= SimilarElementsRequestor.CLASSES | SimilarElementsRequestor.INTERFACES;
			} else if (parent instanceof WildcardType) {
				if (node.getLocationInParent() == WildcardType.BOUND_PROPERTY) {
					return mask & SimilarElementsRequestor.REF_TYPES_AND_VAR;
				}
			}
			node= parent;
			parent= parent.getParent();
		}

		if (parent != null) {
			switch (parent.getNodeType()) {
			case ASTNode.TYPE_DECLARATION:
				if (node.getLocationInParent() == TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY) {
					kind= SimilarElementsRequestor.INTERFACES;
				} else if (node.getLocationInParent() == TypeDeclaration.SUPERCLASS_TYPE_PROPERTY) {
					kind= SimilarElementsRequestor.CLASSES;
				}
				break;
			case ASTNode.ENUM_DECLARATION:
				kind= SimilarElementsRequestor.INTERFACES;
				break;
			case ASTNode.METHOD_DECLARATION:
				if (node.getLocationInParent() == MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY) {
					kind= SimilarElementsRequestor.CLASSES;
				} else if (node.getLocationInParent() == MethodDeclaration.RETURN_TYPE2_PROPERTY) {
					kind= SimilarElementsRequestor.ALL_TYPES | SimilarElementsRequestor.VOIDTYPE;
				}
				break;
			case ASTNode.ANNOTATION_TYPE_MEMBER_DECLARATION:
				kind= SimilarElementsRequestor.PRIMITIVETYPES | SimilarElementsRequestor.ANNOTATIONS | SimilarElementsRequestor.ENUMS;
				break;
			case ASTNode.INSTANCEOF_EXPRESSION:
				kind= SimilarElementsRequestor.REF_TYPES;
				break;
			case ASTNode.THROW_STATEMENT:
				kind= SimilarElementsRequestor.CLASSES;
				break;
			case ASTNode.CLASS_INSTANCE_CREATION:
				if (((ClassInstanceCreation) parent).getAnonymousClassDeclaration() == null) {
					kind= SimilarElementsRequestor.CLASSES;
				} else {
					kind= SimilarElementsRequestor.CLASSES | SimilarElementsRequestor.INTERFACES;
				}
				break;
			case ASTNode.SINGLE_VARIABLE_DECLARATION:
				int superParent= parent.getParent().getNodeType();
				if (superParent == ASTNode.CATCH_CLAUSE) {
					kind= SimilarElementsRequestor.CLASSES;
				} else if (superParent == ASTNode.ENHANCED_FOR_STATEMENT) {
					kind= SimilarElementsRequestor.REF_TYPES;
				}
				break;
			case ASTNode.TAG_ELEMENT:
				kind= SimilarElementsRequestor.REF_TYPES;
				break;
			case ASTNode.MARKER_ANNOTATION:
			case ASTNode.SINGLE_MEMBER_ANNOTATION:
			case ASTNode.NORMAL_ANNOTATION:
				kind= SimilarElementsRequestor.ANNOTATIONS;
				break;
			case ASTNode.TYPE_PARAMETER:
				if (((TypeParameter) parent).typeBounds().indexOf(node) > 0) {
					kind= SimilarElementsRequestor.INTERFACES;
				} else {
					kind= SimilarElementsRequestor.REF_TYPES_AND_VAR;
				}
				break;
			case ASTNode.TYPE_LITERAL:
				kind= SimilarElementsRequestor.REF_TYPES;
				break;
			default:
			}
		}
		return kind & mask;
	}
}
