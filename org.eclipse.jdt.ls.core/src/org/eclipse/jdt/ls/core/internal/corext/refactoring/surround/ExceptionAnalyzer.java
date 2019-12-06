/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.surround.ExceptionAnalyzer
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.surround;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.util.AbstractExceptionAnalyzer;
import org.eclipse.jdt.ls.core.internal.text.correction.QuickAssistProcessor;

public class ExceptionAnalyzer extends AbstractExceptionAnalyzer {

	private Selection fSelection;
	private static ASTNode fEnclosingNode;

	private static class ExceptionComparator implements Comparator<ITypeBinding> {
		@Override
		public int compare(ITypeBinding o1, ITypeBinding o2) {
			int d1 = getDepth(o1);
			int d2 = getDepth(o2);
			if (d1 < d2) {
				return 1;
			}
			if (d1 > d2) {
				return -1;
			}
			return 0;
		}

		private int getDepth(ITypeBinding binding) {
			int result = 0;
			while (binding != null) {
				binding = binding.getSuperclass();
				result++;
			}
			return result;
		}
	}

	private ExceptionAnalyzer(ASTNode enclosingNode, Selection selection) {
		Assert.isNotNull(selection);
		fEnclosingNode = enclosingNode;
		fSelection = selection;
	}

	public static ITypeBinding[] perform(ASTNode enclosingNode, Selection selection) {
		ExceptionAnalyzer analyzer = new ExceptionAnalyzer(enclosingNode, selection);
		enclosingNode.accept(analyzer);
		List<ITypeBinding> exceptions = analyzer.getCurrentExceptions();
		if (enclosingNode.getNodeType() == ASTNode.METHOD_DECLARATION) {
			List<Type> thrownExceptions = ((MethodDeclaration) enclosingNode).thrownExceptionTypes();
			for (Iterator<Type> thrown = thrownExceptions.iterator(); thrown.hasNext();) {
				ITypeBinding thrownException = thrown.next().resolveBinding();
				if (thrownException != null) {
					updateExceptionsList(exceptions, thrownException);
				}
			}
		} else {
			ITypeBinding typeBinding = null;
			if (enclosingNode.getLocationInParent() == LambdaExpression.BODY_PROPERTY) {
				typeBinding = ((LambdaExpression) enclosingNode.getParent()).resolveTypeBinding();
			} else if (enclosingNode instanceof MethodReference) {
				typeBinding = ((MethodReference) enclosingNode).resolveTypeBinding();
			}
			if (typeBinding != null) {
				IMethodBinding methodBinding = typeBinding.getFunctionalInterfaceMethod();
				if (methodBinding != null) {
					for (ITypeBinding thrownException : methodBinding.getExceptionTypes()) {
						updateExceptionsList(exceptions, thrownException);
					}
				}
			}
		}
		Collections.sort(exceptions, new ExceptionComparator());
		return exceptions.toArray(new ITypeBinding[exceptions.size()]);
	}

	private static void updateExceptionsList(List<ITypeBinding> exceptions, ITypeBinding thrownException) {
		for (Iterator<ITypeBinding> excep = exceptions.iterator(); excep.hasNext();) {
			ITypeBinding exception = excep.next();
			if (exception.isAssignmentCompatible(thrownException)) {
				excep.remove();
			}
		}
	}

	@Override
	public boolean visit(ExpressionMethodReference node) {
		return handleMethodReference(node);
	}

	@Override
	public boolean visit(TypeMethodReference node) {
		return handleMethodReference(node);
	}

	@Override
	public boolean visit(SuperMethodReference node) {
		return handleMethodReference(node);
	}

	@Override
	public boolean visit(CreationReference node) {
		return handleMethodReference(node);
	}

	@Override
	public boolean visit(ThrowStatement node) {
		ITypeBinding exception = node.getExpression().resolveTypeBinding();
		if (!isSelected(node) || exception == null || Bindings.isRuntimeException(exception)) {
			return true;
		}

		addException(exception, node.getAST());
		return true;
	}

	@Override
	public boolean visit(MethodInvocation node) {
		if (!isSelected(node)) {
			return false;
		}
		return handleExceptions(node.resolveMethodBinding(), node);
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		if (!isSelected(node)) {
			return false;
		}
		return handleExceptions(node.resolveMethodBinding(), node);
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (!isSelected(node)) {
			return false;
		}
		return handleExceptions(node.resolveConstructorBinding(), node);
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		if (!isSelected(node)) {
			return false;
		}
		return handleExceptions(node.resolveConstructorBinding(), node);
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		if (!isSelected(node)) {
			return false;
		}
		return handleExceptions(node.resolveConstructorBinding(), node);
	}

	@Override
	public boolean visit(VariableDeclarationExpression node) {
		if (!isSelected(node)) {
			return false;
		}
		return super.visit(node);
	}

	private boolean handleMethodReference(MethodReference node) {
		if (!isSelected(node)) {
			return false;
		}
		if (!fEnclosingNode.equals(node)) {
			return false;
		}
		IMethodBinding referredMethodBinding = node.resolveMethodBinding();
		if (referredMethodBinding == null) {
			return false;
		}
		IMethodBinding functionalMethod = QuickAssistProcessor.getFunctionalMethodForMethodReference(node);
		if (functionalMethod == null || functionalMethod.isGenericMethod()) { // generic lambda expressions are not allowed
			return false;
		}
		return handleExceptions(referredMethodBinding, node);
	}

	private boolean handleExceptions(IMethodBinding binding, ASTNode node) {
		if (binding == null) {
			return true;
		}
		ITypeBinding[] exceptions = binding.getExceptionTypes();
		for (int i = 0; i < exceptions.length; i++) {
			addException(exceptions[i], node.getAST());
		}
		return true;
	}

	private boolean isSelected(ASTNode node) {
		return fSelection.getVisitSelectionMode(node) == Selection.SELECTED;
	}
}
