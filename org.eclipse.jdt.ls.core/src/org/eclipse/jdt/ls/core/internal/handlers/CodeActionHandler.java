/*******************************************************************************
 * Copyright (c) 2017-2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.corrections.QuickFixProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.RefactorProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.text.correction.CUCorrectionCommandProposal;
import org.eclipse.jdt.ls.core.internal.text.correction.QuickAssistProcessor;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactoringCorrectionCommandProposal;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class CodeActionHandler {

	public static final String COMMAND_ID_APPLY_EDIT = "java.apply.workspaceEdit";

	private QuickFixProcessor quickFixProcessor;
	private RefactorProcessor refactorProcessor;
	private QuickAssistProcessor quickAssistProcessor;
	private SourceAssistProcessor sourceAssistProcessor;

	private PreferenceManager preferenceManager;

	public CodeActionHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
		this.quickFixProcessor = new QuickFixProcessor();
		this.sourceAssistProcessor = new SourceAssistProcessor(preferenceManager);
		this.quickAssistProcessor = new QuickAssistProcessor(preferenceManager);
		this.refactorProcessor = new RefactorProcessor(preferenceManager);
	}

	public List<Either<Command, CodeAction>> getCodeActionCommands(CodeActionParams params, IProgressMonitor monitor) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return Collections.emptyList();
		}

		int start = DiagnosticsHelper.getStartOffset(unit, params.getRange());
		int end = DiagnosticsHelper.getEndOffset(unit, params.getRange());
		InnovationContext context = new InnovationContext(unit, start, end - start);
		context.setASTRoot(getASTRoot(unit));

		IProblemLocationCore[] locations = this.getProblemLocationCores(unit, params.getContext().getDiagnostics());

		List<String> codeActionKinds = new ArrayList<>();
		if (params.getContext().getOnly() != null && !params.getContext().getOnly().isEmpty()) {
			codeActionKinds.addAll(params.getContext().getOnly());
		} else {
			List<String> defaultCodeActionKinds = Arrays.asList(
				CodeActionKind.QuickFix,
				CodeActionKind.Refactor,
				JavaCodeActionKind.QUICK_ASSIST,
				CodeActionKind.Source
			);
			codeActionKinds.addAll(defaultCodeActionKinds);
		}

		List<ChangeCorrectionProposal> proposals = new ArrayList<>();
		if (codeActionKinds.contains(CodeActionKind.QuickFix)) {
			try {

				List<ChangeCorrectionProposal> quickfixProposals = this.quickFixProcessor.getCorrections(context, locations);
				proposals.addAll(quickfixProposals);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem resolving quick fix code actions", e);
			}
		}

		if (codeActionKinds.contains(CodeActionKind.Refactor)) {
			try {
				List<ChangeCorrectionProposal> refactorProposals = this.refactorProcessor.getProposals(params, context, locations);
				proposals.addAll(refactorProposals);

			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem resolving refactor code actions", e);
			}
		}

		if (codeActionKinds.contains(JavaCodeActionKind.QUICK_ASSIST)) {
			try {
				List<ChangeCorrectionProposal> quickassistProposals = this.quickAssistProcessor.getAssists(params, context, locations);
				proposals.addAll(quickassistProposals);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem resolving quick assist code actions", e);
			}
		}

		// TODO (Yan): See https://github.com/eclipse/eclipse.jdt.ls/issues/1250
		proposals.sort(new ChangeCorrectionProposalComparator());

		List<Either<Command, CodeAction>> codeActions = new ArrayList<>();
		try {
			for (ChangeCorrectionProposal proposal : proposals) {
				Optional<Either<Command, CodeAction>> codeActionFromProposal = getCodeActionFromProposal(proposal, params.getContext());
				if (codeActionFromProposal.isPresent() && !codeActions.contains(codeActionFromProposal.get())) {
					codeActions.add(codeActionFromProposal.get());
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem converting proposal to code actions", e);
		}

		if (codeActionKinds.contains(CodeActionKind.Source)) {
			codeActions.addAll(sourceAssistProcessor.getSourceActionCommands(params, context, locations));
		}
		return codeActions;
	}

	private Optional<Either<Command, CodeAction>> getCodeActionFromProposal(ChangeCorrectionProposal proposal, CodeActionContext context) throws CoreException {
		String name = proposal.getName();

		ICompilationUnit unit = null;
		if (proposal instanceof CUCorrectionProposal) {
			unit = ((CUCorrectionProposal) proposal).getCompilationUnit();
		}

		Command command;
		if (proposal instanceof CUCorrectionCommandProposal) {
			CUCorrectionCommandProposal commandProposal = (CUCorrectionCommandProposal) proposal;
			command = new Command(name, commandProposal.getCommand(), commandProposal.getCommandArguments());
		} else if (proposal instanceof RefactoringCorrectionCommandProposal) {
			RefactoringCorrectionCommandProposal commandProposal = (RefactoringCorrectionCommandProposal) proposal;
			command = new Command(name, commandProposal.getCommand(), commandProposal.getCommandArguments());
		} else {
			WorkspaceEdit edit = ChangeUtil.convertToWorkspaceEdit(proposal.getChange());
			if (!ChangeUtil.hasChanges(edit)) {
				return Optional.empty();
			}
			command = new Command(name, COMMAND_ID_APPLY_EDIT, Collections.singletonList(edit));
		}

		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(proposal.getKind())) {
			// TODO: Should set WorkspaceEdit directly instead of Command
			CodeAction codeAction = new CodeAction(name);
			codeAction.setKind(proposal.getKind());
			codeAction.setCommand(command);
			codeAction.setDiagnostics(context.getDiagnostics());
			return Optional.of(Either.forRight(codeAction));
		} else {
			return Optional.of(Either.forLeft(command));
		}
	}

	public static IProblemLocationCore[] getProblemLocationCores(ICompilationUnit unit, List<Diagnostic> diagnostics) {
		IProblemLocationCore[] locations = new IProblemLocationCore[diagnostics.size()];
		for (int i = 0; i < diagnostics.size(); i++) {
			Diagnostic diagnostic = diagnostics.get(i);
			int problemId = getProblemId(diagnostic);
			int start = DiagnosticsHelper.getStartOffset(unit, diagnostic.getRange());
			int end = DiagnosticsHelper.getEndOffset(unit, diagnostic.getRange());
			boolean isError = diagnostic.getSeverity() == DiagnosticSeverity.Error;
			locations[i] = new ProblemLocationCore(start, end - start, problemId, new String[0], isError, IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
		}
		return locations;
	}

	private static int getProblemId(Diagnostic diagnostic) {
		int $ = 0;
		try {
			$ = Integer.parseInt(diagnostic.getCode());
		} catch (NumberFormatException e) {
			// return 0
		}
		return $;
	}

	public static CompilationUnit getASTRoot(ICompilationUnit unit) {
		return CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
	}

	private static class ChangeCorrectionProposalComparator implements Comparator<ChangeCorrectionProposal> {

		@Override
		public int compare(ChangeCorrectionProposal p1, ChangeCorrectionProposal p2) {
			String k1 = p1.getKind();
			String k2 = p2.getKind();
			if (!StringUtils.isBlank(k1) && !StringUtils.isBlank(k2) && !k1.equals(k2)) {
				return k1.compareTo(k2);
			}

			int r1 = p1.getRelevance();
			int r2 = p2.getRelevance();
			int relevanceDif = r2 - r1;
			if (relevanceDif != 0) {
				return relevanceDif;
			}
			return p1.getName().compareToIgnoreCase(p2.getName());
		}

	}

}
