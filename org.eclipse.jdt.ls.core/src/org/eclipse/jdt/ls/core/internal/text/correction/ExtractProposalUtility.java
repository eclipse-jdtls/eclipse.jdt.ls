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

package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.code.PromoteTempToFieldRefactoring;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractConstantRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractFieldRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractMethodRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractTempRefactoring;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.RefactoringCorrectionProposal;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.ltk.core.refactoring.Refactoring;

public class ExtractProposalUtility {
	public static final String APPLY_REFACTORING_COMMAND_ID = "java.action.applyRefactoringCommand";
	public static final String EXTRACT_VARIABLE_ALL_OCCURRENCE_COMMAND = "extractVariableAllOccurrence";
	public static final String EXTRACT_VARIABLE_COMMAND = "extractVariable";
	public static final String EXTRACT_CONSTANT_COMMAND = "extractConstant";
	public static final String EXTRACT_METHOD_COMMAND = "extractMethod";
	public static final String EXTRACT_FIELD_COMMAND = "extractField";
	public static final String CONVERT_VARIABLE_TO_FIELD_COMMAND = "convertVariableToField";

	public static List<CUCorrectionProposal> getExtractVariableProposals(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation) throws CoreException {
		return getExtractVariableProposals(params, context, problemsAtLocation, false);
	}

	public static List<CUCorrectionProposal> getExtractVariableCommandProposals(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation) throws CoreException {
		return getExtractVariableProposals(params, context, problemsAtLocation, true);
	}

