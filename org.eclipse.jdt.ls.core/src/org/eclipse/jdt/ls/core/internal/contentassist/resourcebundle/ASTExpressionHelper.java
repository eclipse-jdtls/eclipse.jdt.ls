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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Shared utilities for working with AST expressions.
 * Provides common functionality for tracing variable bindings and extracting values.
 */
public class ASTExpressionHelper {

	/**
	 * AST visitor to find a variable declaration fragment or assignment for a given variable binding.
	 */
	public static class VariableFinder extends ASTVisitor {
		private final org.eclipse.jdt.core.dom.IVariableBinding targetBinding;
		public VariableDeclarationFragment declarationFragment;
		public Assignment assignment;

		public VariableFinder(org.eclipse.jdt.core.dom.IVariableBinding targetBinding) {
			this.targetBinding = targetBinding;
		}

		/**
		 * Checks if two bindings represent the same variable/field.
		 * Uses == first (fastest), then falls back to getKey() comparison.
		 */
		private boolean isSameBinding(IBinding binding1, IBinding binding2) {
			if (binding1 == binding2) {
				return true;
			}
			if (binding1 == null || binding2 == null) {
				return false;
			}
			// Fallback: compare by binding key
			try {
				return binding1.getKey().equals(binding2.getKey());
			} catch (Exception e) {
				return false;
			}
		}

		@Override
		public boolean visit(VariableDeclarationFragment node) {
			IBinding nodeBinding = node.resolveBinding();
			if (isSameBinding(nodeBinding, targetBinding)) {
				declarationFragment = node;
				// Continue visiting to also check for assignments (in case initializer is null)
			}
			return true;
		}

		@Override
		public boolean visit(Assignment node) {
			Expression leftHandSide = node.getLeftHandSide();
			IBinding lhsBinding = null;
			
			if (leftHandSide instanceof org.eclipse.jdt.core.dom.SimpleName) {
				org.eclipse.jdt.core.dom.SimpleName name = (org.eclipse.jdt.core.dom.SimpleName) leftHandSide;
				lhsBinding = name.resolveBinding();
			} else if (leftHandSide instanceof org.eclipse.jdt.core.dom.FieldAccess) {
				org.eclipse.jdt.core.dom.FieldAccess fieldAccess = (org.eclipse.jdt.core.dom.FieldAccess) leftHandSide;
				lhsBinding = fieldAccess.getName().resolveBinding();
			}
			
			if (isSameBinding(lhsBinding, targetBinding)) {
				assignment = node;
				return false; // Stop visiting once we find the assignment
			}
			
			return true;
		}
	}

	/**
	 * Checks if a MethodInvocation is ResourceBundle.getBundle().
	 */
	private static boolean isResourceBundleGetBundle(MethodInvocation invocation) {
		if (invocation == null) {
			return false;
		}
		IMethodBinding binding = invocation.resolveMethodBinding();
		if (binding == null || !"getBundle".equals(binding.getName())) {
			return false;
		}
		// Check if it's ResourceBundle.getBundle() by checking the declaring class
		org.eclipse.jdt.core.dom.ITypeBinding declaringClass = binding.getDeclaringClass();
		return declaringClass != null && "java.util.ResourceBundle".equals(declaringClass.getQualifiedName());
	}

