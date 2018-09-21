/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.corrections.QuickFixProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.text.correction.QuickAssistProcessor;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextChange;

public class CodeActionHandler {

	/**
	 *
	 */
	public static final String COMMAND_ID_APPLY_EDIT = "java.apply.workspaceEdit";

	private QuickFixProcessor quickFixProcessor = new QuickFixProcessor();

	private QuickAssistProcessor quickAssistProcessor = new QuickAssistProcessor();

	private PreferenceManager preferenceManager;

	public CodeActionHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	/**
	 * @param params
	 * @return
	 */
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

		List<Either<Command, CodeAction>> $ = new ArrayList<>();
		try {
			CUCorrectionProposal[] corrections = this.quickFixProcessor.getCorrections(context, locations);
			Arrays.sort(corrections, new CUCorrectionProposalComparator());
			for (CUCorrectionProposal proposal : corrections) {
				$.add(getCodeActionFromProposal(proposal, params.getContext()));
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem resolving code actions", e);
		}

		try {
			CUCorrectionProposal[] corrections = this.quickAssistProcessor.getAssists(context, locations);
			Arrays.sort(corrections, new CUCorrectionProposalComparator());
			for (CUCorrectionProposal proposal : corrections) {
				$.add(getCodeActionFromProposal(proposal, params.getContext()));
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem resolving code actions", e);
		}

		return $;
	}

	private Either<Command, CodeAction> getCodeActionFromProposal(CUCorrectionProposal proposal, CodeActionContext context) throws CoreException {
		String name = proposal.getName();
		ICompilationUnit unit = proposal.getCompilationUnit();
		Command command = new Command(name, COMMAND_ID_APPLY_EDIT, Arrays.asList(convertChangeToWorkspaceEdit(unit, proposal.getChange())));

		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(proposal.getKind())) {
			CodeAction codeAction = new CodeAction(name);
			codeAction.setKind(proposal.getKind());
			codeAction.setCommand(command);
			codeAction.setDiagnostics(context.getDiagnostics());
			return Either.forRight(codeAction);
		} else {
			return Either.forLeft(command);
		}
	}

	private IProblemLocationCore[] getProblemLocationCores(ICompilationUnit unit, List<Diagnostic> diagnostics) {
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

	private int getProblemId(Diagnostic diagnostic) {
		int $ = 0;
		try {
			$ = Integer.parseInt(diagnostic.getCode());
		} catch (NumberFormatException e) {
			// return 0
		}
		return $;
	}

	private static WorkspaceEdit convertChangeToWorkspaceEdit(ICompilationUnit unit, Change change) {
		WorkspaceEdit $ = new WorkspaceEdit();

		if (change instanceof TextChange) {
			TextEditConverter converter = new TextEditConverter(unit, ((TextChange) change).getEdit());
			String uri = JDTUtils.toURI(unit);
			$.getChanges().put(uri, converter.convert());
		} else if (change instanceof CompositeChange) {
			for (Change c : ((CompositeChange) change).getChildren()) {
				if (c instanceof CompilationUnitChange) {
					TextEditConverter converter = new TextEditConverter(((CompilationUnitChange) c).getCompilationUnit(), ((TextChange) c).getEdit());
					String uri = JDTUtils.toURI(((CompilationUnitChange) c).getCompilationUnit());
					$.getChanges().put(uri, converter.convert());
				}
			}
		}

		return $;
	}

	private static CompilationUnit getASTRoot(ICompilationUnit unit) {
		return CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
	}

	private static class CUCorrectionProposalComparator implements Comparator<CUCorrectionProposal> {

		@Override
		public int compare(CUCorrectionProposal p1, CUCorrectionProposal p2) {
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
