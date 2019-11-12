/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.Dimension;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.CleanUpContextCore;
import org.eclipse.jdt.core.manipulation.CleanUpOptionsCore;
import org.eclipse.jdt.core.manipulation.CleanUpRequirementsCore;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.core.manipulation.ICleanUpFixCore;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.ConvertLoopFixCore;
import org.eclipse.jdt.internal.corext.fix.ICleanUpCore;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.LambdaExpressionsFixCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTesterCore;
import org.eclipse.jdt.internal.corext.refactoring.code.ConvertAnonymousToNestedRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineConstantRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineMethodRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineTempRefactoring;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCore;
import org.eclipse.jdt.internal.ui.fix.LambdaExpressionsCleanUpCore;
import org.eclipse.jdt.internal.ui.fix.MultiFixMessages;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ASTRewriteCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.AssignToVariableAssistProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.FixCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.RefactoringCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.TypeChangeCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.link.LinkedPositionGroup;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactoringCorrectionCommandProposal;
import org.eclipse.lsp4j.CodeActionParams;

/**
 * RefactorProcessor
 */
public class RefactorProcessor {
	public static final String CONVERT_ANONYMOUS_CLASS_TO_NESTED_COMMAND = "convertAnonymousClassToNestedCommand";

	private PreferenceManager preferenceManager;

	public RefactorProcessor(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<ChangeCorrectionProposal> getProposals(CodeActionParams params, IInvocationContext context, IProblemLocationCore[] locations) throws CoreException {
		ASTNode coveringNode = context.getCoveringNode();
		if (coveringNode != null) {
			ArrayList<ChangeCorrectionProposal> proposals = new ArrayList<>();
			// TODO (Yan): Move refactor proposals here.
			InvertBooleanUtility.getInverseConditionProposals(params, context, coveringNode, proposals);
			getInverseLocalVariableProposals(params, context, coveringNode, proposals);

			getMoveRefactoringProposals(params, context, coveringNode, proposals);

			boolean noErrorsAtLocation = noErrorsAtLocation(locations);
			if (noErrorsAtLocation) {
				boolean problemsAtLocation = locations.length != 0;
				getExtractVariableProposal(params, context, problemsAtLocation, proposals);
				getExtractMethodProposal(params, context, coveringNode, problemsAtLocation, proposals);
				getExtractFieldProposal(params, context, problemsAtLocation, proposals);
				getInlineProposal(context, coveringNode, proposals);

				getConvertAnonymousToNestedProposals(params, context, coveringNode, proposals);
				getConvertAnonymousClassCreationsToLambdaProposals(context, coveringNode, proposals);
				getConvertLambdaToAnonymousClassCreationsProposals(context, coveringNode, proposals);

				getConvertVarTypeToResolvedTypeProposal(context, coveringNode, proposals);
				getConvertResolvedTypeToVarTypeProposal(context, coveringNode, proposals);
			}
			return proposals;
		}
		return Collections.emptyList();
	}

	private boolean getInverseLocalVariableProposals(CodeActionParams params, IInvocationContext context, ASTNode covering, Collection<ChangeCorrectionProposal> proposals) {
		if (proposals == null) {
			return false;
		}

		ChangeCorrectionProposal proposal = null;
		if (this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported()) {
			proposal = InvertBooleanUtility.getInvertVariableProposal(params, context, covering, true /*returnAsCommand*/);
		} else {
			proposal = InvertBooleanUtility.getInvertVariableProposal(params, context, covering, false /*returnAsCommand*/);
		}

		if (proposal == null) {
			return false;
		}

		proposals.add(proposal);
		return true;
	}

	private boolean getMoveRefactoringProposals(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, ArrayList<ChangeCorrectionProposal> resultingCollections) {
		if (resultingCollections == null) {
			return false;
		}

		if (this.preferenceManager.getClientPreferences().isMoveRefactoringSupported()) {
			List<CUCorrectionProposal> newProposals = RefactorProposalUtility.getMoveRefactoringProposals(params, context);
			if (newProposals != null && !newProposals.isEmpty()) {
				resultingCollections.addAll(newProposals);
				return true;
			}
		}

		return false;

	}

	static boolean noErrorsAtLocation(IProblemLocationCore[] locations) {
		if (locations != null) {
			for (int i = 0; i < locations.length; i++) {
				IProblemLocationCore location = locations[i];
				if (location.isError()) {
					if (IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER.equals(location.getMarkerType()) && JavaCore.getOptionForConfigurableSeverity(location.getProblemId()) != null) {
						// continue (only drop out for severe (non-optional) errors)
					} else {
						return false;
					}
				}
			}
		}
		return true;
	}


	private boolean getExtractVariableProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Collection<ChangeCorrectionProposal> proposals) throws CoreException {
		if (proposals == null) {
			return false;
		}

		List<CUCorrectionProposal> newProposals = null;
		if (this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported()) {
			newProposals = RefactorProposalUtility.getExtractVariableCommandProposals(params, context, problemsAtLocation);
		} else {
			newProposals = RefactorProposalUtility.getExtractVariableProposals(params, context, problemsAtLocation);
		}

		if (newProposals == null || newProposals.isEmpty()) {
			return false;
		}

		proposals.addAll(newProposals);
		return true;
	}

