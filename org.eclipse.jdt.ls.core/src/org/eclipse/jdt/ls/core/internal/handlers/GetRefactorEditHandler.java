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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore.PositionInformation;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.RefactoringCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.text.correction.ExtractProposalUtility;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.Change;

public class GetRefactorEditHandler {
	public static final String RENAME_COMMAND = "java.action.rename";

	public static RefactorWorkspaceEdit getEditsForRefactor(GetRefactorEditParams params) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.context.getTextDocument().getUri());
		if (unit == null) {
			return null;
		}
		int start = DiagnosticsHelper.getStartOffset(unit, params.context.getRange());
		int end = DiagnosticsHelper.getEndOffset(unit, params.context.getRange());
		InnovationContext context = new InnovationContext(unit, start, end - start);
		context.setASTRoot(CodeActionHandler.getASTRoot(unit));
		IProblemLocationCore[] locations = CodeActionHandler.getProblemLocationCores(unit, params.context.getContext().getDiagnostics());
		boolean problemsAtLocation = locations.length != 0;

		try {
			Map formatterOptions = params.options == null ? null : FormatterHandler.getOptions(params.options, unit);
			RefactoringCorrectionProposal proposal = null;
			if (ExtractProposalUtility.EXTRACT_VARIABLE_COMMAND.equals(params.command) || ExtractProposalUtility.EXTRACT_VARIABLE_ALL_OCCURRENCE_COMMAND.equals(params.command)
					|| ExtractProposalUtility.EXTRACT_CONSTANT_COMMAND.equals(params.command)) {
				proposal = (RefactoringCorrectionProposal) getExtractVariableProposal(params.context, context, problemsAtLocation, params.command, formatterOptions);
			} else if (ExtractProposalUtility.EXTRACT_METHOD_COMMAND.equals(params.command)) {
				proposal = (RefactoringCorrectionProposal) getExtractMethodProposal(params.context, context, context.getCoveringNode(), problemsAtLocation, formatterOptions);
			} else if (ExtractProposalUtility.CONVERT_VARIABLE_TO_FIELD_COMMAND.equals(params.command)) {
				String initializeIn = (params.commandArguments != null && !params.commandArguments.isEmpty()) ? JSONUtility.toModel(params.commandArguments.get(0), String.class) : null;
				proposal = (RefactoringCorrectionProposal) ExtractProposalUtility.getConvertVariableToFieldProposal(params.context, context, problemsAtLocation, formatterOptions, initializeIn, false);
			} else if (ExtractProposalUtility.EXTRACT_FIELD_COMMAND.equals(params.command)) {
				String initializeIn = (params.commandArguments != null && !params.commandArguments.isEmpty()) ? JSONUtility.toModel(params.commandArguments.get(0), String.class) : null;
				proposal = (RefactoringCorrectionProposal) ExtractProposalUtility.getExtractFieldProposal(params.context, context, problemsAtLocation, formatterOptions, initializeIn, false);
			}

			if (proposal == null) {
				return null;
			}

			Change change = proposal.getChange();
			WorkspaceEdit edit = CodeActionHandler.convertChangeToWorkspaceEdit(unit, change);
			LinkedProposalModelCore linkedProposalModel = proposal.getLinkedProposalModel();
			Command additionalCommand = null;
			if (linkedProposalModel != null) {
				LinkedProposalPositionGroupCore linkedPositionGroup = linkedProposalModel.getPositionGroup("name", false);
				PositionInformation highlightPosition = getFirstTrackedNodePosition(linkedPositionGroup);
				if (highlightPosition != null) {
					int offset = highlightPosition.getOffset();
					int length = highlightPosition.getLength();
					RenamePosition renamePosition = new RenamePosition(JDTUtils.toURI(unit), offset, length);
					additionalCommand = new Command("Rename", RENAME_COMMAND, Arrays.asList(renamePosition));
				}
			}

			return new RefactorWorkspaceEdit(edit, additionalCommand);
		} catch (CoreException e) {
			// do nothing.
		}

		return null;
	}

	private static PositionInformation getFirstTrackedNodePosition(LinkedProposalPositionGroupCore positionGroup) {
		if (positionGroup == null) {
			return null;
		}

		PositionInformation[] positions = positionGroup.getPositions();
		if (positions == null || positions.length == 0) {
			return null;
		}

		return positions[0];
	}

	private static CUCorrectionProposal getExtractVariableProposal(CodeActionParams params, IInvocationContext context, boolean problemsAtLocation, String refactorType, Map formatterOptions) throws CoreException {
		if (ExtractProposalUtility.EXTRACT_VARIABLE_ALL_OCCURRENCE_COMMAND.equals(refactorType)) {
			return ExtractProposalUtility.getExtractVariableAllOccurrenceProposal(params, context, problemsAtLocation, formatterOptions, false);
		}

		if (ExtractProposalUtility.EXTRACT_VARIABLE_COMMAND.equals(refactorType)) {
			return ExtractProposalUtility.getExtractVariableProposal(params, context, problemsAtLocation, formatterOptions, false);
		}

		if (ExtractProposalUtility.EXTRACT_CONSTANT_COMMAND.equals(refactorType)) {
			return ExtractProposalUtility.getExtractConstantProposal(params, context, problemsAtLocation, formatterOptions, false);
		}

		return null;
	}

	private static CUCorrectionProposal getExtractMethodProposal(CodeActionParams params, IInvocationContext context, ASTNode coveringNode, boolean problemsAtLocation, Map formatterOptions) throws CoreException {
		return ExtractProposalUtility.getExtractMethodProposal(params, context, coveringNode, problemsAtLocation, formatterOptions, false);
	}

	public static class RenamePosition {
		public String uri;
		public int offset;
		public int length;

		public RenamePosition(String uri, int offset, int length) {
			this.uri = uri;
			this.offset = offset;
			this.length = length;
		}
	}

	public static class RefactorWorkspaceEdit {
		/**
		 * The workspace edit this code action performs.
		 */
		public WorkspaceEdit edit;
		/**
		 * A command this code action executes. If a code action provides a edit and a
		 * command, first the edit is executed and then the command.
		 */
		public Command command;

		public RefactorWorkspaceEdit(WorkspaceEdit edit) {
			this.edit = edit;
		}

		public RefactorWorkspaceEdit(WorkspaceEdit edit, Command command) {
			this.edit = edit;
			this.command = command;
		}
	}

	public static class GetRefactorEditParams {
		public String command;
		public List<Object> commandArguments;
		public CodeActionParams context;
		public FormattingOptions options;

		public GetRefactorEditParams(String command, CodeActionParams context) {
			this(command, null, context);
		}

		public GetRefactorEditParams(String command, List<Object> commandArguments, CodeActionParams context) {
			this(command, commandArguments, context, null);
		}

		public GetRefactorEditParams(String command, List<Object> commandArguments, CodeActionParams context, FormattingOptions options) {
			this.command = command;
			this.commandArguments = commandArguments;
			this.context = context;
			this.options = options;
		}
	}
}
