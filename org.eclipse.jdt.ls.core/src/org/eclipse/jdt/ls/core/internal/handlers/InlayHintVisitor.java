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
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Objects;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.proposed.InlayHint;

@SuppressWarnings("unchecked")
class InlayHintVisitor extends ASTVisitor {
	private List<InlayHint> hints;
	private int startOffset;
	private int endOffset;
	private ITypeRoot typeRoot;
	private PreferenceManager preferenceManager;

	InlayHintVisitor(int startOffset, int endOffset, ITypeRoot typeRoot, PreferenceManager preferenceManager) {
		this.startOffset = startOffset;
		this.endOffset = endOffset;
		this.typeRoot = typeRoot;
		this.hints = new ArrayList<>();
		this.preferenceManager = preferenceManager;
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		if (isOutOfRange(node)) {
			return true;
		}
		resolveInlayHints(node.resolveConstructorBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (isOutOfRange(node)) {
			return true;
		}
		resolveInlayHints(node.resolveConstructorBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(MethodInvocation node) {
		if (isOutOfRange(node)) {
			return true;
		}
		resolveInlayHints(node.resolveMethodBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		if (isOutOfRange(node)) {
			return true;
		}
		resolveInlayHints(node.resolveMethodBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		if (isOutOfRange(node)) {
			return true;
		}
		resolveInlayHints(node.resolveConstructorBinding(), node.arguments());
		return true;
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		if (isOutOfRange(node)) {
			return true;
		}
		resolveInlayHints(node.resolveConstructorBinding(), node.arguments());
		return true;
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
	 * Resolve inlay hints. The results can be got by calling {@link #getInlayHints()}.
	 * @param methodBinding
	 * @param arguments
	 */
	private void resolveInlayHints(IMethodBinding methodBinding, List<Expression> arguments) {
		if (methodBinding == null) {
			return;
		}

		if (arguments.size() == 0) {
			return;
		}

		String[] parameterNames = getParameterNames(methodBinding);
		if (parameterNames == null) {
			return;
		}

		// not showing the inlay hints when arguments are incomplete,
		// this is to avoid hint flickering
		if (arguments.size() < parameterNames.length) {
			return;
		}

		try {
			for (int i = 0; i < parameterNames.length; i++) {
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
			// get the record constructor parameter names via its declared fields
			IVariableBinding[] declaredFields = methodBinding.getDeclaringClass().getDeclaredFields();
			parameterNames = Arrays.stream(declaredFields).map(field -> {
				return field.getName();
			}).toArray(String[]::new);
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
	 * </ul>
	 * </p>
	 */
	private boolean acceptArgument(Expression argument, String paramName) {
		if (InlayHintsParameterMode.LITERALS.equals(preferenceManager.getPreferences().getInlayHintsParameterMode())) {
			if (!isLiteral(argument)) {
				return false;
			}
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
		if (argument instanceof CastExpression) {
			argument = ((CastExpression) argument).getExpression();
		}

		if (!(argument instanceof SimpleName)) {
			return false;
		}

		String argName = ((SimpleName) argument).getIdentifier();
		return Objects.equal(argName, paramName);
	}
}