	public static CUCorrectionProposal getExtractMethodProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean problemsAtLocation) throws CoreException {
		return getExtractMethodProposal(params, context, coveringNode, problemsAtLocation, null, false);
	}

	public static CUCorrectionProposal getExtractMethodCommandProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean problemsAtLocation) throws CoreException {
		return getExtractMethodProposal(params, context, coveringNode, problemsAtLocation, null, true);
	}

	private static List<CUCorrectionProposal> getExtractVariableProposals(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, boolean returnAsCommand) throws CoreException {
		if (!supportsExtractVariable(context)) {
			return null;
		}

		List<CUCorrectionProposal> proposals = new ArrayList<>();
		CUCorrectionProposal proposal = getExtractVariableAllOccurrenceProposal(params, context, problemsAtLocation, null, returnAsCommand);
		if (proposal != null) {
			proposals.add(proposal);
		}

		proposal = getExtractVariableProposal(params, context, problemsAtLocation, null, returnAsCommand);
		if (proposal != null) {
			proposals.add(proposal);
		}

		proposal = getExtractConstantProposal(params, context, problemsAtLocation, null, returnAsCommand);
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
		final ICompilationUnit cu = context.getCompilationUnit();
		ExtractTempRefactoring extractTempRefactoring = new ExtractTempRefactoring(context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength(), formatterOptions);
		if (extractTempRefactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			String label = CorrectionMessages.QuickAssistProcessor_extract_to_local_all_description;
			int relevance;
			if (context.getSelectionLength() == 0) {
				relevance = IProposalRelevance.EXTRACT_LOCAL_ALL_ZERO_SELECTION;
			} else if (problemsAtLocation) {
				relevance = IProposalRelevance.EXTRACT_LOCAL_ALL_ERROR;
			} else {
				relevance = IProposalRelevance.EXTRACT_LOCAL_ALL;
			}

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
		final ICompilationUnit cu = context.getCompilationUnit();
		ExtractTempRefactoring extractTempRefactoringSelectedOnly = new ExtractTempRefactoring(context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength(), formatterOptions);
		extractTempRefactoringSelectedOnly.setReplaceAllOccurrences(false);
		if (extractTempRefactoringSelectedOnly.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			String label = CorrectionMessages.QuickAssistProcessor_extract_to_local_description;
			int relevance;
			if (context.getSelectionLength() == 0) {
				relevance = IProposalRelevance.EXTRACT_LOCAL_ZERO_SELECTION;
			} else if (problemsAtLocation) {
				relevance = IProposalRelevance.EXTRACT_LOCAL_ERROR;
			} else {
				relevance = IProposalRelevance.EXTRACT_LOCAL;
			}

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

	/**
	 * Merge the "Extract to Field" and "Convert Local Variable to Field" to a
	 * generic "Extract to Field".
	 */
	public static CUCorrectionProposal getGenericExtractFieldProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, String initializeIn, boolean returnAsCommand)
			throws CoreException {
		CUCorrectionProposal proposal = getConvertVariableToFieldProposal(params, context, problemsAtLocation, formatterOptions, initializeIn, returnAsCommand);
		if (proposal != null) {
			return proposal;
		}

		return getExtractFieldProposal(params, context, problemsAtLocation, formatterOptions, initializeIn, returnAsCommand);
	}

	public static CUCorrectionProposal getExtractFieldProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, String initializeIn, boolean returnAsCommand) throws CoreException {
		if (!supportsExtractVariable(context)) {
			return null;
		}

		final ICompilationUnit cu = context.getCompilationUnit();
		ExtractFieldRefactoring extractFieldRefactoringSelectedOnly = new ExtractFieldRefactoring(context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength());
		extractFieldRefactoringSelectedOnly.setFormatterOptions(formatterOptions);
		if (extractFieldRefactoringSelectedOnly.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			InitializeScope scope = InitializeScope.fromName(initializeIn);
			if (scope != null) {
				extractFieldRefactoringSelectedOnly.setInitializeIn(scope.ordinal());
			}
			String label = CorrectionMessages.QuickAssistProcessor_extract_to_field_description;
			int relevance;
			if (context.getSelectionLength() == 0) {
				relevance = IProposalRelevance.EXTRACT_LOCAL_ZERO_SELECTION;
			} else if (problemsAtLocation) {
				relevance = IProposalRelevance.EXTRACT_LOCAL_ERROR;
			} else {
				relevance = IProposalRelevance.EXTRACT_LOCAL;
			}

			if (returnAsCommand) {
				List<String> scopes = new ArrayList<>();
				if (extractFieldRefactoringSelectedOnly.canEnableSettingDeclareInMethod()) {
					scopes.add(InitializeScope.CURRENT_METHOD.getName());
				}

				if (extractFieldRefactoringSelectedOnly.canEnableSettingDeclareInFieldDeclaration()) {
					scopes.add(InitializeScope.FIELD_DECLARATION.getName());
				}

				if (extractFieldRefactoringSelectedOnly.canEnableSettingDeclareInConstructors()) {
					scopes.add(InitializeScope.CLASS_CONSTRUCTORS.getName());
				}
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

	public static CUCorrectionProposal getExtractConstantProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, Map formatterOptions, boolean returnAsCommand) throws CoreException {
		final ICompilationUnit cu = context.getCompilationUnit();
		ExtractConstantRefactoring extractConstRefactoring = new ExtractConstantRefactoring(context.getASTRoot(), context.getSelectionOffset(), context.getSelectionLength(), formatterOptions);
		if (extractConstRefactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			String label = CorrectionMessages.QuickAssistProcessor_extract_to_constant_description;
			int relevance;
			if (context.getSelectionLength() == 0) {
				relevance = IProposalRelevance.EXTRACT_CONSTANT_ZERO_SELECTION;
			} else if (problemsAtLocation) {
				relevance = IProposalRelevance.EXTRACT_CONSTANT_ERROR;
			} else {
				relevance = IProposalRelevance.EXTRACT_CONSTANT;
			}

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
		if (extractMethodRefactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
			String label = CorrectionMessages.QuickAssistProcessor_extractmethod_description;
			int relevance = problemsAtLocation ? IProposalRelevance.EXTRACT_METHOD_ERROR : IProposalRelevance.EXTRACT_METHOD;
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

	private static String getUniqueMethodName(ASTNode astNode, String suggestedName) throws JavaModelException {
		while (astNode != null && !(astNode instanceof TypeDeclaration || astNode instanceof AnonymousClassDeclaration)) {
			astNode = astNode.getParent();
		}
		if (astNode instanceof TypeDeclaration) {
			ITypeBinding typeBinding = ((TypeDeclaration) astNode).resolveBinding();
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
}
