/*******************************************************************************
* Copyright (c) 2019, 2023 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
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
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Dimension;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.core.manipulation.CleanUpOptionsCore;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.ConvertLoopFixCore;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.LambdaExpressionsFixCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTesterCore;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.code.ConvertAnonymousToNestedRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineConstantRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineMethodRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineTempRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.MakeStaticRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.jdt.internal.corext.refactoring.surround.SurroundWithTryCatchRefactoring;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.fix.LambdaExpressionsCleanUpCore;
import org.eclipse.jdt.internal.ui.fix.MultiFixMessages;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ASTRewriteRemoveImportsCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.RefactoringCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.TypeChangeCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactoringCorrectionCommandProposal;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * RefactorProcessor
 */
public class RefactorProcessor {
	public static final String CONVERT_ANONYMOUS_CLASS_TO_NESTED_COMMAND = "convertAnonymousClassToNestedCommand";

	private PreferenceManager preferenceManager;

	public RefactorProcessor(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<ProposalKindWrapper> getProposals(CodeActionParams params, IInvocationContext context, IProblemLocation[] locations) throws CoreException {
		return getProposals(params, context, locations, new NullProgressMonitor());
	}

	public List<ProposalKindWrapper> getProposals(CodeActionParams params, IInvocationContext context, IProblemLocation[] locations, IProgressMonitor monitor) throws CoreException {
		ASTNode coveringNode = context.getCoveringNode();
		if (monitor != null && monitor.isCanceled()) {
			return Collections.emptyList();
		}
		if (coveringNode != null) {
			ArrayList<ProposalKindWrapper> proposals = new ArrayList<>();

			InvertBooleanUtility.getInverseConditionProposals(params, context, coveringNode, proposals);
			if (monitor != null && monitor.isCanceled()) {
				return Collections.emptyList();
			}
			getInverseLocalVariableProposals(params, context, coveringNode, proposals);
			if (monitor != null && monitor.isCanceled()) {
				return Collections.emptyList();
			}
			getMoveRefactoringProposals(params, context, coveringNode, proposals);
			if (monitor != null && monitor.isCanceled()) {
				return Collections.emptyList();
			}
			boolean noErrorsAtLocation = noErrorsAtLocation(locations, coveringNode);
			if (noErrorsAtLocation) {
				boolean problemsAtLocation = locations.length != 0;
				getExtractVariableProposal(params, context, problemsAtLocation, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getExtractMethodProposal(params, context, coveringNode, problemsAtLocation, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getExtractFieldProposal(params, context, problemsAtLocation, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getConvertAnonymousToNestedProposals(params, context, coveringNode, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getConvertAnonymousClassCreationsToLambdaProposals(context, coveringNode, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getConvertLambdaToAnonymousClassCreationsProposals(context, coveringNode, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}

				getConvertVarTypeToResolvedTypeProposal(context, coveringNode, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getConvertResolvedTypeToVarTypeProposal(context, coveringNode, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}

				getAddStaticImportProposals(context, coveringNode, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}

				getConvertForLoopProposal(context, coveringNode, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getAssignToVariableProposals(context, coveringNode, locations, proposals, params);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getExtractInterfaceProposal(params, context, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getSurroundWithTryCatchProposal(context, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getChangeSignatureProposal(params, context, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getIntroduceParameterProposals(params, context, coveringNode, locations, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getInlineProposal(context, coveringNode, proposals);
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				getMakeMethodStaticRefactoringProposal(params, context, coveringNode, proposals, monitor);
			}
			return proposals;
		}
		return Collections.emptyList();
	}


	private boolean getIntroduceParameterProposals(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, IProblemLocation[] locations, ArrayList<ProposalKindWrapper> resultingCollections) throws CoreException {
		if (resultingCollections == null) {
			return false;
		}
		ProposalKindWrapper proposal = RefactorProposalUtility.getIntroduceParameterRefactoringProposals(params, context, coveringNode, this.preferenceManager.getClientPreferences().isAdvancedIntroduceParameterRefactoringSupported(),
				locations);
		if (proposal != null) {
			return resultingCollections.add(proposal);
		}
		return false;
	}

	private boolean getInverseLocalVariableProposals(CodeActionParams params, IInvocationContext context, ASTNode covering, Collection<ProposalKindWrapper> proposals) {
		if (proposals == null) {
			return false;
		}

		ProposalKindWrapper proposal = null;
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

	private boolean getMoveRefactoringProposals(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, ArrayList<ProposalKindWrapper> resultingCollections) {
		if (resultingCollections == null) {
			return false;
		}

		if (this.preferenceManager.getClientPreferences().isMoveRefactoringSupported()) {
			List<ProposalKindWrapper> newProposals = RefactorProposalUtility.getMoveRefactoringProposals(params, context);
			if (newProposals != null && !newProposals.isEmpty()) {
				resultingCollections.addAll(newProposals);
				return true;
			}
		}

		return false;

	}

	static boolean noErrorsAtLocation(IProblemLocation[] locations, ASTNode coveringNode) {
		if (locations != null) {
			int start = coveringNode.getStartPosition();
			int length = coveringNode.getLength();
			for (int i = 0; i < locations.length; i++) {
				IProblemLocation location = locations[i];
				if (location.getOffset() > start + length || (location.getOffset() + location.getLength()) < start) {
					continue;
				}
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


	private boolean getExtractVariableProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Collection<ProposalKindWrapper> proposals) throws CoreException {
		if (proposals == null) {
			return false;
		}

		List<ProposalKindWrapper> newProposals = null;
		if (this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported()) {
			newProposals = RefactorProposalUtility.getExtractVariableCommandProposals(params, context, problemsAtLocation, this.preferenceManager.getClientPreferences().isExtractVariableInferSelectionSupported());
		} else {
			newProposals = RefactorProposalUtility.getExtractVariableProposals(params, context, problemsAtLocation, this.preferenceManager.getClientPreferences().isExtractVariableInferSelectionSupported());
		}

		if (newProposals == null || newProposals.isEmpty()) {
			return false;
		}

		proposals.addAll(newProposals);
		return true;
	}

	private boolean getAssignToVariableProposals(IInvocationContext context, ASTNode node, IProblemLocation[] locations, Collection<ProposalKindWrapper> resultingCollections, CodeActionParams params) {
		String declsToFinal = this.preferenceManager.getPreferences().getCodeGenerationAddFinalForNewDeclaration();
		try {
			Map formatterOptions = null;
			ProposalKindWrapper proposal = RefactorProposalUtility.getAssignVariableProposal(params, context, locations != null && locations.length != 0, formatterOptions,
					this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported(), locations, ("all".equals(declsToFinal) || "variables".equals(declsToFinal)));
			if (proposal != null) {
				resultingCollections.add(proposal);
			}
			proposal = RefactorProposalUtility.getAssignFieldProposal(params, context, locations != null && locations.length != 0, formatterOptions,
					this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported(), locations);
			if (proposal != null) {
				resultingCollections.add(proposal);
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e);
		}
		return true;
	}

	private boolean getExtractMethodProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean problemsAtLocation, Collection<ProposalKindWrapper> proposals) throws CoreException {
		if (proposals == null) {
			return false;
		}

		ProposalKindWrapper proposal = null;
		if (this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported()) {
			proposal = RefactorProposalUtility.getExtractMethodCommandProposal(params, context, coveringNode, problemsAtLocation, this.preferenceManager.getClientPreferences().isExtractMethodInferSelectionSupported());
		} else {
			proposal = RefactorProposalUtility.getExtractMethodProposal(params, context, coveringNode, problemsAtLocation, this.preferenceManager.getClientPreferences().isExtractMethodInferSelectionSupported());
		}

		if (proposal == null) {
			return false;
		}

		proposals.add(proposal);
		return true;
	}

	private boolean getExtractFieldProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Collection<ProposalKindWrapper> proposals) throws CoreException {
		if (proposals == null) {
			return false;
		}

		ProposalKindWrapper proposal = RefactorProposalUtility.getGenericExtractFieldProposal(params, context, problemsAtLocation, null, null, this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported(),
				this.preferenceManager.getClientPreferences().isExtractFieldInferSelectionSupported());

		if (proposal == null) {
			return false;
		}

		proposals.add(proposal);
		return true;
	}


	private boolean getInlineProposal(IInvocationContext context, ASTNode node, Collection<ProposalKindWrapper> resultingCollections) {
		if (resultingCollections == null) {
			return false;
		}

		if (!(node instanceof SimpleName)) {
			return false;
		}

		SimpleName name= (SimpleName) node;
		IBinding binding = name.resolveBinding();
		try {
			if (binding instanceof IVariableBinding varBinding) {
				if (varBinding.isParameter()) {
					return false;
				}

				if (varBinding.isField()) {
					// Inline Constant (static final field)
					if (RefactoringAvailabilityTesterCore.isInlineConstantAvailable((IField) varBinding.getJavaElement())) {
						InlineConstantRefactoring refactoring = new InlineConstantRefactoring(context.getCompilationUnit(), context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength());
						if (refactoring != null) {
							String label = ActionMessages.InlineConstantRefactoringAction_label;
							int relevance = IProposalRelevance.INLINE_LOCAL;
							ChangeCorrectionProposalCore proposal = new ChangeCorrectionProposalCore(label, null /*create.getChange()*/, relevance) {
								@Override
								public Change getChange() throws CoreException {
									if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK() && refactoring.getReferences(new NullProgressMonitor(), new RefactoringStatus()).length > 0) {
										refactoring.setRemoveDeclaration(refactoring.isDeclarationSelected());
										refactoring.setReplaceAllReferences(refactoring.isDeclarationSelected());
										CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.FINAL_CONDITIONS);
										final CreateChangeOperation create = new CreateChangeOperation(check, RefactoringStatus.FATAL);
										try {
											create.run(new NullProgressMonitor());
											return create.getChange();
										} catch (CoreException e) {
											JavaLanguageServerPlugin.log(e);
										}
									}
									return new NullChange();
								}
							};
							resultingCollections.add(CodeActionHandler.wrap(proposal, CodeActionKind.RefactorInline));
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
				// https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3321
				// I haven't enhanced it because InlineVariableTest.testInlineLocalVariableWithNoReferences() fails; can be enhanced
				if (binding.getJavaElement() instanceof ILocalVariable localVar && RefactoringAvailabilityTesterCore.isInlineTempAvailable(localVar)) {
					InlineTempRefactoring refactoring = new InlineTempRefactoring((VariableDeclaration) decl);
					boolean status;
					try {
						status = refactoring.checkAllConditions(new NullProgressMonitor()).isOK();
					} catch (Exception e) {
						// ignore
						status = false;
					}
					if (status && refactoring.getReferences().length > 0) {
						String label = CorrectionMessages.QuickAssistProcessor_inline_local_description;
						int relevance = IProposalRelevance.INLINE_LOCAL;
						RefactoringCorrectionProposalCore proposal = new RefactoringCorrectionProposalCore(label, context.getCompilationUnit(), refactoring, relevance);
						resultingCollections.add(CodeActionHandler.wrap(proposal, CodeActionKind.RefactorInline));
						return true;
					}
				}
			} else if (binding instanceof IMethodBinding) {
				boolean isUnnamedClass = List.of(context.getCompilationUnit().getTypes()).stream().anyMatch(t -> JDTUtils.isUnnamedClass(t));
				if (isUnnamedClass) {
					return false;
				}
				// Inline Method
				if (RefactoringAvailabilityTesterCore.isInlineMethodAvailable((IMethod) binding.getJavaElement())) {
					InlineMethodRefactoring refactoring = InlineMethodRefactoring.create(context.getCompilationUnit(), context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength());
					if (refactoring != null) {
						String label = ActionMessages.InlineMethodRefactoringAction_label;
						int relevance = IProposalRelevance.INLINE_LOCAL;
						ChangeCorrectionProposalCore proposal = new ChangeCorrectionProposalCore(label, null /*create.getChange()*/, relevance) {
							@Override
							public Change getChange() throws CoreException {
								if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
									CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.FINAL_CONDITIONS);
									final CreateChangeOperation create = new CreateChangeOperation(check, RefactoringStatus.FATAL);
									try {
										create.run(new NullProgressMonitor());
										return create.getChange();
									} catch (CoreException e) {
										JavaLanguageServerPlugin.log(e);
									}
								}
								return new NullChange();
							}
						};
						resultingCollections.add(CodeActionHandler.wrap(proposal, CodeActionKind.RefactorInline));
						return true;
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
		}

		return false;
	}


	private boolean getConvertAnonymousToNestedProposals(CodeActionParams params, IInvocationContext context, ASTNode node, Collection<ProposalKindWrapper> proposals) throws CoreException {
		if (proposals == null) {
			return false;
		}

		ProposalKindWrapper proposal = null;
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

	public static ProposalKindWrapper getConvertAnonymousToNestedProposal(CodeActionParams params, IInvocationContext context, final ASTNode node, boolean returnAsCommand) throws CoreException {
		String label = CorrectionMessages.QuickAssistProcessor_convert_anonym_to_nested;
		ClassInstanceCreation cic = getClassInstanceCreation(node);
		if (cic == null) {
			return null;
		}
		final AnonymousClassDeclaration anonymTypeDecl = cic.getAnonymousClassDeclaration();
		if (anonymTypeDecl == null || anonymTypeDecl.resolveBinding() == null) {
			return null;
		}

		final ConvertAnonymousToNestedRefactoring refactoring = new ConvertAnonymousToNestedRefactoring(anonymTypeDecl);
		if (!refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			return null;
		}

		if (returnAsCommand) {
			RefactoringCorrectionCommandProposal p1 = new RefactoringCorrectionCommandProposal(label, context.getCompilationUnit(), IProposalRelevance.CONVERT_ANONYMOUS_TO_NESTED, RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID,
					Arrays.asList(CONVERT_ANONYMOUS_CLASS_TO_NESTED_COMMAND, params));
			return CodeActionHandler.wrap(p1, CodeActionKind.Refactor);
		}

		String extTypeName = ASTNodes.getTypeName(cic.getType());
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
		RefactoringCorrectionProposalCore proposal = new RefactoringCorrectionProposalCore(label, cu, refactoring, IProposalRelevance.CONVERT_ANONYMOUS_TO_NESTED);
		proposal.setLinkedProposalModel(linkedProposalModel);
		return CodeActionHandler.wrap(proposal, CodeActionKind.Refactor);
	}

	private static ClassInstanceCreation getClassInstanceCreation(ASTNode node) {
		while (node instanceof Name || node instanceof Type || node instanceof Dimension || node.getParent() instanceof MethodDeclaration
				|| node.getLocationInParent() == AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY) {
			node = node.getParent();
		}

		if (node instanceof ClassInstanceCreation classInstanceCreation) {
			return classInstanceCreation;
		} else if (node.getLocationInParent() == ClassInstanceCreation.ANONYMOUS_CLASS_DECLARATION_PROPERTY) {
			return (ClassInstanceCreation) node.getParent();
		} else {
			return null;
		}
	}

	private static boolean getConvertAnonymousClassCreationsToLambdaProposals(IInvocationContext context, ASTNode covering, Collection<ProposalKindWrapper> resultingCollections) {
		ClassInstanceCreation cic = getClassInstanceCreation(covering);
		if (cic == null) {
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
		FixCorrectionProposalCore proposal = new FixCorrectionProposalCore(fix, new LambdaExpressionsCleanUpCore(options), IProposalRelevance.CONVERT_TO_LAMBDA_EXPRESSION, context);
		resultingCollections.add(CodeActionHandler.wrap(proposal, CodeActionKind.Refactor));
		return true;
	}

	private static boolean getConvertLambdaToAnonymousClassCreationsProposals(IInvocationContext context, ASTNode covering, Collection<ProposalKindWrapper> resultingCollections) {
		if (resultingCollections == null) {
			return true;
		}

		LambdaExpression lambda;
		if (covering instanceof LambdaExpression lambdaExpression) {
			lambda = lambdaExpression;
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
		FixCorrectionProposalCore proposal = new FixCorrectionProposalCore(fix, new LambdaExpressionsCleanUpCore(options), IProposalRelevance.CONVERT_TO_ANONYMOUS_CLASS_CREATION, context);
		resultingCollections.add(CodeActionHandler.wrap(proposal, CodeActionKind.Refactor));
		return true;
	}

	private static boolean getConvertVarTypeToResolvedTypeProposal(IInvocationContext context, ASTNode node, Collection<ProposalKindWrapper> proposals) {
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

		SimpleName name = getSimpleNameForVariable(node);
		if (name == null) {
			return false;
		}

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
		if (varDeclaration instanceof SingleVariableDeclaration singleVar) {
			type = singleVar.getType();
		} else if (varDeclaration instanceof VariableDeclarationFragment) {
			ASTNode parent = varDeclaration.getParent();
			if (parent instanceof VariableDeclarationStatement variableDeclStatement) {
				type = variableDeclStatement.getType();
			} else if (parent instanceof VariableDeclarationExpression variableDeclExpression) {
				type = variableDeclExpression.getType();
			}
		}
		if (type == null || !type.isVar()) {
			return false;
		}

		TypeChangeCorrectionProposalCore proposal = new TypeChangeCorrectionProposalCore(context.getCompilationUnit(), varBinding, astRoot, typeBinding, false, IProposalRelevance.CHANGE_VARIABLE);
		proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.Refactor));
		return true;
	}

	private static SimpleName getSimpleNameForVariable(ASTNode node) {
		if (!(node instanceof SimpleName)) {
			return null;
		}
		SimpleName name = (SimpleName) node;
		if (!name.isDeclaration()) {
			while (node instanceof Name || node instanceof Type) {
				node = node.getParent();
			}
			if (node instanceof VariableDeclarationStatement variableDeclStatement) {
				List<VariableDeclarationFragment> fragments = variableDeclStatement.fragments();
				if (fragments.size() > 0) {
					// var is not allowed in a compound declaration
					name = fragments.get(0).getName();
				}
			}
		}
		return name;
	}

	private static boolean getConvertResolvedTypeToVarTypeProposal(IInvocationContext context, ASTNode node, Collection<ProposalKindWrapper> proposals) {
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

		SimpleName name = getSimpleNameForVariable(node);
		if (name == null) {
			return false;
		}

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

		if (varDeclaration instanceof SingleVariableDeclaration svDecl) {
			type = svDecl.getType();
			expression = svDecl.getInitializer();
			if (expression != null) {
				expressionTypeBinding = expression.resolveTypeBinding();
			} else {
				ASTNode parent = svDecl.getParent();
				if (parent instanceof EnhancedForStatement efStmt) {
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
		} else if (varDeclaration instanceof VariableDeclarationFragment variableDeclarationFragment) {
			ASTNode parent = varDeclaration.getParent();
			expression = variableDeclarationFragment.getInitializer();
			if (expression != null) {
				expressionTypeBinding = expression.resolveTypeBinding();
			}
			if (parent instanceof VariableDeclarationStatement variableDeclarationStatement) {
				type = variableDeclarationStatement.getType();
			} else if (parent instanceof VariableDeclarationExpression varDecl) {
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

		TypeChangeCorrectionProposalCore proposal = new TypeChangeCorrectionProposalCore(context.getCompilationUnit(), varBinding, astRoot, typeBinding, IProposalRelevance.CHANGE_VARIABLE);
		proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.Refactor));
		return true;
	}


	/**
	 * Create static import proposal, which converts invocations to static import.
	 *
	 * @param context
	 *            the invocation context
	 * @param node
	 *            the node to work on
	 * @param proposals
	 *            the receiver of proposals, may be {@code null}
	 * @return {@code true} if the operation could or has been performed,
	 *         {@code false otherwise}
	 */
	private static boolean getAddStaticImportProposals(IInvocationContext context, ASTNode node, Collection<ProposalKindWrapper> proposals) {
		if (!(node instanceof SimpleName)) {
			return false;
		}

		final SimpleName name = (SimpleName) node;
		final IBinding binding;
		final ITypeBinding declaringClass;

		// get bindings for method invocation or variable access

		if (name.getParent() instanceof MethodInvocation mi) {
			Expression expression = mi.getExpression();
			if (expression == null || expression.equals(name)) {
				return false;
			}

			binding = mi.resolveMethodBinding();
			if (binding == null) {
				return false;
			}

			declaringClass = ((IMethodBinding) binding).getDeclaringClass();
		} else if (name.getParent() instanceof QualifiedName qualifiedName) {
			if (name.equals(qualifiedName.getQualifier()) || qualifiedName.getParent() instanceof ImportDeclaration) {
				return false;
			}

			binding = qualifiedName.resolveBinding();
			if (!(binding instanceof IVariableBinding)) {
				return false;
			}
			declaringClass = ((IVariableBinding) binding).getDeclaringClass();
		} else {
			return false;
		}

		// at this point binding cannot be null

		if (!Modifier.isStatic(binding.getModifiers())) {
			// only work with static bindings
			return false;
		}

		boolean needImport = false;
		if (!isDirectlyAccessible(name, declaringClass)) {
			if (Modifier.isPrivate(declaringClass.getModifiers())) {
				return false;
			}
			needImport = true;
		}

		if (proposals == null) {
			return true; // return early, just testing if we could do it
		}

		try {
			ImportRewrite importRewrite = StubUtility.createImportRewrite(context.getCompilationUnit(), true);
			ASTRewrite astRewrite = ASTRewrite.create(node.getAST());
			ASTRewrite astRewriteReplaceAllOccurrences = ASTRewrite.create(node.getAST());

			ImportRemover remover = new ImportRemover(context.getCompilationUnit().getJavaProject(), context.getASTRoot());
			ImportRemover removerAllOccurences = new ImportRemover(context.getCompilationUnit().getJavaProject(), context.getASTRoot());
			MethodInvocation mi = null;
			QualifiedName qn = null;
			if (name.getParent() instanceof MethodInvocation parentInvocation) {
				mi = parentInvocation;
				// convert the method invocation
				astRewrite.remove(mi.getExpression(), null);
				remover.registerRemovedNode(mi.getExpression());
				removerAllOccurences.registerRemovedNode(mi.getExpression());
				mi.typeArguments().forEach(typeObject -> {
					Type type = (Type) typeObject;
					astRewrite.remove(type, null);
					remover.registerRemovedNode(type);
					removerAllOccurences.registerRemovedNode(type);
				});
			} else if (name.getParent() instanceof QualifiedName qname) {
				qn = qname;
				// convert the field access
				astRewrite.replace(qn, ASTNodeFactory.newName(node.getAST(), name.getFullyQualifiedName()), null);
				remover.registerRemovedNode(qn);
				removerAllOccurences.registerRemovedNode(qn);
			} else {
				return false;
			}

			MethodInvocation miFinal = mi;
			name.getRoot().accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation methodInvocation) {
					Expression methodInvocationExpression = methodInvocation.getExpression();
					if (methodInvocationExpression == null) {
						return super.visit(methodInvocation);
					}

					if (methodInvocationExpression instanceof Name name) {
						String fullyQualifiedName = name.getFullyQualifiedName();
						if (miFinal != null && miFinal.getExpression() instanceof Name exprName && exprName.getFullyQualifiedName().equals(fullyQualifiedName)
								&& miFinal.getName().getIdentifier().equals(methodInvocation.getName().getIdentifier())) {
							methodInvocation.typeArguments().forEach(type -> {
								astRewriteReplaceAllOccurrences.remove((Type) type, null);
								removerAllOccurences.registerRemovedNode((Type) type);
							});
							astRewriteReplaceAllOccurrences.remove(methodInvocationExpression, null);
							removerAllOccurences.registerRemovedNode(methodInvocationExpression);
						}
					}

					return super.visit(methodInvocation);
				}
			});
			QualifiedName qnFinal = qn;
			name.getRoot().accept(new ASTVisitor() {
				@Override
				public boolean visit(QualifiedName qualifiedName) {
					if (qnFinal != null && qualifiedName.getFullyQualifiedName().equals(qnFinal.getFullyQualifiedName())) {
						astRewriteReplaceAllOccurrences.replace(qualifiedName, ASTNodeFactory.newName(node.getAST(), name.getFullyQualifiedName()), null);
						removerAllOccurences.registerRemovedNode(qualifiedName);
					}
					return super.visit(qualifiedName);
				}
			});

			if (needImport) {
				importRewrite.addStaticImport(binding);
			}

			ASTRewriteRemoveImportsCorrectionProposalCore proposal = new ASTRewriteRemoveImportsCorrectionProposalCore(CorrectionMessages.QuickAssistProcessor_convert_to_static_import, context.getCompilationUnit(), astRewrite,
					IProposalRelevance.ADD_STATIC_IMPORT);
			proposal.setImportRewrite(importRewrite);
			proposal.setImportRemover(remover);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.Refactor));

			ASTRewriteRemoveImportsCorrectionProposalCore proposalReplaceAllOccurrences = new ASTRewriteRemoveImportsCorrectionProposalCore(CorrectionMessages.QuickAssistProcessor_convert_to_static_import_replace_all,
					context.getCompilationUnit(), astRewriteReplaceAllOccurrences,
					IProposalRelevance.ADD_STATIC_IMPORT);
			proposalReplaceAllOccurrences.setImportRewrite(importRewrite);
			proposalReplaceAllOccurrences.setImportRemover(removerAllOccurences);
			proposals.add(CodeActionHandler.wrap(proposalReplaceAllOccurrences, CodeActionKind.Refactor));
		} catch (IllegalArgumentException e) {
			// Wrong use of ASTRewrite or ImportRewrite API, see bug 541586
			JavaLanguageServerPlugin.logException("Failed to get static import proposal", e);
			return false;
		} catch (JavaModelException e) {
			return false;
		}

		return true;
	}

	private static boolean isDirectlyAccessible(ASTNode nameNode, ITypeBinding declaringClass) {
		ASTNode node = nameNode.getParent();
		while (node != null) {

			if (node instanceof AbstractTypeDeclaration typeDecl) {
				ITypeBinding binding = typeDecl.resolveBinding();
				if (binding != null && binding.isSubTypeCompatible(declaringClass)) {
					return true;
				}
			} else if (node instanceof AnonymousClassDeclaration anonymousClassDecl) {
				ITypeBinding binding = anonymousClassDecl.resolveBinding();
				if (binding != null && binding.isSubTypeCompatible(declaringClass)) {
					return true;
				}
			}

			node = node.getParent();
		}
		return false;
	}

	private static boolean getConvertForLoopProposal(IInvocationContext context, ASTNode node, Collection<ProposalKindWrapper> resultingCollections) {
		ForStatement forStatement = getEnclosingForStatementHeader(node);
		if (forStatement == null) {
			return false;
		}
		if (resultingCollections == null) {
			return true;
		}
		IProposableFix fix = ConvertLoopFixCore.createConvertForLoopToEnhancedFix(context.getASTRoot(), forStatement);
		if (fix == null) {
			return false;
		}
		Map<String, String> options = new HashMap<>();
		options.put(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED, CleanUpOptionsCore.TRUE);
		ICleanUp cleanUp = new AbstractCleanUp(options) {
			@Override
			public CleanUpRequirements getRequirements() {
				return new CleanUpRequirements(isEnabled(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED), false, false, null);
			}

			@Override
			public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
				CompilationUnit compilationUnit = context.getAST();
				if (compilationUnit == null) {
					return null;
				}
				boolean convertForLoops = isEnabled(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
				boolean checkIfLoopVarUsed = isEnabled(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_ONLY_IF_LOOP_VAR_USED);
				return ConvertLoopFixCore.createCleanUp(compilationUnit, convertForLoops, convertForLoops,
						isEnabled(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL) && isEnabled(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES), checkIfLoopVarUsed);
			}

			@Override
			public String[] getStepDescriptions() {
				List<String> result = new ArrayList<>();
				if (isEnabled(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED)) {
					result.add(MultiFixMessages.Java50CleanUp_ConvertToEnhancedForLoop_description);
					if (isEnabled(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_ONLY_IF_LOOP_VAR_USED)) {
						result.add(MultiFixMessages.Java50CleanUp_ConvertLoopOnlyIfLoopVarUsed_description);
					}
				}
				return result.toArray(new String[result.size()]);
			}

			@Override
			public String getPreview() {
				StringBuilder buf = new StringBuilder();
				if (isEnabled(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED)) {
					buf.append("for (int element : ids) {\n"); //$NON-NLS-1$
					buf.append("    double value= element / 2; \n"); //$NON-NLS-1$
					buf.append("    System.out.println(value);\n"); //$NON-NLS-1$
					buf.append("}\n"); //$NON-NLS-1$
				} else {
					buf.append("for (int i = 0; i < ids.length; i++) {\n"); //$NON-NLS-1$
					buf.append("    double value= ids[i] / 2; \n"); //$NON-NLS-1$
					buf.append("    System.out.println(value);\n"); //$NON-NLS-1$
					buf.append("}\n"); //$NON-NLS-1$
				}
				if (isEnabled(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED) && !isEnabled(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_ONLY_IF_LOOP_VAR_USED)) {
					buf.append("for (int id : ids) {\n"); //$NON-NLS-1$
					buf.append("    System.out.println(\"here\");\n"); //$NON-NLS-1$
					buf.append("}\n"); //$NON-NLS-1$
				} else {
					buf.append("for (int i = 0; i < ids.length; i++) {\n"); //$NON-NLS-1$
					buf.append("    System.out.println(\"here\");\n"); //$NON-NLS-1$
					buf.append("}\n"); //$NON-NLS-1$
				}
				return buf.toString();
			}
		};
		FixCorrectionProposalCore proposal = new FixCorrectionProposalCore(fix, cleanUp, IProposalRelevance.CONVERT_FOR_LOOP_TO_ENHANCED, context);
		resultingCollections.add(CodeActionHandler.wrap(proposal, CodeActionKind.Refactor));
		return true;
	}

	private static ForStatement getEnclosingForStatementHeader(ASTNode node) {
		return getEnclosingHeader(node, ForStatement.class, ForStatement.INITIALIZERS_PROPERTY, ForStatement.EXPRESSION_PROPERTY, ForStatement.UPDATERS_PROPERTY);
	}

	private static <T extends ASTNode> T getEnclosingHeader(ASTNode node, Class<T> headerType, StructuralPropertyDescriptor... headerProperties) {
		if (headerType.isInstance(node)) {
			return headerType.cast(node);
		}

		while (node != null) {
			ASTNode parent = node.getParent();
			if (headerType.isInstance(parent)) {
				StructuralPropertyDescriptor locationInParent = node.getLocationInParent();
				for (StructuralPropertyDescriptor property : headerProperties) {
					if (locationInParent == property) {
						return headerType.cast(parent);
					}
				}
				return null;
			}
			node = parent;
		}
		return null;
	}

	private boolean getExtractInterfaceProposal(CodeActionParams params, IInvocationContext context, Collection<ProposalKindWrapper> proposals) {
		if (proposals == null) {
			return false;
		}

		if (!this.preferenceManager.getClientPreferences().isExtractInterfaceSupport() || !this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported()) {
			return false;
		}

		ProposalKindWrapper proposal = RefactorProposalUtility.getExtractInterfaceProposal(params, context);

		if (proposal == null) {
			return false;
		}

		proposals.add(proposal);
		return true;
	}

	private boolean getChangeSignatureProposal(CodeActionParams params, IInvocationContext context, Collection<ProposalKindWrapper> proposals) {
		if (proposals == null) {
			return false;
		}

		ProposalKindWrapper proposal = RefactorProposalUtility.getChangeSignatureProposal(params, context);

		if (proposal == null) {
			return false;
		}

		proposals.add(proposal);
		return true;
	}

	private boolean getSurroundWithTryCatchProposal(IInvocationContext context, Collection<ProposalKindWrapper> proposals) {
		if (proposals == null) {
			return false;
		}

		if(context.getSelectionLength() <= 0) {
			return false;
		}

		ICompilationUnit cu = context.getCompilationUnit();

		CompilationUnit astRoot = context.getASTRoot();
		ASTNode selectedNode = context.getCoveredNode();
		if (selectedNode == null) {
			return false;
		}

		while (selectedNode != null && !(selectedNode instanceof Statement) && !(selectedNode instanceof VariableDeclarationExpression) && !(selectedNode.getLocationInParent() == LambdaExpression.BODY_PROPERTY)
				&& !(selectedNode instanceof MethodReference)) {
			selectedNode = selectedNode.getParent();
		}
		if (selectedNode == null) {
			return false;
		}

		int offset = selectedNode.getStartPosition();
		int length = selectedNode.getLength();
		int selectionEnd = context.getSelectionOffset() + context.getSelectionLength();

		if (selectionEnd > offset + length) {
			// extend the selection if more than one statement is selected (bug 72149)
			length = selectionEnd - offset;
		}

		try {
			SurroundWithTryCatchRefactoring refactoring = SurroundWithTryCatchRefactoring.create(cu, offset, length);

			if (!refactoring.checkActivationBasics(astRoot).isOK()) {
				return false;
			}

			refactoring.setLeaveDirty(true);

			String label = CorrectionMessages.LocalCorrectionsSubProcessor_surroundwith_trycatch_description;
			RefactoringCorrectionProposalCore proposal = new RefactoringCorrectionProposalCore(label, cu, refactoring, IProposalRelevance.SURROUND_WITH_TRY_CATCH);
			proposal.setLinkedProposalModel(refactoring.getLinkedProposalModel());
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.Refactor));
			return true;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
		}

		return false;
	}

	private boolean getMakeMethodStaticRefactoringProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, ArrayList<ProposalKindWrapper> proposals, IProgressMonitor monitor) {
		MakeStaticRefactoring refactoring = new MakeStaticRefactoring(context.getCompilationUnit(), context.getSelectionOffset(), context.getSelectionLength());
		try {
			if (refactoring != null && refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
				String label = RefactoringCoreMessages.MakeStaticRefactoring_name;
				int relevance = IProposalRelevance.CHANGE_METHOD_SIGNATURE;
				ChangeCorrectionProposalCore proposal = new ChangeCorrectionProposalCore(label, null /*create.getChange()*/, relevance) {
					@Override
					public Change getChange() throws CoreException {
						CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.FINAL_CONDITIONS);
						final CreateChangeOperation create = new CreateChangeOperation(check, RefactoringStatus.FATAL);
						try {
							create.run(new NullProgressMonitor());
							return create.getChange();
						} catch (CoreException e) {
							JavaLanguageServerPlugin.log(e);
						}
						return new NullChange();
					}
				};
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.RefactorInline));
			}
		} catch (JavaModelException e) {
			return false;
		}
		return true;
	}

}
