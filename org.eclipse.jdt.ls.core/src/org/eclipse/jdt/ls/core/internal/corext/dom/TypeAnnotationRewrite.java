/*******************************************************************************
 * Copyright (c) 2017 Till Brychcy and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/corext/dom/TypeAnnotationRewrite.java
 *
 * Contributors:
 *     Till Brychcy - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.dom;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Rewrite helper for type annotations.
 *
 */
public class TypeAnnotationRewrite {

	/**
	 * Removes all {@link Annotation} whose only {@link Target} is {@link ElementType#TYPE_USE} from
	 * <code>node</code>'s <code>childListProperty</code>.
	 * <p>
	 * In a combination of {@link ElementType#TYPE_USE} and {@link ElementType#TYPE_PARAMETER}
	 * the latter is ignored, because this is implied by the former and creates no ambiguity.</p>
	 *
	 * @param node ASTNode
	 * @param childListProperty child list property
	 * @param rewrite rewrite that removes the nodes
	 * @param editGroup the edit group in which to collect the corresponding text edits, or null if
	 *            ungrouped
	 */
	public static void removePureTypeAnnotations(ASTNode node, ChildListPropertyDescriptor childListProperty, ASTRewrite rewrite, TextEditGroup editGroup) {
		CompilationUnit root= (CompilationUnit) node.getRoot();
		if (!JavaModelUtil.is1d8OrHigher(root.getJavaElement().getJavaProject())) {
			return;
		}
		ListRewrite listRewrite= rewrite.getListRewrite(node, childListProperty);
		@SuppressWarnings("unchecked")
		List<? extends ASTNode> children= (List<? extends ASTNode>) node.getStructuralProperty(childListProperty);
		for (ASTNode child : children) {
			if (child instanceof Annotation) {
				Annotation annotation= (Annotation) child;
				if (isPureTypeAnnotation(annotation)) {
					listRewrite.remove(child, editGroup);
				}
			}
		}
	}

	private static boolean isPureTypeAnnotation(Annotation annotation) {
		IAnnotationBinding binding= annotation.resolveAnnotationBinding();
		if (binding == null) {
			return false;
		}
		IAnnotationBinding targetAnnotationBinding= findTargetAnnotation(binding.getAnnotationType().getAnnotations());

		if (targetAnnotationBinding == null) {
			return false;
		}
		return isTypeUseOnly(targetAnnotationBinding);
	}

	private static IAnnotationBinding findTargetAnnotation(IAnnotationBinding[] metaAnnotations) {
		for (int i= 0; i < metaAnnotations.length; i++) {
			IAnnotationBinding binding= metaAnnotations[i];
			ITypeBinding annotationType= binding.getAnnotationType();
			if (annotationType != null && annotationType.getQualifiedName().equals(Target.class.getName())) {
				return binding;
			}
		}
		return null;
	}

	private static boolean isTypeUseOnly(IAnnotationBinding binding) {
		IMemberValuePairBinding[] pairs= binding.getAllMemberValuePairs();
		boolean typeUseSeen= false;
		boolean otherSeen= false;
		for (final IMemberValuePairBinding pair : pairs) {
			if (pair.getKey() == null || pair.getKey().equals("value")) { //$NON-NLS-1$
				Object value= pair.getValue();
				if (value instanceof Object[]) {
					Object[] values= (Object[]) value;
					for (int k= 0; k < values.length; k++) {
						if (values[k] instanceof IVariableBinding) {
							String name= ((IVariableBinding) values[k]).getName();
							if (name.equals(ElementType.TYPE_USE.name())) {
								typeUseSeen= true;
							} else if (!name.equals(ElementType.TYPE_PARAMETER.name())) {
								otherSeen= true;
							}
						}
					}
				} else if (value instanceof IVariableBinding) {
					String name= ((IVariableBinding) value).getName();
					if (name.equals(ElementType.TYPE_USE.name())) {
						typeUseSeen= true;
					} else if (!name.equals(ElementType.TYPE_PARAMETER.name())) {
						otherSeen= true;
					}
				}
			}
		}
		return typeUseSeen && !otherSeen;
	}
}