	private boolean getExtractMethodProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean problemsAtLocation, Collection<ChangeCorrectionProposal> proposals) throws CoreException {
		if (proposals == null) {
			return false;
		}

		CUCorrectionProposal proposal = null;
		if (this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported()) {
			proposal = RefactorProposalUtility.getExtractMethodCommandProposal(params, context, coveringNode, problemsAtLocation);
		} else {
			proposal = RefactorProposalUtility.getExtractMethodProposal(params, context, coveringNode, problemsAtLocation);
		}

		if (proposal == null) {
			return false;
		}

		proposals.add(proposal);
		return true;
	}

	private boolean getExtractFieldProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Collection<ChangeCorrectionProposal> proposals) throws CoreException {
		if (proposals == null) {
			return false;
		}

		CUCorrectionProposal proposal = RefactorProposalUtility.getGenericExtractFieldProposal(params, context, problemsAtLocation, null, null, this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported());

		if (proposal == null) {
			return false;
		}

		proposals.add(proposal);
		return true;
	}


	private boolean getInlineProposal(IInvocationContext context, ASTNode node, Collection<ChangeCorrectionProposal> resultingCollections) {
		if (resultingCollections == null) {
			return false;
		}

		if (!(node instanceof SimpleName)) {
			return false;
		}

		SimpleName name= (SimpleName) node;
		IBinding binding = name.resolveBinding();
		try {
			if (binding instanceof IVariableBinding) {
				IVariableBinding varBinding = (IVariableBinding) binding;
				if (varBinding.isParameter()) {
					return false;
				}

				if (varBinding.isField()) {
					// Inline Constant (static final field)
					if (RefactoringAvailabilityTesterCore.isInlineConstantAvailable((IField) varBinding.getJavaElement())) {
						InlineConstantRefactoring refactoring = new InlineConstantRefactoring(context.getCompilationUnit(), context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength());
						if (refactoring != null && refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
							refactoring.setRemoveDeclaration(refactoring.isDeclarationSelected());
							refactoring.setReplaceAllReferences(refactoring.isDeclarationSelected());
							CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.FINAL_CONDITIONS);
							final CreateChangeOperation create = new CreateChangeOperation(check, RefactoringStatus.FATAL);
							create.run(new NullProgressMonitor());
							String label = ActionMessages.InlineConstantRefactoringAction_label;
							int relevance = IProposalRelevance.INLINE_LOCAL;
							ChangeCorrectionProposal proposal = new ChangeCorrectionProposal(label, CodeActionKind.RefactorInline, create.getChange(), relevance);
							resultingCollections.add(proposal);
							return true;
						}
					}

					return false;
				}

				ASTNode decl= context.getASTRoot().findDeclaringNode(varBinding);
				if (!(decl instanceof VariableDeclarationFragment) || decl.getLocationInParent() != VariableDeclarationStatement.FRAGMENTS_PROPERTY) {
					return false;
				}

				// Inline Local Variable
				if (binding.getJavaElement() instanceof ILocalVariable && RefactoringAvailabilityTesterCore.isInlineTempAvailable((ILocalVariable) binding.getJavaElement())) {
					InlineTempRefactoring refactoring= new InlineTempRefactoring((VariableDeclaration) decl);
					if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
						String label = CorrectionMessages.QuickAssistProcessor_inline_local_description;
						int relevance = IProposalRelevance.INLINE_LOCAL;
						RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, CodeActionKind.RefactorInline, context.getCompilationUnit(), refactoring, relevance);
						resultingCollections.add(proposal);
						return true;
					}
				}
			} else if (binding instanceof IMethodBinding) {
				// Inline Method
				if (RefactoringAvailabilityTesterCore.isInlineMethodAvailable((IMethod) binding.getJavaElement())) {
					InlineMethodRefactoring refactoring = InlineMethodRefactoring.create(context.getCompilationUnit(), context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength());
					if (refactoring != null && refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
						CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.FINAL_CONDITIONS);
						final CreateChangeOperation create = new CreateChangeOperation(check, RefactoringStatus.FATAL);
						create.run(new NullProgressMonitor());
						String label = ActionMessages.InlineMethodRefactoringAction_label;
						int relevance = IProposalRelevance.INLINE_LOCAL;
						ChangeCorrectionProposal proposal = new ChangeCorrectionProposal(label, CodeActionKind.RefactorInline, create.getChange(), relevance);
						resultingCollections.add(proposal);
						return true;
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
		}

		return false;
	}


	private boolean getConvertAnonymousToNestedProposals(CodeActionParams params, IInvocationContext context, final ASTNode node, Collection<ChangeCorrectionProposal> proposals) throws CoreException {
		if (!(node instanceof Name)) {
			return false;
		}

		if (proposals == null) {
			return false;
		}

		RefactoringCorrectionProposal proposal = null;
		if (this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported()) {
			proposal = getConvertAnonymousToNestedProposal(params, context, node, true /*returnAsCommand*/);
		} else {
			proposal = getConvertAnonymousToNestedProposal(params, context, node, false /*returnAsCommand*/);
		}

		if (proposal == null) {
			return false;
		}

		proposals.add(proposal);
		return true;
	}

	public static RefactoringCorrectionProposal getConvertAnonymousToNestedProposal(CodeActionParams params, IInvocationContext context, final ASTNode node, boolean returnAsCommand) throws CoreException {
		String label = CorrectionMessages.QuickAssistProcessor_convert_anonym_to_nested;
		ASTNode normalized = ASTNodes.getNormalizedNode(node);
		if (normalized.getLocationInParent() != ClassInstanceCreation.TYPE_PROPERTY) {
			return null;
		}

		final AnonymousClassDeclaration anonymTypeDecl = ((ClassInstanceCreation) normalized.getParent()).getAnonymousClassDeclaration();
		if (anonymTypeDecl == null || anonymTypeDecl.resolveBinding() == null) {
			return null;
		}

		final ConvertAnonymousToNestedRefactoring refactoring = new ConvertAnonymousToNestedRefactoring(anonymTypeDecl);
		if (!refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			return null;
		}

		if (returnAsCommand) {
			return new RefactoringCorrectionCommandProposal(label, CodeActionKind.Refactor, context.getCompilationUnit(), IProposalRelevance.CONVERT_ANONYMOUS_TO_NESTED, RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID,
					Arrays.asList(CONVERT_ANONYMOUS_CLASS_TO_NESTED_COMMAND, params));
		}

		String extTypeName = ASTNodes.getSimpleNameIdentifier((Name) node);
		ITypeBinding anonymTypeBinding = anonymTypeDecl.resolveBinding();
		String className;
		if (anonymTypeBinding.getInterfaces().length == 0) {
			className = Messages.format(CorrectionMessages.QuickAssistProcessor_name_extension_from_interface, extTypeName);
		} else {
			className = Messages.format(CorrectionMessages.QuickAssistProcessor_name_extension_from_class, extTypeName);
		}
		String[][] existingTypes = ((IType) anonymTypeBinding.getJavaElement()).resolveType(className);
		int i = 1;
		while (existingTypes != null) {
			i++;
			existingTypes = ((IType) anonymTypeBinding.getJavaElement()).resolveType(className + i);
		}
		refactoring.setClassName(i == 1 ? className : className + i);

		LinkedProposalModelCore linkedProposalModel = new LinkedProposalModelCore();
		refactoring.setLinkedProposalModel(linkedProposalModel);

		final ICompilationUnit cu = context.getCompilationUnit();
		RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, CodeActionKind.Refactor, cu, refactoring, IProposalRelevance.CONVERT_ANONYMOUS_TO_NESTED);
		proposal.setLinkedProposalModel(linkedProposalModel);
		return proposal;

	}

	private static boolean getConvertAnonymousClassCreationsToLambdaProposals(IInvocationContext context, ASTNode covering, Collection<ChangeCorrectionProposal> resultingCollections) {
		while (covering instanceof Name || covering instanceof Type || covering instanceof Dimension || covering.getParent() instanceof MethodDeclaration
				|| covering.getLocationInParent() == AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY) {
			covering = covering.getParent();
		}

		ClassInstanceCreation cic;
		if (covering instanceof ClassInstanceCreation) {
			cic = (ClassInstanceCreation) covering;
		} else if (covering.getLocationInParent() == ClassInstanceCreation.ANONYMOUS_CLASS_DECLARATION_PROPERTY) {
			cic = (ClassInstanceCreation) covering.getParent();
		} else if (covering instanceof Name) {
			ASTNode normalized = ASTNodes.getNormalizedNode(covering);
			if (normalized.getLocationInParent() != ClassInstanceCreation.TYPE_PROPERTY) {
				return false;
			}
			cic = (ClassInstanceCreation) normalized.getParent();
		} else {
			return false;
		}

		IProposableFix fix = LambdaExpressionsFixCore.createConvertToLambdaFix(cic);
		if (fix == null) {
			return false;
		}

		if (resultingCollections == null) {
			return true;
		}

		Map<String, String> options = new HashMap<>();
		options.put(CleanUpConstants.CONVERT_FUNCTIONAL_INTERFACES, CleanUpOptionsCore.TRUE);
		options.put(CleanUpConstants.USE_LAMBDA, CleanUpOptionsCore.TRUE);
		FixCorrectionProposal proposal = new FixCorrectionProposal(fix, new LambdaExpressionsCleanUpCore(options), IProposalRelevance.CONVERT_TO_LAMBDA_EXPRESSION, context, CodeActionKind.Refactor);
		resultingCollections.add(proposal);
		return true;
	}

	private static boolean getConvertLambdaToAnonymousClassCreationsProposals(IInvocationContext context, ASTNode covering, Collection<ChangeCorrectionProposal> resultingCollections) {
		if (resultingCollections == null) {
			return true;
		}

		LambdaExpression lambda;
		if (covering instanceof LambdaExpression) {
			lambda = (LambdaExpression) covering;
		} else if (covering.getLocationInParent() == LambdaExpression.BODY_PROPERTY) {
			lambda = (LambdaExpression) covering.getParent();
		} else {
			return false;
		}

		IProposableFix fix = LambdaExpressionsFixCore.createConvertToAnonymousClassCreationsFix(lambda);
		if (fix == null) {
			return false;
		}

		// add correction proposal
		Map<String, String> options = new HashMap<>();
		options.put(CleanUpConstants.CONVERT_FUNCTIONAL_INTERFACES, CleanUpOptionsCore.TRUE);
		options.put(CleanUpConstants.USE_ANONYMOUS_CLASS_CREATION, CleanUpOptionsCore.TRUE);
		FixCorrectionProposal proposal = new FixCorrectionProposal(fix, new LambdaExpressionsCleanUpCore(options), IProposalRelevance.CONVERT_TO_ANONYMOUS_CLASS_CREATION, context, CodeActionKind.Refactor);
		resultingCollections.add(proposal);
		return true;
	}

	private static boolean getConvertVarTypeToResolvedTypeProposal(IInvocationContext context, ASTNode node, Collection<ChangeCorrectionProposal> proposals) {
		CompilationUnit astRoot = context.getASTRoot();
		IJavaElement root = astRoot.getJavaElement();
		if (root == null) {
			return false;
		}
		IJavaProject javaProject = root.getJavaProject();
		if (javaProject == null) {
			return false;
		}
		if (!JavaModelUtil.is10OrHigher(javaProject)) {
			return false;
		}

		if (!(node instanceof SimpleName)) {
			return false;
		}
		SimpleName name = (SimpleName) node;
		IBinding binding = name.resolveBinding();
		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		IVariableBinding varBinding = (IVariableBinding) binding;
		if (varBinding.isField() || varBinding.isParameter()) {
			return false;
		}

		ASTNode varDeclaration = astRoot.findDeclaringNode(varBinding);
		if (varDeclaration == null) {
			return false;
		}

		ITypeBinding typeBinding = varBinding.getType();
		if (typeBinding == null || typeBinding.isAnonymous() || typeBinding.isIntersectionType() || typeBinding.isWildcardType()) {
			return false;
		}

		Type type = null;
		if (varDeclaration instanceof SingleVariableDeclaration) {
			type = ((SingleVariableDeclaration) varDeclaration).getType();
		} else if (varDeclaration instanceof VariableDeclarationFragment) {
			ASTNode parent = varDeclaration.getParent();
			if (parent instanceof VariableDeclarationStatement) {
				type = ((VariableDeclarationStatement) parent).getType();
			} else if (parent instanceof VariableDeclarationExpression) {
				type = ((VariableDeclarationExpression) parent).getType();
			}
		}
		if (type == null || !type.isVar()) {
			return false;
		}

		TypeChangeCorrectionProposal proposal = new TypeChangeCorrectionProposal(context.getCompilationUnit(), varBinding, astRoot, typeBinding, false, IProposalRelevance.CHANGE_VARIABLE);
		proposal.setKind(CodeActionKind.Refactor);
		proposals.add(proposal);
		return true;
	}

	private static boolean getConvertResolvedTypeToVarTypeProposal(IInvocationContext context, ASTNode node, Collection<ChangeCorrectionProposal> proposals) {
		CompilationUnit astRoot = context.getASTRoot();
		IJavaElement root = astRoot.getJavaElement();
		if (root == null) {
			return false;
		}
		IJavaProject javaProject = root.getJavaProject();
		if (javaProject == null) {
			return false;
		}
		if (!JavaModelUtil.is10OrHigher(javaProject)) {
			return false;
		}

		if (!(node instanceof SimpleName)) {
			return false;
		}
		SimpleName name = (SimpleName) node;
		IBinding binding = name.resolveBinding();
		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		IVariableBinding varBinding = (IVariableBinding) binding;
		if (varBinding.isField() || varBinding.isParameter()) {
			return false;
		}

		ASTNode varDeclaration = astRoot.findDeclaringNode(varBinding);
		if (varDeclaration == null) {
			return false;
		}

		Type type = null;
		Expression expression = null;

		ITypeBinding typeBinding = varBinding.getType();
		if (typeBinding == null) {
			return false;
		}
		ITypeBinding expressionTypeBinding = null;

		if (varDeclaration instanceof SingleVariableDeclaration) {
			SingleVariableDeclaration svDecl = (SingleVariableDeclaration) varDeclaration;
			type = svDecl.getType();
			expression = svDecl.getInitializer();
			if (expression != null) {
				expressionTypeBinding = expression.resolveTypeBinding();
			} else {
				ASTNode parent = svDecl.getParent();
				if (parent instanceof EnhancedForStatement) {
					EnhancedForStatement efStmt = (EnhancedForStatement) parent;
					expression = efStmt.getExpression();
					if (expression != null) {
						ITypeBinding expBinding = expression.resolveTypeBinding();
						if (expBinding != null) {
							if (expBinding.isArray()) {
								expressionTypeBinding = expBinding.getElementType();
							} else {
								ITypeBinding iterable = Bindings.findTypeInHierarchy(expBinding, "java.lang.Iterable"); //$NON-NLS-1$
								if (iterable != null) {
									ITypeBinding[] typeArguments = iterable.getTypeArguments();
									if (typeArguments.length == 1) {
										expressionTypeBinding = typeArguments[0];
										expressionTypeBinding = Bindings.normalizeForDeclarationUse(expressionTypeBinding, context.getASTRoot().getAST());
									}
								}
							}
						}
					}
				}
			}
		} else if (varDeclaration instanceof VariableDeclarationFragment) {
			ASTNode parent = varDeclaration.getParent();
			expression = ((VariableDeclarationFragment) varDeclaration).getInitializer();
			if (expression != null) {
				expressionTypeBinding = expression.resolveTypeBinding();
			}
			if (parent instanceof VariableDeclarationStatement) {
				type = ((VariableDeclarationStatement) parent).getType();
			} else if (parent instanceof VariableDeclarationExpression) {
				VariableDeclarationExpression varDecl = (VariableDeclarationExpression) parent;
				// cannot convert a VariableDeclarationExpression with multiple fragments to var.
				if (varDecl.fragments().size() > 1) {
					return false;
				}
				type = varDecl.getType();
			}
		}

		if (type == null || type.isVar()) {
			return false;
		}
		if (expression == null || expression instanceof ArrayInitializer || expression instanceof LambdaExpression || expression instanceof MethodReference) {
			return false;
		}
		if (expressionTypeBinding == null || !expressionTypeBinding.isEqualTo(typeBinding)) {
			return false;
		}

		TypeChangeCorrectionProposal proposal = new TypeChangeCorrectionProposal(context.getCompilationUnit(), varBinding, astRoot, typeBinding, IProposalRelevance.CHANGE_VARIABLE);
		proposal.setKind(CodeActionKind.Refactor);
		proposals.add(proposal);
		return true;
	}

}
