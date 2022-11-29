/*******************************************************************************
 * Copyright (c) 2007, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.sef.SelfEncapsulateFieldRefactoring;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.ltk.core.refactoring.Change;

public class GetterSetterCorrectionSubProcessor {

	private static class ProposalParameter {
		public final boolean useSuper;
		public final ICompilationUnit compilationUnit;
		public final ASTRewrite astRewrite;
		public final Expression accessNode;
		public final Expression qualifier;
		public final IVariableBinding variableBinding;

		public ProposalParameter(boolean useSuper, ICompilationUnit compilationUnit, ASTRewrite rewrite, Expression accessNode, Expression qualifier, IVariableBinding variableBinding) {
			this.useSuper = useSuper;
			this.compilationUnit = compilationUnit;
			this.astRewrite = rewrite;
			this.accessNode = accessNode;
			this.qualifier = qualifier;
			this.variableBinding = variableBinding;
		}
	}

	public static class SelfEncapsulateFieldProposal extends ChangeCorrectionProposal { // public for tests

		public SelfEncapsulateFieldProposal(int relevance, IField field) {
			super(getDescription(field), CodeActionKind.Refactor, getRefactoringChange(field), relevance);
		}

		public static Change getRefactoringChange(IField field) {
			SelfEncapsulateFieldRefactoring refactoring;
			try {
				Preferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
				refactoring = new SelfEncapsulateFieldRefactoring(field);

				refactoring.setGenerateJavadoc(preferences.isCodeGenerationTemplateGenerateComments());
				refactoring.setVisibility(Flags.AccPublic);
				refactoring.setConsiderVisibility(false);//private field references are just searched in local file
				refactoring.checkInitialConditions(new NullProgressMonitor());
				refactoring.checkFinalConditions(new NullProgressMonitor());
				return refactoring.createChange(new NullProgressMonitor());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
			return null;
		}

		private static String getDescription(IField field) {
			return Messages.format(CorrectionMessages.GetterSetterCorrectionSubProcessor_creategetterunsingencapsulatefield_description, BasicElementLabels.getJavaElementName(field.getElementName()));
		}

		/*
		 * @see org.eclipse.jface.text.contentassist.ICompletionProposalExtension5#getAdditionalProposalInfo(org.eclipse.core.runtime.IProgressMonitor)
		 * @since 3.5
		 */
		@Override
		public String getAdditionalProposalInfo(IProgressMonitor monitor) {
			return CorrectionMessages.GetterSetterCorrectionSubProcessor_additional_info;
		}
	}

	/**
	 * Used by quick assist
	 *
	 * @param context
	 *            the invocation context
	 * @param coveringNode
	 *            the covering node
	 * @param locations
	 *            the problems at the corrent location
	 * @param resultingCollections
	 *            the resulting proposals
	 * @return <code>true</code> if the quick assist is applicable at this offset
	 */
	public static boolean addGetterSetterProposal(IInvocationContext context, ASTNode coveringNode, IProblemLocationCore[] locations, ArrayList<ChangeCorrectionProposal> resultingCollections) {
		if (locations != null) {
			for (int i = 0; i < locations.length; i++) {
				int problemId = locations[i].getProblemId();
				if (problemId == IProblem.UnusedPrivateField) {
					return false;
				}
				if (problemId == IProblem.UnqualifiedFieldAccess) {
					return false;
				}
			}
		}
		return addGetterSetterProposal(context, coveringNode, resultingCollections, IProposalRelevance.GETTER_SETTER_QUICK_ASSIST);
	}

	public static void addGetterSetterProposal(IInvocationContext context, IProblemLocationCore location, Collection<ChangeCorrectionProposal> proposals, int relevance) {
		addGetterSetterProposal(context, location.getCoveringNode(context.getASTRoot()), proposals, relevance);
	}

	private static boolean addGetterSetterProposal(IInvocationContext context, ASTNode coveringNode, Collection<ChangeCorrectionProposal> proposals, int relevance) {
		if (!(coveringNode instanceof SimpleName)) {
			return false;
		}
		SimpleName sn = (SimpleName) coveringNode;

		IBinding binding = sn.resolveBinding();
		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		IVariableBinding variableBinding = (IVariableBinding) binding;
		if (!variableBinding.isField()) {
			return false;
		}

		if (proposals == null) {
			return true;
		}

		ChangeCorrectionProposal proposal = getProposal(context.getCompilationUnit(), sn, variableBinding, relevance);
		if (proposal != null) {
			proposals.add(proposal);
		}
		return true;
	}

	private static ChangeCorrectionProposal getProposal(ICompilationUnit cu, SimpleName sn, IVariableBinding variableBinding, int relevance) {
		Expression accessNode = sn;
		Expression qualifier = null;
		boolean useSuper = false;

		ASTNode parent = sn.getParent();
		switch (parent.getNodeType()) {
			case ASTNode.QUALIFIED_NAME:
				accessNode = (Expression) parent;
				qualifier = ((QualifiedName) parent).getQualifier();
				break;
			case ASTNode.SUPER_FIELD_ACCESS:
				accessNode = (Expression) parent;
				qualifier = ((SuperFieldAccess) parent).getQualifier();
				useSuper = true;
				break;
		}
		ASTRewrite rewrite = ASTRewrite.create(sn.getAST());
		ProposalParameter gspc = new ProposalParameter(useSuper, cu, rewrite, accessNode, qualifier, variableBinding);
		if (ASTResolving.isWriteAccess(sn)) {
			return addSetterProposal(gspc, relevance);
		} else {
			return addGetterProposal(gspc, relevance);
		}
	}

	/**
	 * Proposes a getter for this field.
	 *
	 * @param context
	 *            the proposal parameter
	 * @param relevance
	 *            relevance of this proposal
	 * @return the proposal if available or null
	 */
	private static ChangeCorrectionProposal addGetterProposal(ProposalParameter context, int relevance) {
		IMethodBinding method = findGetter(context);
		if (method != null) {
			Expression mi = createMethodInvocation(context, method, null);
			context.astRewrite.replace(context.accessNode, mi, null);

			String label = Messages.format(CorrectionMessages.GetterSetterCorrectionSubProcessor_replacewithgetter_description, BasicElementLabels.getJavaCodeString(ASTNodes.asString(context.accessNode)));
			ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.compilationUnit, context.astRewrite, relevance);
			return proposal;
		} else {
			IJavaElement element = context.variableBinding.getJavaElement();
			if (element instanceof IField field) {
				try {
					if (RefactoringAvailabilityTester.isSelfEncapsulateAvailable(field)) {
						return new SelfEncapsulateFieldProposal(relevance, field);
					}
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.log(e);
				}
			}
		}
		return null;
	}

	private static IMethodBinding findGetter(ProposalParameter context) {
		ITypeBinding returnType = context.variableBinding.getType();
		String getterName = GetterSetterUtil.getGetterName(context.variableBinding, context.compilationUnit.getJavaProject(), null, isBoolean(context));
		ITypeBinding declaringType = context.variableBinding.getDeclaringClass();
		if (declaringType == null) {
			return null;
		}
		IMethodBinding getter = Bindings.findMethodInHierarchy(declaringType, getterName, new ITypeBinding[0]);
		if (getter != null && getter.getReturnType().isAssignmentCompatible(returnType) && Modifier.isStatic(getter.getModifiers()) == Modifier.isStatic(context.variableBinding.getModifiers())) {
			return getter;
		}
		return null;
	}

	private static Expression createMethodInvocation(ProposalParameter context, IMethodBinding method, Expression argument) {
		AST ast = context.astRewrite.getAST();
		Expression qualifier = context.qualifier;
		if (context.useSuper) {
			SuperMethodInvocation invocation = ast.newSuperMethodInvocation();
			invocation.setName(ast.newSimpleName(method.getName()));
			if (qualifier != null) {
				invocation.setQualifier((Name) context.astRewrite.createCopyTarget(qualifier));
			}
			if (argument != null) {
				invocation.arguments().add(argument);
			}
			return invocation;
		} else {
			MethodInvocation invocation = ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName(method.getName()));
			if (qualifier != null) {
				invocation.setExpression((Expression) context.astRewrite.createCopyTarget(qualifier));
			}
			if (argument != null) {
				invocation.arguments().add(argument);
			}
			return invocation;
		}
	}

	/**
	 * Proposes a setter for this field.
	 *
	 * @param context
	 *            the proposal parameter
	 * @param relevance
	 *            relevance of this proposal
	 * @return the proposal if available or null
	 */
	private static ChangeCorrectionProposal addSetterProposal(ProposalParameter context, int relevance) {
		boolean isBoolean = isBoolean(context);
		String setterName = GetterSetterUtil.getSetterName(context.variableBinding, context.compilationUnit.getJavaProject(), null, isBoolean);
		ITypeBinding declaringType = context.variableBinding.getDeclaringClass();
		if (declaringType == null) {
			return null;
		}

		IMethodBinding method = Bindings.findMethodInHierarchy(declaringType, setterName, new ITypeBinding[] { context.variableBinding.getType() });
		if (method != null && Bindings.isVoidType(method.getReturnType()) && (Modifier.isStatic(method.getModifiers()) == Modifier.isStatic(context.variableBinding.getModifiers()))) {
			Expression assignedValue = getAssignedValue(context);
			if (assignedValue == null) {
				return null; //we don't know how to handle those cases.
			}
			Expression mi = createMethodInvocation(context, method, assignedValue);
			context.astRewrite.replace(context.accessNode.getParent(), mi, null);

			String label = Messages.format(CorrectionMessages.GetterSetterCorrectionSubProcessor_replacewithsetter_description, BasicElementLabels.getJavaCodeString(ASTNodes.asString(context.accessNode)));
			ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.compilationUnit, context.astRewrite, relevance);
			return proposal;
		} else {
			IJavaElement element = context.variableBinding.getJavaElement();
			if (element instanceof IField field) {
				try {
					if (RefactoringAvailabilityTester.isSelfEncapsulateAvailable(field)) {
						return new SelfEncapsulateFieldProposal(relevance, field);
					}
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.log(e);
				}
			}
		}
		return null;
	}

	private static boolean isBoolean(ProposalParameter context) {
		AST ast = context.astRewrite.getAST();
		boolean isBoolean = ast.resolveWellKnownType("boolean") == context.variableBinding.getType(); //$NON-NLS-1$
		if (!isBoolean) {
			isBoolean = ast.resolveWellKnownType("java.lang.Boolean") == context.variableBinding.getType(); //$NON-NLS-1$
		}
		return isBoolean;
	}

	private static Expression getAssignedValue(ProposalParameter context) {
		ASTNode parent = context.accessNode.getParent();
		ASTRewrite astRewrite = context.astRewrite;
		IJavaProject javaProject = context.compilationUnit.getJavaProject();
		IMethodBinding getter = findGetter(context);
		Expression getterExpression = null;
		if (getter != null) {
			getterExpression = astRewrite.getAST().newSimpleName("placeholder"); //$NON-NLS-1$
		}
		ITypeBinding type = context.variableBinding.getType();
		boolean is50OrHigher = JavaModelUtil.is50OrHigher(javaProject);
		Expression result = GetterSetterUtil.getAssignedValue(parent, astRewrite, getterExpression, type, is50OrHigher);
		if (result != null && getterExpression != null && getterExpression.getParent() != null) {
			getterExpression.getParent().setStructuralProperty(getterExpression.getLocationInParent(), createMethodInvocation(context, getter, null));
		}
		return result;
	}

}
