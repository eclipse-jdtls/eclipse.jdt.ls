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

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Detects if we're in a ResourceBundle context (ResourceBundle.getString() calls).
 */
public class ResourceBundleContextDetector {

	private static final String RESOURCE_BUNDLE_CLASS = "java.util.ResourceBundle";
	private static final String GET_STRING_METHOD = "getString";

	/**
	 * Result of detecting resource bundle context.
	 */
	public static class ResourceBundleContext {
		public final String bundleName;
		public final MethodInvocation invocation;
		public final String locale; // Locale string (e.g., "fr", "fr_FR") extracted from getBundle() call, or null

		public ResourceBundleContext(String bundleName, MethodInvocation invocation, String locale) {
			this.bundleName = bundleName;
			this.invocation = invocation;
			this.locale = locale;
		}
	}

	/**
	 * Bundle information extracted from method invocation.
	 */
	private static class BundleInfo {
		final String bundleName;
		final String locale;

		BundleInfo(String bundleName, String locale) {
			this.bundleName = bundleName;
			this.locale = locale;
		}
	}

	private final ResourceBundleNameExtractor nameExtractor;
	private final ResourceBundleLocaleExtractor localeExtractor;

	public ResourceBundleContextDetector() {
		this.nameExtractor = new ResourceBundleNameExtractor();
		this.localeExtractor = new ResourceBundleLocaleExtractor();
	}

