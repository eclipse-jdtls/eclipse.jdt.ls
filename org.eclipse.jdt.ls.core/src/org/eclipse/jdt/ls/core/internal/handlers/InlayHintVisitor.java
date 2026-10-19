/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

@SuppressWarnings("unchecked")
class InlayHintVisitor extends ASTVisitor {
	private List<InlayHint> hints;
	private int startOffset;
	private int endOffset;
	private ITypeRoot typeRoot;
	private boolean isVariableTypeHintsEnabled;
	private boolean isParameterTypeHintsEnabled;
	private boolean isFormatParameterHintsEnabled;
	private InlayHintsParameterMode inlayHintsParameterMode;
	private boolean inlayHintsSuppressedWhenSameNameNumberedParameter;

	/**
	 * Regex matching Java format specifiers per {@link java.util.Formatter} syntax.
	 * Captures:
	 * <ul>
	 *   <li>Group 1: argument index (e.g. "2$"), may be null</li>
	 *   <li>Group 2: conversion character</li>
	 * </ul>
	 */
	private static final Pattern FORMAT_SPECIFIER_PATTERN = Pattern.compile(
			"%(\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([bBhHsScCdoxXeEfgGaAtTnN%])");

	InlayHintVisitor(int startOffset, int endOffset, ITypeRoot typeRoot, PreferenceManager preferenceManager) {
		this.startOffset = startOffset;
		this.endOffset = endOffset;
		this.typeRoot = typeRoot;
		this.hints = new ArrayList<>();
		this.isVariableTypeHintsEnabled = preferenceManager.getPreferences().isInlayHintsVariableTypesEnabled();
		this.isParameterTypeHintsEnabled = preferenceManager.getPreferences().isInlayHintsParameterTypesEnabled();
		this.isFormatParameterHintsEnabled = preferenceManager.getPreferences().isInlayHintsFormatParametersEnabled();
		this.inlayHintsParameterMode = preferenceManager.getPreferences().getInlayHintsParameterMode();
		this.inlayHintsSuppressedWhenSameNameNumberedParameter = preferenceManager.getPreferences().isInlayHintsSuppressedWhenSameNameNumberedParameter();
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		if (isOutOfRange(node) || isGenerated(node)) {
			return true;
		}
		resolveParameterInlayHints(node.resolveConstructorBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (isOutOfRange(node) || isGenerated(node)) {
			return true;
		}
		resolveParameterInlayHints(node.resolveConstructorBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(MethodInvocation node) {
		if (isOutOfRange(node) || isGenerated(node)) {
			return true;
		}
		resolveParameterInlayHints(node.resolveMethodBinding(), node.arguments());
		if (isFormatParameterHintsEnabled) {
			resolveFormatParameterInlayHints(node);
		}
		return true;
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		if (isOutOfRange(node) || isGenerated(node)) {
			return true;
		}
		resolveParameterInlayHints(node.resolveMethodBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		if (isOutOfRange(node) || isGenerated(node)) {
			return true;
		}
		resolveParameterInlayHints(node.resolveConstructorBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		if (isOutOfRange(node) || isGenerated(node)) {
			return true;
		}
		resolveParameterInlayHints(node.resolveConstructorBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(LambdaExpression node) {
		if (isOutOfRange(node) || isGenerated(node) || !isParameterTypeHintsEnabled) {
			return true;
		}

		// Get the method binding for the lambda to understand parameter types
		IMethodBinding methodBinding = node.resolveMethodBinding();
		if (methodBinding == null) {
			return true;
		}

		// Get parameter types from the functional interface
		ITypeBinding[] parameterTypes = methodBinding.getParameterTypes();
		List<?> parameters = node.parameters();

		if (parameters.size() != parameterTypes.length) {
			return true;
		}

		try {
			for (int i = 0; i < parameters.size(); i++) {
				Object param = parameters.get(i);
				if (param instanceof SingleVariableDeclaration) {
					// Explicit type lambda parameter (e.g., (Integer n) -> n * 2)
					// Skip showing hint since type is already visible.
					// And since mixing implicit and explicit parameter types is forbidden,
					// there's no need to keep processing parameters any further.
					return true;
				}

				SimpleName paramName = null;

				if (param instanceof SimpleName) {
					// Implicit type lambda parameter (e.g., n -> n * 2)
					paramName = (SimpleName) param;
				} else if (param instanceof VariableDeclaration) {
					// Other variable declarations
					paramName = ((VariableDeclaration) param).getName();
				}

				if (paramName != null) {
					String typeName = parameterTypes[i].getName();
					int[] lineAndColumn = JsonRpcHelpers.toLine(typeRoot.getBuffer(), paramName.getStartPosition());
					InlayHint hint = new InlayHint(new Position(lineAndColumn[0], lineAndColumn[1]), Either.forLeft(typeName));
					hint.setPaddingRight(true);
					hints.add(hint);
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}

		return true;
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		if (!isVariableTypeHintsEnabled ||
			isOutOfRange(node) ||
			isGenerated(node) ||
			!node.getType().isVar()) {
			return true;
		}
		List<VariableDeclarationFragment> fragments = node.fragments();
		for (VariableDeclarationFragment fragment : fragments) {
			IVariableBinding binding = fragment.resolveBinding();
			if (binding == null) {
				continue;
			}
			Expression initializer = fragment.getInitializer();
			// Skip hints for direct assignment of primitives, String, new object, array, or any cast
			if (isUninterestingExpression(initializer)) {
				continue;
			}
			String inferredType = binding.getType() != null ? binding.getType().getName() : null;
			if (inferredType == null || inferredType.isEmpty()) {
				continue;
			}
			// Place the hint after the variable name
			var varName = fragment.getName();
			int nameStart = varName.getStartPosition();
			int nameLength = varName.getLength();
			try {
				int[] lineAndColumn = JsonRpcHelpers.toLine(typeRoot.getBuffer(), nameStart + nameLength);
				String label = ": " + inferredType;
				InlayHint hint = new InlayHint(new Position(lineAndColumn[0], lineAndColumn[1]), Either.forLeft(label));
				hint.setPaddingLeft(true);
				hints.add(hint);
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		return true;
	}


	private boolean isUninterestingExpression(Expression initializer) {
		return initializer == null ||
				initializer instanceof NumberLiteral ||
				initializer instanceof BooleanLiteral ||
				initializer instanceof CharacterLiteral ||
				initializer instanceof StringLiteral ||
				initializer instanceof ClassInstanceCreation ||
				initializer instanceof ArrayCreation ||
				initializer instanceof ArrayInitializer ||
				initializer instanceof CastExpression;
	}
	/**
	 * Return the inlay hints after this visitor visits the AST. An empty list
	 * will be returned when there is no inlay hints to show or the results is
	 * unavailable.
	 */
	public List<InlayHint> getInlayHints() {
		return this.hints;
	}

	/**
	 * Check if the node is out of the viewport's range
	 */
	private boolean isOutOfRange(ASTNode node) {
		if (node.getStartPosition() > endOffset || node.getStartPosition() + node.getLength() < startOffset) {
			return true;
		}
		return false;
	}

	/**
	 * Check if the given ASTNode is generated by a generator like lombok. Since
	 * generated nodes are not present in source code we use this method to filter
	 * out any nodes that are generated. This check is inspired from
	 * https://github.com/projectlombok/lombok/blob/731bb185077918af8bc1e6a9e6bb538b2d3fbbd8/src/eclipseAgent/lombok/launch/PatchFixesHider.java#L398
	 */
	private boolean isGenerated(ASTNode node) {
		try {
			return (Boolean) node.getClass().getField("$isGenerated").get(node);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			return false;
		}
	}

	/**
	 * Classes whose {@code format} and {@code printf} methods use
	 * {@link java.util.Formatter} syntax.
	 */
	private static final Set<String> FORMAT_CLASSES = Set.of(
			"java.lang.String",
			"java.io.PrintStream",
			"java.io.PrintWriter",
			"java.util.Formatter",
			"java.io.Console");

	/**
	 * Resolve format parameter inlay hints for methods that accept a
	 * {@link java.util.Formatter}-style format string, such as
	 * {@code String.format()}, {@code String.formatted()},
	 * {@code PrintStream.printf()}, {@code Formatter.format()}, etc.
	 * For each format specifier that consumes an argument, an inlay hint
	 * is placed after the specifier showing the corresponding argument
	 * expression text.
	 */
	private void resolveFormatParameterInlayHints(MethodInvocation node) {
		IMethodBinding binding = node.resolveMethodBinding();
		if (binding == null) {
			return;
		}

		ITypeBinding declaringClass = binding.getDeclaringClass();
		if (declaringClass == null || !FORMAT_CLASSES.contains(declaringClass.getQualifiedName())) {
			return;
		}

		String methodName = binding.getName();
		String formatString = null;
		List<Expression> formatArgs;
		List<Expression> allArgs = node.arguments();

		if ("formatted".equals(methodName) && "java.lang.String".equals(declaringClass.getQualifiedName())) {
			// "pattern".formatted(args...) — instance method on String
			Expression expr = node.getExpression();
			formatString = extractStringValue(expr);
			if (formatString == null) {
				return;
			}
			formatArgs = allArgs;
		} else if ("format".equals(methodName) || "printf".equals(methodName)) {
			// format(pattern, args...) or format(locale, pattern, args...)
			// printf(pattern, args...) or printf(locale, pattern, args...)
			if (allArgs.isEmpty()) {
				return;
			}
			ITypeBinding[] paramTypes = binding.getParameterTypes();
			if (paramTypes.length < 1) {
				return;
			}
			int patternIndex;
			if ("java.util.Locale".equals(paramTypes[0].getQualifiedName())) {
				patternIndex = 1;
			} else {
				patternIndex = 0;
			}
			if (allArgs.size() <= patternIndex) {
				return;
			}
			formatString = extractStringValue(allArgs.get(patternIndex));
			if (formatString == null) {
				return;
			}
			formatArgs = allArgs.subList(patternIndex + 1, allArgs.size());
		} else {
			return;
		}

		// Parse format specifiers and create inlay hints
		createFormatSpecifierHints(node, formatString, formatArgs);
	}

	/**
	 * Extract the string value from a StringLiteral or TextBlock expression.
	 */
	private String extractStringValue(Expression expr) {
		if (expr instanceof StringLiteral stringLiteral) {
			return stringLiteral.getLiteralValue();
		} else if (expr instanceof TextBlock textBlock) {
			return textBlock.getLiteralValue();
		}
		return null;
	}

	/**
	 * Create inlay hints for format specifiers within a format string.
	 * Each specifier that consumes an argument gets a hint showing the
	 * corresponding argument expression.
	 */
	private void createFormatSpecifierHints(MethodInvocation node, String formatString, List<Expression> formatArgs) {
		if (formatArgs.isEmpty()) {
			return;
		}

		// Find the format string expression to compute positions
		Expression formatExpr;
		IMethodBinding binding = node.resolveMethodBinding();
		String methodName = binding.getName();
		if ("formatted".equals(methodName)) {
			formatExpr = node.getExpression();
		} else {
			ITypeBinding[] paramTypes = binding.getParameterTypes();
			int patternIndex = "java.util.Locale".equals(paramTypes[0].getQualifiedName()) ? 1 : 0;
			formatExpr = (Expression) node.arguments().get(patternIndex);
		}

		// The source offset of the format string content (after the opening quote)
		int sourceStart = formatExpr.getStartPosition();
		// For StringLiteral, content starts after the opening '"'
		// For TextBlock, content starts after the opening '"""\n'
		boolean isTextBlock = formatExpr instanceof TextBlock;

		Matcher matcher = FORMAT_SPECIFIER_PATTERN.matcher(formatString);
		int implicitArgIndex = 0; // tracks the next argument for specifiers without explicit index
		int specifierIndexInOrder = 0; // 0-based index of argument-consuming specifiers (for text block source lookup)

		try {
			while (matcher.find()) {
				String conversion = matcher.group(2);
				// Skip %% (literal percent) and %n (newline) - these don't consume arguments
				if ("%".equals(conversion) || "n".equals(conversion) || "N".equals(conversion)) {
					continue;
				}

				String argIndexStr = matcher.group(1);
				int argIndex;
				if (argIndexStr != null) {
					// Explicit argument index like %2$s — 1-based
					argIndex = Integer.parseInt(argIndexStr.substring(0, argIndexStr.length() - 1)) - 1;
				} else {
					argIndex = implicitArgIndex++;
				}

				if (argIndex < 0 || argIndex >= formatArgs.size()) {
					continue;
				}

				Expression arg = formatArgs.get(argIndex);
				String argText = arg.toString();

				// Compute the position after the specifier in the source
				int sourcePosition = computeSourcePosition(formatExpr, sourceStart, formatString, matcher.end(), specifierIndexInOrder, isTextBlock);
				specifierIndexInOrder++;

				int[] lineAndColumn = JsonRpcHelpers.toLine(typeRoot.getBuffer(), sourcePosition);
				InlayHint hint = new InlayHint(new Position(lineAndColumn[0], lineAndColumn[1]), Either.forLeft(":" + argText));
				hint.setPaddingLeft(false);
				hints.add(hint);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	/**
	 * Compute the source position for a given offset within the format string's
	 * literal value. Handles escape sequences in StringLiterals and the offset
	 * of TextBlocks.
	 *
	 * @param specifierIndexInOrder 0-based index of this argument-consuming specifier (used for text blocks, where literal value may differ from source due to indentation stripping)
	 */
	private int computeSourcePosition(Expression formatExpr, int sourceStart, String formatString, int formatOffset, int specifierIndexInOrder, boolean isTextBlock) {
		if (isTextBlock) {
			// For text blocks, getLiteralValue() strips indentation so literal offsets don't match source.
			// Find the n-th argument-consuming specifier in the raw source content instead.
			String source = formatExpr.toString();
			int contentStart = source.indexOf('\n') + 1;
			String sourceContent = source.substring(contentStart);
			int endInContent = findNthArgumentConsumingSpecifierEnd(sourceContent, specifierIndexInOrder);
			return sourceStart + contentStart + endInContent;
		} else {
			// For string literals, content starts after the opening '"'
			String source = formatExpr.toString();
			// Skip the opening quote
			return sourceStart + mapFormatOffsetToSource(source.substring(1), formatString, formatOffset) + 1;
		}
	}

	/**
	 * Find the end offset (exclusive) of the n-th argument-consuming format specifier in the given source content.
	 * Used for text blocks where we cannot rely on literal/source offset mapping.
	 */
	private int findNthArgumentConsumingSpecifierEnd(String sourceContent, int n) {
		Matcher m = FORMAT_SPECIFIER_PATTERN.matcher(sourceContent);
		int count = 0;
		while (m.find()) {
			String conversion = m.group(2);
			if ("%".equals(conversion) || "n".equals(conversion) || "N".equals(conversion)) {
				continue;
			}
			if (count == n) {
				return m.end();
			}
			count++;
		}
		return 0;
	}

	/**
	 * Map an offset in the literal value (unescaped) to an offset in the source
	 * (escaped) string. This accounts for escape sequences like \n, \t, \\, etc.
	 */
	private int mapFormatOffsetToSource(String source, String literalValue, int targetOffset) {
		int sourceIdx = 0;
		int literalIdx = 0;
		while (literalIdx < targetOffset && sourceIdx < source.length()) {
			char c = source.charAt(sourceIdx);
			if (c == '\\' && sourceIdx + 1 < source.length()) {
				char next = source.charAt(sourceIdx + 1);
				if (next == 'u') {
					// Unicode escape: uXXXX
					sourceIdx += 6;
				} else if (next >= '0' && next <= '7') {
					// Octal escape: \0 to \377
					sourceIdx += 2;
					if (sourceIdx < source.length() && source.charAt(sourceIdx) >= '0' && source.charAt(sourceIdx) <= '7') {
						sourceIdx++;
						if (next <= '3' && sourceIdx < source.length() && source.charAt(sourceIdx) >= '0' && source.charAt(sourceIdx) <= '7') {
							sourceIdx++;
						}
					}
				} else {
					// Simple escape: \n, \t, \\, \", etc.
					sourceIdx += 2;
				}
			} else {
				sourceIdx++;
			}
			literalIdx++;
		}
		return sourceIdx;
	}

	/**
	 * Resolve parameter inlay hints. The results can be got by calling
	 * {@link #getInlayHints()}.
	 *
	 * @param methodBinding
	 * @param arguments
	 */
	private void resolveParameterInlayHints(IMethodBinding methodBinding, List<Expression> arguments) {
		if (methodBinding == null || inlayHintsParameterMode == InlayHintsParameterMode.NONE) {
			return;
		}

		if (arguments.isEmpty()) {
			return;
		}

		String[] parameterNames = getParameterNames(methodBinding);
		if (parameterNames == null) {
			return;
		}

		// not showing the inlay hints when arguments are incomplete,
		// this is to avoid hint flickering
		if (!methodBinding.isVarargs() && arguments.size() != parameterNames.length) {
			return;
		}

		if (InlayHintFilterManager.instance().match((IMethod) methodBinding.getJavaElement())) {
			return;
		}

		// Filter out methods with parameters that follow  prefix + incremented number pattern
		if (inlayHintsSuppressedWhenSameNameNumberedParameter && hasNumberedParameterPattern(parameterNames)) {
			return;
		}

		try {
			int paramNum = Math.min(parameterNames.length, arguments.size());
			for (int i = 0; i < paramNum; i++) {
				Expression arg = arguments.get(i);
				if (!acceptArgument(arg, parameterNames[i])) {
					continue;
				}

				String label = parameterNames[i] + ":";
				if (i == parameterNames.length - 1 && methodBinding.isVarargs()) {
					label = "..." + label;
				}

				int[] lineAndColumn = JsonRpcHelpers.toLine(typeRoot.getBuffer(), arg.getStartPosition());
				InlayHint hint = new InlayHint(new Position(lineAndColumn[0], lineAndColumn[1]), Either.forLeft(label));
				hint.setPaddingRight(true);
				hints.add(hint);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	/**
	 * Get parameter names from the method binding.
	 * @param methodBinding
	 * @return the parameter names, or <code>null</code> if it's not available.
	 */
	private String[] getParameterNames(IMethodBinding methodBinding) {
		if (!hasSource(methodBinding)) {
			return null;
		}
		String[] parameterNames = null;
		IMethod method = (IMethod) methodBinding.getJavaElement();
		if (method != null) {
			try {
				parameterNames = method.getParameterNames();
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e);
			}
		} else if (methodBinding.getDeclaringClass().isRecord()) {
			parameterNames = methodBinding.getParameterNames();
		}
		return parameterNames;
	}

	/**
	 * Check if the given method has attached source.
	 */
	private boolean hasSource(IMethodBinding methodBinding) {
		IType type = (IType) methodBinding.getDeclaringClass().getJavaElement();
		if (type == null) {
			return false;
		}

		ITypeRoot typeRoot = type.getTypeRoot();
		if (typeRoot == null || !typeRoot.exists()) {
			return false;
		}

		try {
			return typeRoot.getBuffer() != null;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return false;
	}

	/**
	 * Check if the argument needs to show an inlay hint for it. The following cases will not
	 * have inlay hints:
	 * <p>
	 * <ul>
	 *   <li>It's in literal mode but the given argument is not a literal</li>
	 *   <li>Argument name and parameter names are same</li>
	 *   <li>Argument is a lambda expression (lambda parameters provide their own type hints)</li>
	 * </ul>
	 * </p>
	 */
	private boolean acceptArgument(Expression argument, String paramName) {
		if (InlayHintsParameterMode.LITERALS.equals(inlayHintsParameterMode)) {
			if (!isLiteral(argument)) {
				return false;
			}
		}

		// Skip parameter hints for lambda expressions since they have their own type hints
		if (argument instanceof LambdaExpression) {
			return false;
		}

		return !isSameName(argument, paramName);
	}

	/**
	 * Check whether the argument is literal type.
	 */
	private boolean isLiteral(Expression argument) {
		if (argument instanceof BooleanLiteral || argument instanceof CharacterLiteral ||
				argument instanceof NullLiteral || argument instanceof NumberLiteral ||
				argument instanceof StringLiteral || argument instanceof TypeLiteral) {
			return true;
		}

		return false;
	}

	/**
	 * Check whether the argument name is the same as the parameter name.
	 * Note: if the argument is a cast expression, only the expression
	 * to be casted will be compared.
	 */
	private boolean isSameName(Expression argument, String paramName) {
		if (argument instanceof CastExpression castExpression) {
			argument = castExpression.getExpression();
		}

		if (!(argument instanceof SimpleName)) {
			return false;
		}

		String argName = ((SimpleName) argument).getIdentifier();
		return Objects.equals(argName, paramName);
	}

	/**
	 * Pattern to validate first parameter and extract prefix (e.g., s1 -> "s", param_1 -> "param_").
	 * Group 1: prefix (any non-digit characters, no numbers allowed)
	 */
	private static final Pattern FIRST_PARAM_PATTERN = Pattern.compile("^([^\\d]+)1$");

	/**
	 * Check if the method has parameters that follow the pattern of same prefix + consecutive numbers
	 * starting from 1 (e.g., s1, s2, s3 or param1, param2, param3). Methods with such parameter
	 * patterns typically don't benefit from inlay hints as the parameter names are not descriptive.
	 *
	 * @param parameterNames the parameter names to check
	 * @return true if all parameters follow the same prefix+consecutive number pattern starting from 1
	 */
	private boolean hasNumberedParameterPattern(String[] parameterNames) {
		if (parameterNames == null || parameterNames.length < 2 || parameterNames[0] == null) {
			return false;
		}

		String firstParam = parameterNames[0];

		Matcher matcher = FIRST_PARAM_PATTERN.matcher(firstParam);
		if (!matcher.matches()) {
			return false;
		}

		String prefix = matcher.group(1);

		// Check remaining parameters using simple string concatenation
		for (int i = 1; i < parameterNames.length; i++) {
			String expectedParam = prefix + (i + 1);
			if (!expectedParam.equals(parameterNames[i])) {
				return false;
			}
		}

		return true;
	}

}