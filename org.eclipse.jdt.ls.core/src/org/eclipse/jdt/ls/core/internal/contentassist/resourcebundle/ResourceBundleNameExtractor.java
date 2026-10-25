/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist.resourcebundle;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;

/**
 * Extracts bundle names from AST expressions.
 * Handles string literals, method invocations, field access, and variable references.
 */
public class ResourceBundleNameExtractor {

	/**
	 * Extracts the bundle name from the expression (e.g., ResourceBundle.getBundle("bundleName")).
	 * Also traces back variable assignments to find the bundle name.
	 */
	public String extractBundleName(Expression expression) {
		if (expression == null) {
			return null;
		}
		ASTNode root = expression.getRoot();
		return extractBundleNameFromExpression(expression, root);
	}

	/**
	 * Extracts bundle name from an expression, handling direct string literals,
	 * ResourceBundle.getBundle() calls, field access, and variable references.
	 */
	private String extractBundleNameFromExpression(Expression expression, ASTNode root) {
		if (expression == null) {
			return null;
		}

		// Direct string literal: var name = "messages";
		if (expression instanceof StringLiteral stringLiteral) {
			return stringLiteral.getLiteralValue();
		}

		// Method invocation: var bundle = ResourceBundle.getBundle("messages");
		if (expression instanceof MethodInvocation invocation) {
			IMethodBinding binding = invocation.resolveMethodBinding();
			if (binding != null && "getBundle".equals(binding.getName())) {
				@SuppressWarnings("unchecked")
				List<Expression> arguments = invocation.arguments();
				if (!arguments.isEmpty()) {
					Expression arg = arguments.get(0);
					// If argument is a string literal, return it directly
					if (arg instanceof StringLiteral stringLiteral) {
						return stringLiteral.getLiteralValue();
					}
					// If argument is a variable or field access, trace it back recursively
					org.eclipse.jdt.core.dom.IVariableBinding vb = ASTExpressionHelper.extractVariableBinding(arg);
					if (vb != null) {
						return findBundleNameFromVariableBinding(vb, root);
					}
				}
			}
		}

		// Field access or variable reference: extract the variable binding and trace it
		org.eclipse.jdt.core.dom.IVariableBinding vb = ASTExpressionHelper.extractVariableBinding(expression);
		if (vb != null) {
			return findBundleNameFromVariableBinding(vb, root);
		}

		return null;
	}

	/**
	 * Finds the bundle name by tracing back to where a variable was assigned.
	 */
	private String findBundleNameFromVariableBinding(org.eclipse.jdt.core.dom.IVariableBinding varBinding, ASTNode root) {
		if (varBinding == null || root == null) {
			return null;
		}

		// Find the variable declaration or assignment in the AST
		ASTExpressionHelper.VariableFinder finder = new ASTExpressionHelper.VariableFinder(varBinding);
		root.accept(finder);

		// First check if there's an initializer in the declaration
		if (finder.declarationFragment != null) {
			Expression initializer = finder.declarationFragment.getInitializer();
			String bundleName = extractBundleNameFromExpression(initializer, root);
			if (bundleName != null) {
				return bundleName;
			}
		}

		// If no initializer, check for assignment statements
		if (finder.assignment != null) {
			Expression rightHandSide = finder.assignment.getRightHandSide();
			String bundleName = extractBundleNameFromExpression(rightHandSide, root);
			if (bundleName != null) {
				return bundleName;
			}
		}

		return null;
	}
}
