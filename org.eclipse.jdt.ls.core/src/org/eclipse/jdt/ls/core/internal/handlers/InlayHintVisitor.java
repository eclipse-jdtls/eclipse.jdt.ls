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
	private InlayHintsParameterMode inlayHintsParameterMode;

	InlayHintVisitor(int startOffset, int endOffset, ITypeRoot typeRoot, PreferenceManager preferenceManager) {
		this.startOffset = startOffset;
		this.endOffset = endOffset;
		this.typeRoot = typeRoot;
		this.hints = new ArrayList<>();
		this.isVariableTypeHintsEnabled = preferenceManager.getPreferences().isInlayHintsVariableTypesEnabled();
		this.isParameterTypeHintsEnabled = preferenceManager.getPreferences().isInlayHintsParameterTypesEnabled();
		this.inlayHintsParameterMode = preferenceManager.getPreferences().getInlayHintsParameterMode();
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

}