	/**
	 * Detects if we're in a resource bundle context and returns both bundle name and method invocation.
	 * @return ResourceBundleContext with bundle name and invocation, or null if not in context
	 */
	public ResourceBundleContext detectContext(ICompilationUnit cu, int offset, IProgressMonitor monitor) {
		try {
			CompilationUnit ast = SharedASTProviderCore.getAST(cu, SharedASTProviderCore.WAIT_ACTIVE_ONLY, null);
			if (ast == null) {
				return null;
			}

			// Try to find a node at the offset, expanding the search if needed
			ASTNode node = NodeFinder.perform(ast, offset, 0);
			if (node == null) {
				// Try with a small range around the offset
				node = NodeFinder.perform(ast, Math.max(0, offset - 1), 2);
			}

			// If the node itself is a StringLiteral, check its parent
			if (node instanceof StringLiteral) {
				StringLiteral stringLiteral = (StringLiteral) node;
				ASTNode parent = node.getParent();
				if (parent instanceof MethodInvocation invocation) {
					BundleInfo bundleInfo = checkMethodInvocation(invocation, stringLiteral, offset);
					return bundleInfo != null ? new ResourceBundleContext(bundleInfo.bundleName, invocation, bundleInfo.locale) : null;
				}
			}

			// Find the enclosing method invocation
			MethodInvocation enclosingInvocation = findEnclosingMethodInvocation(node);
			if (enclosingInvocation == null) {
				return null;
			}

			// Check if any of the arguments is a StringLiteral containing the offset
			@SuppressWarnings("unchecked")
			java.util.List<Expression> arguments = enclosingInvocation.arguments();
			for (Expression arg : arguments) {
				if (arg instanceof StringLiteral stringLiteral) {
					if (isInsideStringLiteral(offset, stringLiteral)) {
						BundleInfo bundleInfo = checkMethodInvocation(enclosingInvocation, stringLiteral, offset);
						return bundleInfo != null ? new ResourceBundleContext(bundleInfo.bundleName, enclosingInvocation, bundleInfo.locale) : null;
					}
				}
			}

			// Check if we're at a position where a string literal argument is expected but not yet created
			// This handles cases like bundle.getString(|) where the quotes haven't been typed yet
			// Only check if there are no arguments yet, or if we're at the first argument position
			if (arguments.isEmpty() || isAtFirstArgumentPosition(enclosingInvocation, offset, arguments)) {
				BundleInfo bundleInfo = checkMethodInvocation(enclosingInvocation, null, offset);
				return bundleInfo != null ? new ResourceBundleContext(bundleInfo.bundleName, enclosingInvocation, bundleInfo.locale) : null;
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error detecting resource bundle context", e);
		}

		return null;
	}

	/**
	 * Checks if the offset is at the first argument position where a string argument would be expected.
	 * This handles the case when the cursor is at bundle.getString(|) before quotes are typed.
	 * For getString() which only takes one parameter, we should not provide completion after the first argument.
	 * Uses AST node positions and source code to verify we're actually in the argument list.
	 */
	private boolean isAtFirstArgumentPosition(MethodInvocation invocation, int offset, java.util.List<Expression> arguments) {
		try {
			ASTNode nameNode = invocation.getName();
			if (nameNode == null) {
				return false;
			}
			int nameEnd = nameNode.getStartPosition() + nameNode.getLength();

			// Check if offset is after the method name
			if (offset < nameEnd) {
				return false;
			}

			// Get source code to find parentheses and check for commas
			ASTNode root = invocation.getRoot();
			if (!(root instanceof CompilationUnit rootCU)) {
				return false;
			}
			ICompilationUnit cu = (ICompilationUnit) rootCU.getJavaElement();
			if (cu == null) {
				return false;
			}
			String source = cu.getSource();
			if (source == null) {
				return false;
			}

			int invocationStart = invocation.getStartPosition();
			int invocationEnd = invocationStart + invocation.getLength();

			// Find opening and closing parentheses
			ResourceBundleTextProcessor.ParenthesisPositions parens = ResourceBundleTextProcessor.findParenthesisPositions(source, nameEnd, invocationEnd);
			if (parens == null) {
				return false;
			}
			int openParenPos = parens.openParenPos();
			int closeParenPos = parens.closeParenPos();

			// Verify offset is within the parentheses
			if (offset <= openParenPos || offset >= closeParenPos) {
				return false;
			}

			// If there are no arguments, we're at the first argument position
			if (arguments.isEmpty()) {
				return true;
			}

			// If there's already an argument, check if we're still within it (not after a comma)
			Expression firstArg = arguments.get(0);
			int firstArgStart = firstArg.getStartPosition();
			int firstArgEnd = firstArgStart + firstArg.getLength();

			// Check if offset is within the first argument
			if (offset >= firstArgStart && offset <= firstArgEnd) {
				return true;
			}

			// Check if offset is after the first argument but before any comma
			// This handles: bundle.getString("key"|) where cursor is right after the string
			if (offset > firstArgEnd && offset < closeParenPos) {
				// Check if there's a comma between the argument end and the offset
				for (int i = firstArgEnd; i < offset && i < source.length(); i++) {
					char c = source.charAt(i);
					if (c == ',') {
						// Found a comma, we're past the first argument position
						return false;
					}
					if (!Character.isWhitespace(c)) {
						// Found non-whitespace character (might be closing paren or other)
						break;
					}
				}
				// No comma found, we're still at the first argument position
				return true;
			}

			return false;
		} catch (Exception e) {
			// If we can't determine, fall back to false
			return false;
		}
	}

	/**
	 * Checks if a method invocation is a resource bundle method and returns the bundle name and locale.
	 * @param invocation the method invocation
	 * @param stringLiteral the string literal argument (may be null if not yet created)
	 * @param offset the completion offset
	 * @return BundleInfo with bundle name and locale if this is a resource bundle method, null otherwise
	 */
	private BundleInfo checkMethodInvocation(MethodInvocation invocation, StringLiteral stringLiteral, int offset) {
		IMethodBinding methodBinding = invocation.resolveMethodBinding();
		String methodName = null;

		if (methodBinding != null) {
			methodName = methodBinding.getName();
		}

		// Fallback: if binding doesn't resolve, try to get method name from AST
		if (methodName == null) {
			ASTNode astNameNode = invocation.getName();
			if (astNameNode instanceof org.eclipse.jdt.core.dom.SimpleName nameNode) {
				methodName = nameNode.getIdentifier();
			}
		}

		// Check if it's ResourceBundle.getString() or a subclass of ResourceBundle
		if (GET_STRING_METHOD.equals(methodName) && isResourceBundleSubclass(methodBinding)) {
			// Check if we're inside a string literal, or if stringLiteral is null (not yet created)
			if (stringLiteral == null || isInsideStringLiteral(offset, stringLiteral)) {
				// Try to find the bundle name and locale from the receiver
				Expression receiver = invocation.getExpression();
				String bundleName = nameExtractor.extractBundleName(receiver);
				if (bundleName != null) {
					String locale = localeExtractor.extractLocaleFromBundle(receiver, invocation.getRoot());
					return new BundleInfo(bundleName, locale);
				}
			}
		}

		return null;
	}

	/**
	 * Checks if the given type is ResourceBundle or a subclass of ResourceBundle.
	 * Uses efficient type binding checks.
	 */
	private boolean isResourceBundleSubclass(IMethodBinding methodBinding) {
		if (methodBinding == null) {
			return false;
		}
		ITypeBinding typeBinding = methodBinding.getDeclaringClass();
		if (typeBinding == null) {
			return false;
		}

		// Use erasure to handle generic types properly
		typeBinding = typeBinding.getErasure();
		if (typeBinding == null) {
			return false;
		}

		// Check if it's ResourceBundle itself
		if (RESOURCE_BUNDLE_CLASS.equals(typeBinding.getQualifiedName())) {
			return true;
		}

		// Walk up the superclass hierarchy to find ResourceBundle
		ITypeBinding current = typeBinding.getSuperclass();
		while (current != null) {
			current = current.getErasure();
			if (current == null) {
				break;
			}
			if (RESOURCE_BUNDLE_CLASS.equals(current.getQualifiedName())) {
				return true;
			}
			current = current.getSuperclass();
		}

		return false;
	}

	/**
	 * Finds the enclosing method invocation node.
	 */
	private MethodInvocation findEnclosingMethodInvocation(ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current instanceof MethodInvocation) {
				return (MethodInvocation) current;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Checks if the offset is inside the given string literal.
	 */
	private boolean isInsideStringLiteral(int offset, StringLiteral stringLiteral) {
		int start = stringLiteral.getStartPosition();
		int end = start + stringLiteral.getLength();
		// The offset should be inside the string content (excluding quotes)
		// We allow the offset to be at the end (after the last quote) for completion
		return offset >= start + 1 && offset <= end;
	}
}
