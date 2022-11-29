/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTesterCore;
import org.eclipse.jdt.internal.corext.refactoring.code.PromoteTempToFieldRefactoring;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractConstantRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractFieldRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractMethodRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractTempRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.IntroduceParameterRefactoring;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.AssignToVariableAssistProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.RefactoringCorrectionProposal;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.ltk.core.refactoring.Refactoring;

public class RefactorProposalUtility {
	public static final String APPLY_REFACTORING_COMMAND_ID = "java.action.applyRefactoringCommand";
	public static final String EXTRACT_VARIABLE_ALL_OCCURRENCE_COMMAND = "extractVariableAllOccurrence";
	public static final String EXTRACT_VARIABLE_COMMAND = "extractVariable";
	public static final String ASSIGN_VARIABLE_COMMAND = "assignVariable";
	public static final String EXTRACT_CONSTANT_COMMAND = "extractConstant";
	public static final String EXTRACT_METHOD_COMMAND = "extractMethod";
	public static final String EXTRACT_FIELD_COMMAND = "extractField";
	public static final String ASSIGN_FIELD_COMMAND = "assignField";
	public static final String CONVERT_VARIABLE_TO_FIELD_COMMAND = "convertVariableToField";
	public static final String MOVE_FILE_COMMAND = "moveFile";
	public static final String MOVE_INSTANCE_METHOD_COMMAND = "moveInstanceMethod";
	public static final String MOVE_STATIC_MEMBER_COMMAND = "moveStaticMember";
	public static final String MOVE_TYPE_COMMAND = "moveType";
	public static final String INTRODUCE_PARAMETER_COMMAND = "introduceParameter";

