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
import java.util.Locale;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;

/**
 * Extracts locale information from ResourceBundle.getBundle() calls.
 * Handles Locale constants, new Locale() instances, and traces variable references.
 */
public class ResourceBundleLocaleExtractor {

	/**
	 * Extracts the locale from a ResourceBundle.getBundle() call.
	 * Handles cases like:
	 * - ResourceBundle.getBundle("messages", Locale.FRENCH)
	 * - ResourceBundle.getBundle("messages", new Locale("fr"))
	 * - ResourceBundle.getBundle("messages", Locale.getDefault())
	 *
	 * @param bundleExpression the expression that represents the bundle (e.g., bundle variable)
	 * @param root the AST root node
	 * @return locale string (e.g., "fr", "fr_FR") or null if not found or not applicable
	 */
	public String extractLocaleFromBundle(Expression bundleExpression, ASTNode root) {
		if (bundleExpression == null || root == null) {
			return null;
		}

		// Find the ResourceBundle.getBundle() call that created this bundle
		MethodInvocation getBundleInvocation = findGetBundleInvocation(bundleExpression, root);
		if (getBundleInvocation == null) {
			return null;
		}

		@SuppressWarnings("unchecked")
		List<Expression> arguments = getBundleInvocation.arguments();

		// getBundle() can have 1-4 arguments, locale is typically the second argument
		// getBundle(String baseName)
		// getBundle(String baseName, Locale locale)
		// getBundle(String baseName, Locale locale, ClassLoader loader)
		// getBundle(String baseName, Locale locale, ClassLoader loader, Control control)
		if (arguments.size() < 2) {
			return null; // No locale parameter
		}

		Expression localeArg = arguments.get(1);
		return extractLocaleFromExpression(localeArg, root);
	}

	/**
	 * Finds the ResourceBundle.getBundle() invocation that created the given bundle expression.
	 */
	private MethodInvocation findGetBundleInvocation(Expression bundleExpression, ASTNode root) {
		return ASTExpressionHelper.findGetBundleInvocation(bundleExpression, root);
	}

