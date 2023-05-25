/*******************************************************************************
 * Copyright (c) 2017-2020 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
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
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.NewAnnotationMemberProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.NewCUProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.NewMethodCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.NewVariableCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.text.correction.AssignToVariableAssistCommandProposal;
import org.eclipse.jdt.ls.core.internal.text.correction.CUCorrectionCommandProposal;
import org.eclipse.jdt.ls.core.internal.text.correction.CodeActionComparator;
import org.eclipse.jdt.ls.core.internal.text.correction.NonProjectFixProcessor;
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
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class CodeActionHandler {
	public static final ResponseStore<Either<ChangeCorrectionProposal, CodeActionProposal>> codeActionStore
		= new ResponseStore<>(ForkJoinPool.commonPool().getParallelism());
	public static final String COMMAND_ID_APPLY_EDIT = "java.apply.workspaceEdit";

	private QuickFixProcessor quickFixProcessor;
	private RefactorProcessor refactorProcessor;
	private QuickAssistProcessor quickAssistProcessor;
	private SourceAssistProcessor sourceAssistProcessor;
	private NonProjectFixProcessor nonProjectFixProcessor;

	private PreferenceManager preferenceManager;

	public CodeActionHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
		this.quickFixProcessor = new QuickFixProcessor();
		this.sourceAssistProcessor = new SourceAssistProcessor(preferenceManager);
		this.quickAssistProcessor = new QuickAssistProcessor(preferenceManager);
		this.refactorProcessor = new RefactorProcessor(preferenceManager);
		this.nonProjectFixProcessor = new NonProjectFixProcessor(preferenceManager);
	}

	public List<Either<Command, CodeAction>> getCodeActionCommands(CodeActionParams params, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null || monitor.isCanceled()) {
			return Collections.emptyList();
		}

		Map<String, Object> formattingOptions = ConfigurationHandler.getFormattingOptions(params.getTextDocument().getUri());
		if (formattingOptions != null && !formattingOptions.isEmpty()) {
			Object tabSizeValue = formattingOptions.get(Preferences.JAVA_CONFIGURATION_TABSIZE);
			Object insertSpacesValue = formattingOptions.get(Preferences.JAVA_CONFIGURATION_INSERTSPACES);
			Map<String, String> customOptions = new HashMap<>();
			if (tabSizeValue != null) {
				try {
					int tabSize = Integer.parseInt(String.valueOf(tabSizeValue));
					if (tabSize > 0) {
						customOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, Integer.toString(tabSize));
					}
				} catch (Exception ex) {
					// do nothing
				}
			}

			if (insertSpacesValue != null) {
				boolean insertSpaces = Boolean.parseBoolean(String.valueOf(insertSpacesValue));
				customOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, insertSpaces ? JavaCore.SPACE : JavaCore.TAB);
			}

			if (!customOptions.isEmpty()) {
				unit.setOptions(customOptions);
			}
		}

		CompilationUnit astRoot = getASTRoot(unit, monitor);
		if (astRoot == null || monitor.isCanceled()) {
			return Collections.emptyList();
		}

		InnovationContext context = getContext(unit, astRoot, params.getRange());
		List<Diagnostic> diagnostics = params.getContext().getDiagnostics().stream().filter((d) -> {
			return JavaLanguageServerPlugin.SERVER_SOURCE_ID.equals(d.getSource());
		}).collect(Collectors.toList());
		IProblemLocationCore[] locations = getProblemLocationCores(unit, diagnostics);
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}

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

		List<Either<Command, CodeAction>> codeActions = new ArrayList<>();
		List<ChangeCorrectionProposal> proposals = new ArrayList<>();
		ChangeCorrectionProposalComparator comparator = new ChangeCorrectionProposalComparator();
		if (containsKind(codeActionKinds, CodeActionKind.QuickFix)) {
			try {
				codeActions.addAll(nonProjectFixProcessor.getCorrections(params, context, locations));
				List<ChangeCorrectionProposal> quickfixProposals = this.quickFixProcessor.getCorrections(params, context, locations);
				this.quickFixProcessor.addAddAllMissingImportsProposal(context, quickfixProposals);
				Set<ChangeCorrectionProposal> quickSet = new TreeSet<>(comparator);
				quickSet.addAll(quickfixProposals);
				proposals.addAll(quickSet);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem resolving quick fix code actions", e);
			}
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}

		if (containsKind(codeActionKinds, CodeActionKind.Refactor)) {
			try {
				List<ChangeCorrectionProposal> refactorProposals = this.refactorProcessor.getProposals(params, context, locations);
				refactorProposals.sort(comparator);
				proposals.addAll(refactorProposals);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem resolving refactor code actions", e);
			}
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		if (containsKind(codeActionKinds, JavaCodeActionKind.QUICK_ASSIST)) {
			try {
				List<ChangeCorrectionProposal> quickassistProposals = this.quickAssistProcessor.getAssists(params, context, locations);
				quickassistProposals.sort(comparator);
				proposals.addAll(quickassistProposals);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem resolving quick assist code actions", e);
			}
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		try {
			for (ChangeCorrectionProposal proposal : proposals) {
				Optional<Either<Command, CodeAction>> codeActionFromProposal = getCodeActionFromProposal(params.getTextDocument().getUri(), proposal, params.getContext());
				if (codeActionFromProposal.isPresent() && !codeActions.contains(codeActionFromProposal.get())) {
					codeActions.add(codeActionFromProposal.get());
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem converting proposal to code actions", e);
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		if (containsKind(codeActionKinds, CodeActionKind.Source)) {
			codeActions.addAll(sourceAssistProcessor.getSourceActionCommands(params, context, locations, monitor));
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}

		codeActions.sort(new CodeActionComparator());
		populateDataFields(codeActions);
		return codeActions;
	}

	private void populateDataFields(List<Either<Command, CodeAction>> codeActions) {
		ResponseStore.ResponseItem<Either<ChangeCorrectionProposal, CodeActionProposal>> response = codeActionStore.createResponse();
		List<Either<ChangeCorrectionProposal, CodeActionProposal>> proposals = new ArrayList<>();
		codeActions.forEach(action -> {
			if (action.isRight()) {
				Either<ChangeCorrectionProposal, CodeActionProposal> proposal = null;
				Object originalData = action.getRight().getData();
				if (originalData instanceof CodeActionData codeActionData) {
					Object originalProposal = codeActionData.getProposal();
					if (originalProposal instanceof ChangeCorrectionProposal changeCorrectionProposal) {
						proposal = Either.forLeft(changeCorrectionProposal);
					} else if (originalProposal instanceof CodeActionProposal codeActionProposal) {
						proposal = Either.forRight(codeActionProposal);
					} else {
						action.getRight().setData(null);
						return;
					}
				} else {
					action.getRight().setData(null);
					return;
				}
				Map<String, String> data = new HashMap<>();
				data.put(CodeActionResolveHandler.DATA_FIELD_REQUEST_ID, String.valueOf(response.getId()));
				data.put(CodeActionResolveHandler.DATA_FIELD_PROPOSAL_ID, String.valueOf(proposals.size()));
				action.getRight().setData(data);
				proposals.add(proposal);
			}
		});

		if (!proposals.isEmpty()) {
			response.setProposals(proposals);
			codeActionStore.store(response);
		}
	}

	private Optional<Either<Command, CodeAction>> getCodeActionFromProposal(String uri, ChangeCorrectionProposal proposal, CodeActionContext context) throws CoreException {
		String name = proposal.getName();

		Command command = null;
		if (proposal instanceof CUCorrectionCommandProposal commandProposal) {
			command = new Command(name, commandProposal.getCommand(), commandProposal.getCommandArguments());
		} else if (proposal instanceof RefactoringCorrectionCommandProposal commandProposal) {
			command = new Command(name, commandProposal.getCommand(), commandProposal.getCommandArguments());
		} else if (proposal instanceof AssignToVariableAssistCommandProposal commandProposal) {
			command = new Command(name, commandProposal.getCommand(), commandProposal.getCommandArguments());
		} else {
			if (!this.preferenceManager.getClientPreferences().isResolveCodeActionSupported()) {
				WorkspaceEdit edit = ChangeUtil.convertToWorkspaceEdit(proposal.getChange());
				if (!ChangeUtil.hasChanges(edit)) {
					return Optional.empty();
				}
				command = new Command(name, COMMAND_ID_APPLY_EDIT, Collections.singletonList(edit));
			}
		}

		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(proposal.getKind())) {
			// TODO: Should set WorkspaceEdit directly instead of Command
			CodeAction codeAction = new CodeAction(name);
			codeAction.setKind(proposal.getKind());
			if (command == null) { // lazy resolve the edit.
				if (!preferenceManager.getPreferences().isValidateAllOpenBuffersOnChanges()) {
					if (proposal instanceof NewCUProposal
						|| proposal instanceof NewMethodCorrectionProposal
						|| proposal instanceof NewAnnotationMemberProposal
						|| proposal instanceof NewVariableCorrectionProposal
					) {
						codeAction.setCommand(new Command("refresh Diagnostics", "java.project.refreshDiagnostics", Arrays.asList(
							uri, "thisFile", false, true
						)));
					}
				}

				// The relevance is in descending order while CodeActionComparator sorts in ascending order
				codeAction.setData(new CodeActionData(proposal, -proposal.getRelevance()));
			} else {
				codeAction.setCommand(command);
				codeAction.setData(new CodeActionData(null, -proposal.getRelevance()));
			}
			if (proposal.getKind() != JavaCodeActionKind.QUICK_ASSIST) {
				codeAction.setDiagnostics(context.getDiagnostics());
			}
			return Optional.of(Either.forRight(codeAction));
		} else {
			return Optional.ofNullable(command != null ? Either.forLeft(command) : null);
		}
	}

	public static IProblemLocationCore[] getProblemLocationCores(ICompilationUnit unit, List<Diagnostic> diagnostics) {
		IProblemLocationCore[] locations = new IProblemLocationCore[diagnostics.size()];
		for (int i = 0; i < diagnostics.size(); i++) {
			Diagnostic diagnostic = diagnostics.get(i);
			int start = DiagnosticsHelper.getStartOffset(unit, diagnostic.getRange());
			int end = DiagnosticsHelper.getEndOffset(unit, diagnostic.getRange());
			boolean isError = diagnostic.getSeverity() == DiagnosticSeverity.Error;
			int problemId = getProblemId(diagnostic);
			List<String> arguments = new ArrayList<>();
			if (diagnostic.getData() instanceof JsonArray data) {
				for (JsonElement e : data) {
					arguments.add(e.getAsString());
				}
			}
			locations[i] = new ProblemLocationCore(start, end - start, problemId, arguments.toArray(new String[0]), isError, IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
		}
		return locations;
	}

	private static int getProblemId(Diagnostic diagnostic) {
		int $ = 0;
		try {
			Either<String, Integer> code = diagnostic.getCode();
			if (code != null) {
				if (code.getLeft() != null) {
					$ = Integer.parseInt(code.getLeft());
				} else if (code.getRight() != null) {
					$ = code.getRight().intValue();
				}
			}
		} catch (NumberFormatException e) {
			// return 0
		}
		return $;
	}

	public static CompilationUnit getASTRoot(ICompilationUnit unit) {
		return getASTRoot(unit, new NullProgressMonitor());
	}

	public static CompilationUnit getASTRoot(ICompilationUnit unit, IProgressMonitor monitor) {
		return CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
	}

	public static InnovationContext getContext(ICompilationUnit unit, CompilationUnit astRoot, Range range) {
		int start = DiagnosticsHelper.getStartOffset(unit, range);
		int end = DiagnosticsHelper.getEndOffset(unit, range);
		InnovationContext context = new InnovationContext(unit, start, end - start);
		context.setASTRoot(astRoot);
		return context;
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

	private static boolean containsKind(List<String> codeActionKinds, String baseKind) {
		return codeActionKinds.stream().anyMatch(kind -> kind.startsWith(baseKind));
	}

	public static class CodeActionData {
		private final Object proposal;
		private final int priority;

		public CodeActionData(Object proposal) {
			this.proposal = proposal;
			this.priority = CodeActionComparator.LOWEST_PRIORITY;
		}

		public CodeActionData(Object proposal, int priority) {
			this.proposal = proposal;
			this.priority = priority;
		}

		public Object getProposal() {
			return proposal;
		}

		public int getPriority() {
			return priority;
		}
	}

}