	public static List<CUCorrectionProposal> getMoveRefactoringProposals(CodeActionParams params, IInvocationContext context) {
		int relevance = IProposalRelevance.MOVE_REFACTORING;
		List<String> kindOfActions = params.getContext().getOnly();
		boolean alwaysShowMove = kindOfActions != null && kindOfActions.contains(CodeActionKind.Refactor);
		ASTNode node = context.getCoveredNode();
		if (node == null) {
			node = context.getCoveringNode();
		}
		node = getDeclarationNode(node, alwaysShowMove);

		ICompilationUnit cu = context.getCompilationUnit();
		String uri = JDTUtils.toURI(cu);
		if (cu != null && node != null) {
			try {
				if (node instanceof MethodDeclaration || node instanceof FieldDeclaration || node instanceof AbstractTypeDeclaration) {
					String displayName = getDisplayName(node);
					String label = alwaysShowMove ? ActionMessages.MoveRefactoringAction_label : Messages.format(ActionMessages.MoveRefactoringAction_templateLabel, displayName);
					int memberType = node.getNodeType();
					String enclosingTypeName = getEnclosingType(node);
					String projectName = cu.getJavaProject().getProject().getName();
					if (node instanceof AbstractTypeDeclaration typeDecl) {
						MoveTypeInfo moveTypeInfo = new MoveTypeInfo(displayName, enclosingTypeName, projectName);
						if (isMoveInnerAvailable(typeDecl)) {
							moveTypeInfo.addDestinationKind("newFile");
						}

						if (isMoveStaticMemberAvailable(node)) {
							moveTypeInfo.addDestinationKind("class");
						}

						// move inner type.
						if (moveTypeInfo.isMoveAvaiable()) {
							return Collections.singletonList(
								new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_MOVE, cu, relevance, APPLY_REFACTORING_COMMAND_ID,
								Arrays.asList(MOVE_TYPE_COMMAND, params, moveTypeInfo)));
						}

						// move ICompilationUnit.
						return Collections.singletonList((new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_MOVE, cu, relevance, RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID,
								Arrays.asList(MOVE_FILE_COMMAND, params, new MoveFileInfo(uri)))));
					} else if (JdtFlags.isStatic((BodyDeclaration) node)) {
						// move static member.
						if (isMoveStaticMemberAvailable(node)) {
							return Collections.singletonList(new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_MOVE, cu, relevance, APPLY_REFACTORING_COMMAND_ID,
									Arrays.asList(MOVE_STATIC_MEMBER_COMMAND, params, new MoveMemberInfo(displayName, memberType, enclosingTypeName, projectName))));
						}
					} else if (node instanceof MethodDeclaration methodDecl) {
						// move instance method.
						if (isMoveMethodAvailable(methodDecl)) {
							return Collections.singletonList(new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_MOVE, cu, relevance, APPLY_REFACTORING_COMMAND_ID,
									Arrays.asList(MOVE_INSTANCE_METHOD_COMMAND, params, new MoveMemberInfo(displayName))));
						}
					}
				}
			} catch (JavaModelException e) {
				// do nothing.
			}
			return Collections.emptyList();
		}
		return alwaysShowMove ? Collections.singletonList((new CUCorrectionCommandProposal(ActionMessages.MoveRefactoringAction_label, JavaCodeActionKind.REFACTOR_MOVE, cu, relevance, RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID,
				Arrays.asList(MOVE_FILE_COMMAND, params, new MoveFileInfo(uri))))) : Collections.emptyList();

	}

	private static ASTNode getDeclarationNode(ASTNode node, boolean alwaysShowMove) {
		if (node == null) {
			return null;
		}

		if (alwaysShowMove) {
			while (node != null && !(node instanceof BodyDeclaration)) {
				node = node.getParent();
			}
		} else {
			/**
			 * When selection is within a Block but not within other ASTNode (where we don't
			 * want to provide this refactoring), its covering node is the whole
			 * BodyDeclaration. See
			 * https://github.com/redhat-developer/vscode-java/issues/1074#issuecomment-672520911
			 */
			if (node instanceof BodyDeclaration) {
				return null;
			}
			while (node != null && !(node instanceof BodyDeclaration) && !(node instanceof Statement)) {
				node = node.getParent();
			}
		}
		return node;
	}

	private static boolean isMoveMethodAvailable(MethodDeclaration declaration) throws JavaModelException {
		IMethodBinding methodBinding = declaration.resolveBinding();
		IMethod method = methodBinding == null ? null : (IMethod) methodBinding.getJavaElement();
		return method != null && RefactoringAvailabilityTester.isMoveMethodAvailable(method);
	}

	private static boolean isMoveStaticMemberAvailable(ASTNode declaration) throws JavaModelException {
		if (declaration instanceof MethodDeclaration methodDecl) {
			IMethodBinding method = methodDecl.resolveBinding();
			return method != null && RefactoringAvailabilityTesterCore.isMoveStaticAvailable((IMember) method.getJavaElement());
		} else if (declaration instanceof FieldDeclaration fieldDecl) {
			List<IMember> members = new ArrayList<>();
			for (Object fragment : fieldDecl.fragments()) {
				IVariableBinding variable = ((VariableDeclarationFragment) fragment).resolveBinding();
				if (variable != null) {
					members.add((IField) variable.getJavaElement());
				}
			}
			return RefactoringAvailabilityTesterCore.isMoveStaticMembersAvailable(members.toArray(new IMember[0]));
		} else if (declaration instanceof AbstractTypeDeclaration typeDecl) {
			ITypeBinding type = typeDecl.resolveBinding();
			return type != null && RefactoringAvailabilityTesterCore.isMoveStaticAvailable((IType) type.getJavaElement());
		}

		return false;
	}

	private static boolean isMoveInnerAvailable(AbstractTypeDeclaration declaration) throws JavaModelException {
		ITypeBinding type = declaration.resolveBinding();
		if (type != null) {
			return RefactoringAvailabilityTester.isMoveInnerAvailable((IType) type.getJavaElement());
		}

		return false;
	}

	private static String getDisplayName(ASTNode declaration) {
		if (declaration instanceof MethodDeclaration methodDecl) {
			IMethodBinding method = methodDecl.resolveBinding();
			if (method != null) {
				String name = method.getName();
				String[] parameters = Stream.of(method.getParameterTypes()).map(type -> type.getName()).toArray(String[]::new);
				return name + "(" + String.join(",", parameters) + ")";
			}
		} else if (declaration instanceof FieldDeclaration fieldDecl) {
			List<String> fieldNames = new ArrayList<>();
			for (Object fragment : fieldDecl.fragments()) {
				IVariableBinding variable = ((VariableDeclarationFragment) fragment).resolveBinding();
				if (variable != null) {
					fieldNames.add(variable.getName());
				}
			}
			return String.join(",", fieldNames);
		} else if (declaration instanceof AbstractTypeDeclaration typeDecl) {
			ITypeBinding type = typeDecl.resolveBinding();
			if (type != null) {
				return type.getName();
			}
		}

		return null;
	}

	private static String getEnclosingType(ASTNode declaration) {
		ASTNode node = declaration == null ? null : declaration.getParent();
		ITypeBinding type = ASTNodes.getEnclosingType(node);
		return type == null ? null : type.getQualifiedName();
	}

	public static List<CUCorrectionProposal> getExtractVariableProposals(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, boolean inferSelectionSupport) throws CoreException {
		return getExtractVariableProposals(params, context, problemsAtLocation, false, inferSelectionSupport);
	}

	public static List<CUCorrectionProposal> getExtractVariableCommandProposals(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, boolean inferSelectionSupport) throws CoreException {
		return getExtractVariableProposals(params, context, problemsAtLocation, true, inferSelectionSupport);
	}

	public static CUCorrectionProposal getExtractMethodProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean problemsAtLocation, boolean inferSelectionSupport) throws CoreException {
		return getExtractMethodProposal(params, context, coveringNode, problemsAtLocation, null, false, inferSelectionSupport);
	}

	public static CUCorrectionProposal getExtractMethodCommandProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean problemsAtLocation, boolean inferSelectionSupport) throws CoreException {
		return getExtractMethodProposal(params, context, coveringNode, problemsAtLocation, null, true, inferSelectionSupport);
	}

	private static List<CUCorrectionProposal> getExtractVariableProposals(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, boolean returnAsCommand, boolean inferSelectionSupport) throws CoreException {
		if (!supportsExtractVariable(context)) {
			return null;
		}

		List<CUCorrectionProposal> proposals = new ArrayList<>();
		CUCorrectionProposal proposal = getExtractVariableAllOccurrenceProposal(params, context, problemsAtLocation, null, returnAsCommand, inferSelectionSupport);
		if (proposal != null) {
			proposals.add(proposal);
		}

		proposal = getExtractVariableProposal(params, context, problemsAtLocation, null, returnAsCommand, inferSelectionSupport);
		if (proposal != null) {
			proposals.add(proposal);
		}

		proposal = getExtractConstantProposal(params, context, problemsAtLocation, null, returnAsCommand, inferSelectionSupport);
		if (proposal != null) {
			proposals.add(proposal);
		}

		return proposals;
	}

	private static boolean supportsExtractVariable(IInvocationContext context) {
		ASTNode node = context.getCoveredNode();
		if (!(node instanceof Expression)) {
			if (context.getSelectionLength() != 0) {
				return false;
			}

			node = context.getCoveringNode();
			if (!(node instanceof Expression)) {
				return false;
			}
		}

		final Expression expression = (Expression) node;
		ITypeBinding binding = expression.resolveTypeBinding();
		if (binding == null || Bindings.isVoidType(binding)) {
			return false;
		}

		return true;
	}

	public static CUCorrectionProposal getExtractVariableAllOccurrenceProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, boolean returnAsCommand) throws CoreException {
		return getExtractVariableAllOccurrenceProposal(params, context, problemsAtLocation, formatterOptions, returnAsCommand, false);
	}

	private static CUCorrectionProposal getExtractVariableAllOccurrenceProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, boolean returnAsCommand, boolean inferSelectionSupport) throws CoreException {
		final ICompilationUnit cu = context.getCompilationUnit();
		String label = CorrectionMessages.QuickAssistProcessor_extract_to_local_all_description;
		int relevance;
		if (context.getSelectionLength() == 0) {
			relevance = IProposalRelevance.EXTRACT_LOCAL_ALL_ZERO_SELECTION;
		} else if (problemsAtLocation) {
			relevance = IProposalRelevance.EXTRACT_LOCAL_ALL_ERROR;
		} else {
			relevance = IProposalRelevance.EXTRACT_LOCAL_ALL;
		}
		if (inferSelectionSupport && context.getSelectionLength() == 0) {
			ASTNode parent = context.getCoveringNode();
			while (parent != null && parent instanceof Expression) {
				if (parent instanceof ParenthesizedExpression) {
					parent = parent.getParent();
					continue;
				}
				ExtractTempRefactoring refactoring = new ExtractTempRefactoring(context.getASTRoot(), parent.getStartPosition(), parent.getLength());
				if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
					return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_VARIABLE, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_VARIABLE_ALL_OCCURRENCE_COMMAND, params));
				}
				parent = parent.getParent();
			}
			return null;
		}
		ExtractTempRefactoring extractTempRefactoring = new ExtractTempRefactoring(context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength(), formatterOptions);
		if (extractTempRefactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			if (returnAsCommand) {
				return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_VARIABLE, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_VARIABLE_ALL_OCCURRENCE_COMMAND, params));
			}

			extractTempRefactoring.setReplaceAllOccurrences(true);
			LinkedProposalModelCore linkedProposalModel = new LinkedProposalModelCore();
			extractTempRefactoring.setLinkedProposalModel(linkedProposalModel);
			extractTempRefactoring.setCheckResultForCompileProblems(false);
			RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_VARIABLE, cu, extractTempRefactoring, relevance) {
				@Override
				protected void init(Refactoring refactoring) throws CoreException {
					ExtractTempRefactoring etr = (ExtractTempRefactoring) refactoring;
					etr.setTempName(etr.guessTempName()); // expensive
				}
			};
			proposal.setLinkedProposalModel(linkedProposalModel);
			return proposal;
		}

		return null;
	}

	public static CUCorrectionProposal getExtractVariableProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, boolean returnAsCommand) throws CoreException {
		return getExtractVariableProposal(params, context, problemsAtLocation, formatterOptions, returnAsCommand, false);
	}

	private static CUCorrectionProposal getExtractVariableProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, boolean returnAsCommand, boolean inferSelectionSupport) throws CoreException {
		final ICompilationUnit cu = context.getCompilationUnit();
		String label = CorrectionMessages.QuickAssistProcessor_extract_to_local_description;
		int relevance;
		if (context.getSelectionLength() == 0) {
			relevance = IProposalRelevance.EXTRACT_LOCAL_ZERO_SELECTION;
		} else if (problemsAtLocation) {
			relevance = IProposalRelevance.EXTRACT_LOCAL_ERROR;
		} else {
			relevance = IProposalRelevance.EXTRACT_LOCAL;
		}
		if (inferSelectionSupport && context.getSelectionLength() == 0) {
			ASTNode parent = context.getCoveringNode();
			while (parent != null && parent instanceof Expression) {
				if (parent instanceof ParenthesizedExpression) {
					parent = parent.getParent();
					continue;
				}
				ExtractTempRefactoring refactoring = new ExtractTempRefactoring(context.getASTRoot(), parent.getStartPosition(), parent.getLength());
				if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
					return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_VARIABLE, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_VARIABLE_COMMAND, params));
				}
				parent = parent.getParent();
			}
			return null;
		}
		ExtractTempRefactoring extractTempRefactoringSelectedOnly = new ExtractTempRefactoring(context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength(), formatterOptions);
		extractTempRefactoringSelectedOnly.setReplaceAllOccurrences(false);
		if (extractTempRefactoringSelectedOnly.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			if (returnAsCommand) {
				return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_VARIABLE, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_VARIABLE_COMMAND, params));
			}

			LinkedProposalModelCore linkedProposalModel = new LinkedProposalModelCore();
			extractTempRefactoringSelectedOnly.setLinkedProposalModel(linkedProposalModel);
			extractTempRefactoringSelectedOnly.setCheckResultForCompileProblems(false);
			RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_VARIABLE, cu, extractTempRefactoringSelectedOnly, relevance) {
				@Override
				protected void init(Refactoring refactoring) throws CoreException {
					ExtractTempRefactoring etr = (ExtractTempRefactoring) refactoring;
					etr.setTempName(etr.guessTempName()); // expensive
				}
			};
			proposal.setLinkedProposalModel(linkedProposalModel);
			return proposal;
		}

		return null;
	}

	public static boolean containsMatchingProblem(IProblemLocationCore[] locations, int problemId) {
		if (locations != null) {
			for (IProblemLocationCore location : locations) {
				if (IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER.equals(location.getMarkerType()) && location.getProblemId() == problemId) {
					return true;
				}
			}
		}
		return false;
	}

	public static CUCorrectionProposal getAssignVariableProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, boolean returnAsCommand,
			IProblemLocationCore[] locations)
			throws CoreException {
		ASTNode node = context.getCoveringNode();
		Statement statement = ASTResolving.findParentStatement(node);
		if (!(statement instanceof ExpressionStatement)) {
			return null;
		}
		ExpressionStatement expressionStatement = (ExpressionStatement) statement;
		Expression expression = expressionStatement.getExpression();
		if (expression.getNodeType() == ASTNode.ASSIGNMENT) {
			return null;
		}
		ITypeBinding typeBinding = expression.resolveTypeBinding();
		typeBinding = Bindings.normalizeTypeBinding(typeBinding);
		if (typeBinding == null) {
			return null;
		}
		if (containsMatchingProblem(locations, IProblem.UnusedObjectAllocation)) {
			return null;
		}
		final ICompilationUnit cu = context.getCompilationUnit();
		int relevance;
		if (context.getSelectionLength() == 0) {
			relevance = IProposalRelevance.EXTRACT_LOCAL_ZERO_SELECTION;
		} else if (problemsAtLocation) {
			relevance = IProposalRelevance.EXTRACT_LOCAL_ERROR;
		} else {
			relevance = IProposalRelevance.EXTRACT_LOCAL;
		}
		if (returnAsCommand) {
			return new AssignToVariableAssistCommandProposal(cu, JavaCodeActionKind.REFACTOR_ASSIGN_VARIABLE, AssignToVariableAssistProposal.LOCAL, expressionStatement, typeBinding, relevance, APPLY_REFACTORING_COMMAND_ID,
					Arrays.asList(ASSIGN_VARIABLE_COMMAND, params));
		} else {
			return new AssignToVariableAssistProposal(cu, JavaCodeActionKind.REFACTOR_ASSIGN_VARIABLE, AssignToVariableAssistProposal.LOCAL, expressionStatement, typeBinding, relevance);
		}
	}

	public static CUCorrectionProposal getAssignFieldProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, boolean returnAsCommand,
			IProblemLocationCore[] locations) throws CoreException {
		ASTNode node = context.getCoveringNode();
		Statement statement = ASTResolving.findParentStatement(node);
		if (!(statement instanceof ExpressionStatement)) {
			return null;
		}
		ExpressionStatement expressionStatement = (ExpressionStatement) statement;
		Expression expression = expressionStatement.getExpression();
		if (expression.getNodeType() == ASTNode.ASSIGNMENT) {
			return null;
		}
		ITypeBinding typeBinding = expression.resolveTypeBinding();
		typeBinding = Bindings.normalizeTypeBinding(typeBinding);
		if (typeBinding == null) {
			return null;
		}
		if (containsMatchingProblem(locations, IProblem.UnusedObjectAllocation)) {
			return null;
		}
		final ICompilationUnit cu = context.getCompilationUnit();
		ASTNode type = ASTResolving.findParentType(expression);
		if (type != null) {
			int relevance;
			if (context.getSelectionLength() == 0) {
				relevance = IProposalRelevance.EXTRACT_LOCAL_ZERO_SELECTION;
			} else if (problemsAtLocation) {
				relevance = IProposalRelevance.EXTRACT_LOCAL_ERROR;
			} else {
				relevance = IProposalRelevance.EXTRACT_LOCAL;
			}
			if (returnAsCommand) {
				return new AssignToVariableAssistCommandProposal(cu, JavaCodeActionKind.REFACTOR_ASSIGN_FIELD, AssignToVariableAssistProposal.FIELD, expressionStatement, typeBinding, relevance, APPLY_REFACTORING_COMMAND_ID,
						Arrays.asList(ASSIGN_FIELD_COMMAND, params));
			} else {
				return new AssignToVariableAssistProposal(cu, JavaCodeActionKind.REFACTOR_ASSIGN_FIELD, AssignToVariableAssistProposal.FIELD, expressionStatement, typeBinding, relevance);
			}
		}
		return null;
	}

	/**
	 * Merge the "Extract to Field" and "Convert Local Variable to Field" to a
	 * generic "Extract to Field".
	 */
	public static CUCorrectionProposal getGenericExtractFieldProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, String initializeIn, boolean returnAsCommand, boolean inferSelectionSupport)
			throws CoreException {
		CUCorrectionProposal proposal = getConvertVariableToFieldProposal(params, context, problemsAtLocation, formatterOptions, initializeIn, returnAsCommand);
		if (proposal != null) {
			return proposal;
		}

		return getExtractFieldProposal(params, context, problemsAtLocation, formatterOptions, initializeIn, returnAsCommand, inferSelectionSupport);
	}

	public static CUCorrectionProposal getExtractFieldProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, String initializeIn, boolean returnAsCommand) throws CoreException {
		return getExtractFieldProposal(params, context, problemsAtLocation, formatterOptions, initializeIn, returnAsCommand, false);
	}

	private static CUCorrectionProposal getExtractFieldProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, String initializeIn, boolean returnAsCommand, boolean inferSelectionSupport) throws CoreException {
		if (!supportsExtractVariable(context)) {
			return null;
		}
		final ICompilationUnit cu = context.getCompilationUnit();
		String label = CorrectionMessages.QuickAssistProcessor_extract_to_field_description;
		int relevance;
		if (context.getSelectionLength() == 0) {
			relevance = IProposalRelevance.EXTRACT_LOCAL_ZERO_SELECTION;
		} else if (problemsAtLocation) {
			relevance = IProposalRelevance.EXTRACT_LOCAL_ERROR;
		} else {
			relevance = IProposalRelevance.EXTRACT_LOCAL;
		}
		if (context.getSelectionLength() == 0 && inferSelectionSupport) {
			ASTNode parent = context.getCoveringNode();
			while (parent != null && parent instanceof Expression) {
				if (parent instanceof ParenthesizedExpression) {
					parent = parent.getParent();
					continue;
				}
				ExtractFieldRefactoring refactoring = new ExtractFieldRefactoring(context.getASTRoot(), parent.getStartPosition(), parent.getLength());
				if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
					InitializeScope scope = InitializeScope.fromName(initializeIn);
					if (scope != null) {
						refactoring.setInitializeIn(scope.ordinal());
					}
					List<String> scopes = getInitializeScopes(refactoring);
					if (!scopes.isEmpty()) {
						return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_FIELD, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_FIELD_COMMAND, params));
					}
				}
				parent = parent.getParent();
			}
			return null;
		}
		ExtractFieldRefactoring extractFieldRefactoringSelectedOnly = new ExtractFieldRefactoring(context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength());
		extractFieldRefactoringSelectedOnly.setFormatterOptions(formatterOptions);
		if (extractFieldRefactoringSelectedOnly.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			InitializeScope scope = InitializeScope.fromName(initializeIn);
			if (scope != null) {
				extractFieldRefactoringSelectedOnly.setInitializeIn(scope.ordinal());
			}
			if (returnAsCommand) {
				List<String> scopes = getInitializeScopes(extractFieldRefactoringSelectedOnly);
				return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_FIELD, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_FIELD_COMMAND, params, new ExtractFieldInfo(scopes)));
			}
			LinkedProposalModelCore linkedProposalModel = new LinkedProposalModelCore();
			extractFieldRefactoringSelectedOnly.setLinkedProposalModel(linkedProposalModel);
			RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_FIELD, cu, extractFieldRefactoringSelectedOnly, relevance) {
				@Override
				protected void init(Refactoring refactoring) throws CoreException {
					ExtractFieldRefactoring etr = (ExtractFieldRefactoring) refactoring;
					etr.setFieldName(etr.guessFieldName()); // expensive
				}
			};
			proposal.setLinkedProposalModel(linkedProposalModel);
			return proposal;
		}

		return null;
	}

	public static List<String> getInitializeScopes(ExtractFieldRefactoring refactoring) throws CoreException {
		List<String> scopes = new ArrayList<>();
		if (refactoring.canEnableSettingDeclareInMethod()) {
			scopes.add(InitializeScope.CURRENT_METHOD.getName());
		}

		if (refactoring.canEnableSettingDeclareInFieldDeclaration()) {
			scopes.add(InitializeScope.FIELD_DECLARATION.getName());
		}

		if (refactoring.canEnableSettingDeclareInConstructors()) {
			scopes.add(InitializeScope.CLASS_CONSTRUCTORS.getName());
		}
		return scopes;
	}

	public static CUCorrectionProposal getExtractConstantProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, boolean returnAsCommand) throws CoreException {
		return getExtractConstantProposal(params, context, problemsAtLocation, formatterOptions, returnAsCommand, false);
	}

	private static CUCorrectionProposal getExtractConstantProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, boolean returnAsCommand, boolean inferSelectionSupport) throws CoreException {
		final ICompilationUnit cu = context.getCompilationUnit();
		String label = CorrectionMessages.QuickAssistProcessor_extract_to_constant_description;
		int relevance;
		if (context.getSelectionLength() == 0) {
			relevance = IProposalRelevance.EXTRACT_CONSTANT_ZERO_SELECTION;
		} else if (problemsAtLocation) {
			relevance = IProposalRelevance.EXTRACT_CONSTANT_ERROR;
		} else {
			relevance = IProposalRelevance.EXTRACT_CONSTANT;
		}
		if (inferSelectionSupport && context.getSelectionLength() == 0) {
			ASTNode parent = context.getCoveringNode();
			while (parent != null && parent instanceof Expression) {
				if (parent instanceof ParenthesizedExpression) {
					parent = parent.getParent();
					continue;
				}
				ExtractConstantRefactoring refactoring = new ExtractConstantRefactoring(context.getASTRoot(), parent.getStartPosition(), parent.getLength());
				if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
					return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_CONSTANT, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_CONSTANT_COMMAND, params));
				}
				parent = parent.getParent();
			}
			return null;
		}
		ExtractConstantRefactoring extractConstRefactoring = new ExtractConstantRefactoring(context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength(), formatterOptions);
		if (extractConstRefactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			if (returnAsCommand) {
				return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_CONSTANT, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_CONSTANT_COMMAND, params));
			}

			LinkedProposalModelCore linkedProposalModel = new LinkedProposalModelCore();
			extractConstRefactoring.setLinkedProposalModel(linkedProposalModel);
			extractConstRefactoring.setCheckResultForCompileProblems(false);
			RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_CONSTANT, cu, extractConstRefactoring, relevance) {
				@Override
				protected void init(Refactoring refactoring) throws CoreException {
					ExtractConstantRefactoring etr = (ExtractConstantRefactoring) refactoring;
					etr.setConstantName(etr.guessConstantName()); // expensive
				}
			};
			proposal.setLinkedProposalModel(linkedProposalModel);
			return proposal;
		}

		return null;
	}

	public static CUCorrectionProposal getConvertVariableToFieldProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, String initializeIn, boolean returnAsCommand)
			throws CoreException {
		ASTNode node = context.getCoveredNode();
		if (!(node instanceof SimpleName)) {
			if (context.getSelectionLength() != 0) {
				return null;
			}

			node = context.getCoveringNode();
			if (!(node instanceof SimpleName)) {
				return null;
			}
		}

		SimpleName name = (SimpleName) node;
		IBinding binding = name.resolveBinding();
		if (!(binding instanceof IVariableBinding)) {
			return null;
		}
		IVariableBinding varBinding = (IVariableBinding) binding;
		if (varBinding.isField() || varBinding.isParameter()) {
			return null;
		}
		ASTNode decl = context.getASTRoot().findDeclaringNode(varBinding);
		if (decl == null || decl.getLocationInParent() != VariableDeclarationStatement.FRAGMENTS_PROPERTY) {
			return null;
		}

		PromoteTempToFieldRefactoring refactoring = new PromoteTempToFieldRefactoring((VariableDeclaration) decl);
		refactoring.setFormatterOptions(formatterOptions);
		if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			InitializeScope scope = InitializeScope.fromName(initializeIn);
			if (scope != null) {
				refactoring.setInitializeIn(scope.ordinal());
			}

			String label = CorrectionMessages.QuickAssistProcessor_extract_to_field_description;

			if (returnAsCommand) {
				List<String> scopes = new ArrayList<>();
				if (refactoring.canEnableSettingDeclareInMethod()) {
					scopes.add(InitializeScope.CURRENT_METHOD.getName());
				}

				if (refactoring.canEnableSettingDeclareInFieldDeclaration()) {
					scopes.add(InitializeScope.FIELD_DECLARATION.getName());
				}

				if (refactoring.canEnableSettingDeclareInConstructors()) {
					scopes.add(InitializeScope.CLASS_CONSTRUCTORS.getName());
				}
				return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_FIELD, context.getCompilationUnit(), IProposalRelevance.CONVERT_LOCAL_TO_FIELD, APPLY_REFACTORING_COMMAND_ID,
						Arrays.asList(CONVERT_VARIABLE_TO_FIELD_COMMAND, params, new ExtractFieldInfo(scopes)));
			}

			LinkedProposalModelCore linkedProposalModel = new LinkedProposalModelCore();
			refactoring.setLinkedProposalModel(linkedProposalModel);

			RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_FIELD, context.getCompilationUnit(), refactoring, IProposalRelevance.CONVERT_LOCAL_TO_FIELD) {
				@Override
				protected void init(Refactoring refactoring) throws CoreException {
					PromoteTempToFieldRefactoring etr = (PromoteTempToFieldRefactoring) refactoring;
					String[] names = etr.guessFieldNames();
					if (names.length > 0) {
						etr.setFieldName(names[0]); // expensive
					}
				}
			};
			proposal.setLinkedProposalModel(linkedProposalModel);
			return proposal;
		}

		return null;
	}

	public static CUCorrectionProposal getExtractMethodProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean problemsAtLocation, Map formattingOptions, boolean returnAsCommand) throws CoreException {
		return getExtractMethodProposal(params, context, coveringNode, problemsAtLocation, formattingOptions, returnAsCommand, false);
	}

	private static CUCorrectionProposal getExtractMethodProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean problemsAtLocation, Map formattingOptions, boolean returnAsCommand, boolean inferSelectionSupport) throws CoreException {
		if (!(coveringNode instanceof Expression) && !(coveringNode instanceof Statement) && !(coveringNode instanceof Block)) {
			return null;
		}
		if (coveringNode instanceof Block) {
			List<Statement> statements = ((Block) coveringNode).statements();
			int startIndex = getIndex(context.getSelectionOffset(), statements);
			if (startIndex == -1) {
				return null;
			}
			int endIndex = getIndex(context.getSelectionOffset() + context.getSelectionLength(), statements);
			if (endIndex == -1 || endIndex <= startIndex) {
				return null;
			}
		}

		final ICompilationUnit cu = context.getCompilationUnit();
		final ExtractMethodRefactoring extractMethodRefactoring = new ExtractMethodRefactoring(context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength(), formattingOptions);
		String uniqueMethodName = getUniqueMethodName(coveringNode, "extracted");
		extractMethodRefactoring.setMethodName(uniqueMethodName);
		String label = CorrectionMessages.QuickAssistProcessor_extractmethod_description;
		int relevance = problemsAtLocation ? IProposalRelevance.EXTRACT_METHOD_ERROR : IProposalRelevance.EXTRACT_METHOD;
		if (context.getSelectionLength() == 0) {
			if (!inferSelectionSupport) {
				return null;
			}
			ASTNode parent = coveringNode;
			while (parent != null && parent instanceof Expression) {
				if (parent instanceof ParenthesizedExpression) {
					parent = parent.getParent();
					continue;
				}
				ExtractMethodRefactoring refactoring = new ExtractMethodRefactoring(context.getASTRoot(), parent.getStartPosition(), parent.getLength(), formattingOptions);
				if (refactoring.checkInferConditions(new NullProgressMonitor()).isOK()) {
					return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_METHOD, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_METHOD_COMMAND, params));
				}
				parent = parent.getParent();
			}
			return null;
		} else if (extractMethodRefactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			if (returnAsCommand) {
				return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_METHOD, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(EXTRACT_METHOD_COMMAND, params));
			}

			LinkedProposalModelCore linkedProposalModel = new LinkedProposalModelCore();
			extractMethodRefactoring.setLinkedProposalModel(linkedProposalModel);
			RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, JavaCodeActionKind.REFACTOR_EXTRACT_METHOD, cu, extractMethodRefactoring, relevance);
			proposal.setLinkedProposalModel(linkedProposalModel);
			return proposal;
		}

		return null;
	}

	public static CUCorrectionProposal getIntroduceParameterRefactoringProposals(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean returnAsCommand, IProblemLocationCore[] problemLocations)
			throws CoreException {
		final ICompilationUnit cu = context.getCompilationUnit();
		final IntroduceParameterRefactoring introduceParameterRefactoring = new IntroduceParameterRefactoring(cu, context.getSelectionOffset(), context.getSelectionLength());
		LinkedProposalModelCore linkedProposalModel = new LinkedProposalModelCore();
		introduceParameterRefactoring.setLinkedProposalModel(linkedProposalModel);
		if (introduceParameterRefactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			introduceParameterRefactoring.setParameterName(introduceParameterRefactoring.guessedParameterName());
			String label = RefactoringCoreMessages.IntroduceParameterRefactoring_name + "...";
			int relevance = (problemLocations != null && problemLocations.length > 0) ? IProposalRelevance.EXTRACT_CONSTANT_ERROR : IProposalRelevance.EXTRACT_CONSTANT;
			if (returnAsCommand) {
				return new CUCorrectionCommandProposal(label, JavaCodeActionKind.REFACTOR_INTRODUCE_PARAMETER, cu, relevance, APPLY_REFACTORING_COMMAND_ID, Arrays.asList(INTRODUCE_PARAMETER_COMMAND, params));
			}
			RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, JavaCodeActionKind.REFACTOR_INTRODUCE_PARAMETER, cu, introduceParameterRefactoring, relevance);
			proposal.setLinkedProposalModel(linkedProposalModel);
			return proposal;
		}
		return null;
	}

	public static String getUniqueMethodName(ASTNode astNode, String suggestedName) throws JavaModelException {
		while (astNode != null && !(astNode instanceof TypeDeclaration || astNode instanceof AnonymousClassDeclaration)) {
			astNode = astNode.getParent();
		}
		if (astNode instanceof TypeDeclaration typeDecl) {
			ITypeBinding typeBinding = typeDecl.resolveBinding();
			if (typeBinding == null) {
				return suggestedName;
			}
			IType type = (IType) typeBinding.getJavaElement();
			if (type == null) {
				return suggestedName;
			}
			IMethod[] methods = type.getMethods();

			int suggestedPostfix = 2;
			String resultName = suggestedName;
			while (suggestedPostfix < 1000) {
				if (!hasMethod(methods, resultName)) {
					return resultName;
				}
				resultName = suggestedName + suggestedPostfix++;
			}
		}
		return suggestedName;
	}

	private static boolean hasMethod(IMethod[] methods, String name) {
		for (IMethod method : methods) {
			if (name.equals(method.getElementName())) {
				return true;
			}
		}
		return false;
	}

	private static int getIndex(int offset, List<Statement> statements) {
		for (int i = 0; i < statements.size(); i++) {
			Statement s = statements.get(i);
			if (offset <= s.getStartPosition()) {
				return i;
			}
			if (offset < s.getStartPosition() + s.getLength()) {
				return -1;
			}
		}
		return statements.size();
	}

	public enum InitializeScope {
		//@formatter:off
		FIELD_DECLARATION("Field declaration"),
		CURRENT_METHOD("Current method"),
		CLASS_CONSTRUCTORS("Class constructors");
		//@formatter:on

		private final String name;

		private InitializeScope(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public static InitializeScope fromName(String name) {
			if (name != null) {
				for (InitializeScope scope : values()) {
					if (scope.name.equals(name)) {
						return scope;
					}
				}
			}

			return null;
		}
	}

	public static class ExtractFieldInfo {
		List<String> initializedScopes;

		public ExtractFieldInfo(List<String> scopes) {
			this.initializedScopes = scopes;
		}
	}

	public static class MoveFileInfo {
		public String uri;

		public MoveFileInfo(String uri) {
			this.uri = uri;
		}
	}

	public static class MoveMemberInfo {
		public String displayName;
		public int memberType;
		public String enclosingTypeName;
		public String projectName;

		public MoveMemberInfo(String displayName, int memberType, String enclosingTypeName, String projectName) {
			this.displayName = displayName;
			this.memberType = memberType;
			this.enclosingTypeName = enclosingTypeName;
			this.projectName = projectName;
		}

		public MoveMemberInfo(String displayName, String enclosingTypeName, String projectName) {
			this(displayName, 0, enclosingTypeName, projectName);
		}

		public MoveMemberInfo(String displayName) {
			this.displayName = displayName;
		}
	}

	public static class MoveTypeInfo extends MoveMemberInfo {
		public List<String> supportedDestinationKinds = new ArrayList<>();

		public MoveTypeInfo(String displayName, String enclosingTypeName, String projectName) {
			super(displayName, ASTNode.TYPE_DECLARATION, enclosingTypeName, projectName);
		}

		public void addDestinationKind(String kind) {
			supportedDestinationKinds.add(kind);
		}

		public boolean isMoveAvaiable() {
			return !supportedDestinationKinds.isEmpty();
		}
	}

}