	/**
	 * Extracts locale string from an expression representing a Locale.
	 * Handles:
	 * - Locale.FRENCH -> "fr"
	 * - Locale.FRANCE -> "fr_FR"
	 * - new Locale("fr") -> "fr"
	 * - new Locale("fr", "FR") -> "fr_FR"
	 * - Locale.getDefault() -> null (can't determine at compile time)
	 */
	private String extractLocaleFromExpression(Expression localeExpression, ASTNode root) {
		if (localeExpression == null) {
			return null;
		}

		String fieldName = null;

		// Field access: Locale.FRENCH, Locale.FRANCE, etc.
		if (localeExpression instanceof org.eclipse.jdt.core.dom.FieldAccess fieldAccess) {
			IBinding binding = fieldAccess.getName().resolveBinding();
			
			// Try to get field name from binding first
			if (binding instanceof org.eclipse.jdt.core.dom.IVariableBinding vb) {
				fieldName = vb.getName();
			} else {
				// Fallback: extract field name directly from AST if binding resolution fails
				org.eclipse.jdt.core.dom.SimpleName nameNode = fieldAccess.getName();
				if (nameNode != null) {
					fieldName = nameNode.getIdentifier();
				}
			}
		}
		// Qualified name: Locale.FRENCH, Locale.FRANCE, etc. (alternative representation)
		else if (localeExpression instanceof org.eclipse.jdt.core.dom.QualifiedName qualifiedName) {
			IBinding binding = qualifiedName.resolveBinding();
			
			// Try to get field name from binding first
			if (binding instanceof org.eclipse.jdt.core.dom.IVariableBinding vb) {
				fieldName = vb.getName();
			} else {
				// Fallback: extract field name directly from AST if binding resolution fails
				org.eclipse.jdt.core.dom.SimpleName nameNode = qualifiedName.getName();
				if (nameNode != null) {
					fieldName = nameNode.getIdentifier();
				}
			}
		}

		if (fieldName != null) {
			// Map common Locale constants to locale strings
			switch (fieldName) {
				case "FRENCH": return "fr";
				case "FRANCE": return "fr_FR";
				case "ENGLISH": return "en";
				case "US": return "en_US";
				case "UK": return "en_GB";
				case "GERMAN": return "de";
				case "GERMANY": return "de_DE";
				case "ITALIAN": return "it";
				case "ITALY": return "it_IT";
				case "SPANISH": return "es";
				case "JAPANESE": return "ja";
				case "JAPAN": return "ja_JP";
				case "KOREAN": return "ko";
				case "KOREA": return "ko_KR";
				case "CHINESE": return "zh";
				case "SIMPLIFIED_CHINESE": return "zh_CN";
				case "TRADITIONAL_CHINESE": return "zh_TW";
				default:
					// Try to extract from field name if it follows a pattern
					// This is a best-effort approach
					return null;
			}
		}

		// Class instance creation: new Locale("fr") or new Locale("fr", "FR")
		if (localeExpression instanceof ClassInstanceCreation creation) {
			ITypeBinding typeBinding = creation.resolveTypeBinding();
			if (typeBinding != null && "java.util.Locale".equals(typeBinding.getQualifiedName())) {
				@SuppressWarnings("unchecked")
				List<Expression> arguments = creation.arguments();
				if (!arguments.isEmpty()) {
					// First argument is language code
					Expression langArg = arguments.get(0);
					String language = extractStringFromExpression(langArg, root);
					if (language == null) {
						return null;
					}

					// Second argument (if present) is country code
					if (arguments.size() >= 2) {
						Expression countryArg = arguments.get(1);
						String country = extractStringFromExpression(countryArg, root);
						if (country != null && !country.isEmpty()) {
							return language + "_" + country;
						}
					}

					return language;
				}
			}
		}

		// Method invocation: Locale.of("fr"), Locale.of("fr", "FR"), Locale.forLanguageTag("fr-FR"), or Locale.getDefault()
		if (localeExpression instanceof MethodInvocation invocation) {
			IMethodBinding binding = invocation.resolveMethodBinding();
			String methodName = null;
			ITypeBinding declaringClass = null;
			
			if (binding != null) {
				methodName = binding.getName();
				declaringClass = binding.getDeclaringClass();
			} else {
				// Fallback: extract method name from AST if binding resolution fails
				ASTNode nameNode = invocation.getName();
				if (nameNode instanceof org.eclipse.jdt.core.dom.SimpleName simpleName) {
					methodName = simpleName.getIdentifier();
				}
				// For static methods, check the receiver (e.g., "Locale" in "Locale.of()")
				// The receiver can be an Expression or a Name (for static method calls)
				Expression receiverExpr = invocation.getExpression();
				if (receiverExpr != null) {
					ITypeBinding receiverType = receiverExpr.resolveTypeBinding();
					if (receiverType != null && "java.util.Locale".equals(receiverType.getQualifiedName())) {
						declaringClass = receiverType;
					}
				} else {
					// For static method calls, the receiver might be a Name (e.g., "Locale")
					// Check if the method invocation's name is qualified
					Name methodNameNode = invocation.getName();
					if (methodNameNode instanceof org.eclipse.jdt.core.dom.QualifiedName qualifiedName) {
						Name qualifier = qualifiedName.getQualifier();
						ITypeBinding qualifierType = qualifier.resolveTypeBinding();
						if (qualifierType != null && "java.util.Locale".equals(qualifierType.getQualifiedName())) {
							declaringClass = qualifierType;
						}
					}
				}
			}
			
			// Only handle methods from java.util.Locale
			if (declaringClass == null || !"java.util.Locale".equals(declaringClass.getQualifiedName())) {
				return null;
			}
			
			if (methodName == null) {
				return null;
			}
			
			// Handle Locale.of() factory methods
			if ("of".equals(methodName)) {
				@SuppressWarnings("unchecked")
				List<Expression> arguments = invocation.arguments();
				if (!arguments.isEmpty()) {
					// First argument is language code
					Expression langArg = arguments.get(0);
					String language = extractStringFromExpression(langArg, root);
					if (language == null || language.isEmpty()) {
						return null;
					}

					// Second argument (if present) is country code
					// Locale.of() can have 1-3 arguments: (language), (language, country), or (language, country, variant)
					if (arguments.size() >= 2) {
						Expression countryArg = arguments.get(1);
						String country = extractStringFromExpression(countryArg, root);
						if (country != null && !country.isEmpty()) {
							// Return locale string with language and country
							return language + "_" + country;
						}
						// If country extraction failed, fall back to language only
						return language;
					}

					// Only language argument provided
					return language;
				}
				return null;
			}
			// Handle Locale.forLanguageTag() - accepts BCP 47 language tags (e.g., "fr", "fr-FR")
			else if ("forLanguageTag".equals(methodName)) {
				@SuppressWarnings("unchecked")
				List<Expression> arguments = invocation.arguments();
				if (!arguments.isEmpty()) {
					Expression tagArg = arguments.get(0);
					String languageTag = extractStringFromExpression(tagArg, root);
					if (languageTag != null && !languageTag.isEmpty()) {
						// Convert BCP 47 format (hyphens) to our internal format (underscores)
						// e.g., "fr-FR" -> "fr_FR", "fr" -> "fr"
						return languageTag.replace('-', '_');
					}
				}
				return null;
			}
			// Handle Locale.getDefault() - can't determine at compile time
			else if ("getDefault".equals(methodName)) {
				// Can't determine default locale at compile time, so assume it's the same as the current locale
				return Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry();
			}
		}

		return null;
	}

	/**
	 * Extracts a string value from an expression.
	 * Handles string literals and traces back variables.
	 */
	private String extractStringFromExpression(Expression expression, ASTNode root) {
		return ASTExpressionHelper.extractStringFromExpression(expression, root);
	}
}