	/**
	 * Finds a ResourceBundle.getBundle() invocation by tracing back through variable/field bindings.
	 * Handles variables, field access, and direct method invocations.
	 *
	 * @param bundleExpression the expression representing the bundle
	 * @param root the AST root node
	 * @return the getBundle() method invocation, or null if not found
	 */
	public static MethodInvocation findGetBundleInvocation(Expression bundleExpression, ASTNode root) {
		if (bundleExpression == null || root == null) {
			return null;
		}

		// If bundleExpression is already a getBundle() call, return it
		if (bundleExpression instanceof MethodInvocation invocation) {
			if (isResourceBundleGetBundle(invocation)) {
				return invocation;
			}
		}

		// If bundleExpression is a variable or field access, find where it was assigned
		org.eclipse.jdt.core.dom.IVariableBinding vb = extractVariableBinding(bundleExpression);
		if (vb != null) {
			VariableFinder finder = new VariableFinder(vb);
			root.accept(finder);

			// Check initializer
			if (finder.declarationFragment != null) {
				Expression initializer = finder.declarationFragment.getInitializer();
				if (initializer != null) {
					// If initializer is directly a getBundle() call, return it
					if (initializer instanceof MethodInvocation inv) {
						if (isResourceBundleGetBundle(inv)) {
							return inv;
						}
					}
					// Otherwise, recursively search (but avoid infinite recursion by checking if it's a variable)
					org.eclipse.jdt.core.dom.IVariableBinding initVb = extractVariableBinding(initializer);
					if (initVb != null && initVb != vb) {
						// It's a different variable, trace it recursively
						MethodInvocation recursiveResult = findGetBundleInvocation(initializer, root);
						if (recursiveResult != null) {
							return recursiveResult;
						}
					}
				}
			}

			// Check assignment
			if (finder.assignment != null) {
				Expression rhs = finder.assignment.getRightHandSide();
				if (rhs != null) {
					// If RHS is directly a getBundle() call, return it
					if (rhs instanceof MethodInvocation inv) {
						if (isResourceBundleGetBundle(inv)) {
							return inv;
						}
					}
					// Otherwise, recursively search (but avoid infinite recursion by checking if it's a variable)
					org.eclipse.jdt.core.dom.IVariableBinding rhsVb = extractVariableBinding(rhs);
					if (rhsVb != null && rhsVb != vb) {
						// It's a different variable, trace it recursively
						MethodInvocation recursiveResult = findGetBundleInvocation(rhs, root);
						if (recursiveResult != null) {
							return recursiveResult;
						}
					}
				}
			}
		}

		return null;
	}

	/**
	 * Extracts a variable binding from an expression.
	 * Handles both SimpleName (variables) and FieldAccess (fields).
	 *
	 * @param expression the expression
	 * @return the variable binding, or null if not a variable/field
	 */
	public static org.eclipse.jdt.core.dom.IVariableBinding extractVariableBinding(Expression expression) {
		if (expression instanceof org.eclipse.jdt.core.dom.SimpleName name) {
			IBinding binding = name.resolveBinding();
			if (binding instanceof org.eclipse.jdt.core.dom.IVariableBinding vb) {
				return vb;
			}
		} else if (expression instanceof org.eclipse.jdt.core.dom.FieldAccess fieldAccess) {
			IBinding binding = fieldAccess.getName().resolveBinding();
			if (binding instanceof org.eclipse.jdt.core.dom.IVariableBinding vb) {
				return vb;
			}
		}
		return null;
	}

	/**
	 * Extracts a string value from an expression.
	 * Handles string literals and traces back variables.
	 *
	 * @param expression the expression
	 * @param root the AST root node
	 * @return the string value, or null if not found
	 */
	public static String extractStringFromExpression(Expression expression, ASTNode root) {
		if (expression == null) {
			return null;
		}

		// Direct string literal
		if (expression instanceof StringLiteral stringLiteral) {
			return stringLiteral.getLiteralValue();
		}

		// Variable reference: trace back
		org.eclipse.jdt.core.dom.IVariableBinding vb = extractVariableBinding(expression);
		if (vb != null) {
			VariableFinder finder = new VariableFinder(vb);
			root.accept(finder);

			if (finder.declarationFragment != null) {
				Expression initializer = finder.declarationFragment.getInitializer();
				return extractStringFromExpression(initializer, root);
			}

			if (finder.assignment != null) {
				Expression rhs = finder.assignment.getRightHandSide();
				return extractStringFromExpression(rhs, root);
			}
		}

		return null;
	}
}
