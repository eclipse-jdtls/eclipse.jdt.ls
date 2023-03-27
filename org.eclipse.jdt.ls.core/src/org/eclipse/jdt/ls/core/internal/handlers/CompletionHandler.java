/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.ProgressMonitorWrapper;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.ls.core.contentassist.CompletionRanking;
import org.eclipse.jdt.ls.core.contentassist.ICompletionRankingProvider;
import org.eclipse.jdt.ls.core.internal.ExceptionFactory;
import org.eclipse.jdt.ls.core.internal.JDTEnvironmentUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalRequestor;
import org.eclipse.jdt.ls.core.internal.contentassist.JavadocCompletionProposal;
import org.eclipse.jdt.ls.core.internal.contentassist.SnippetCompletionProposal;
import org.eclipse.jdt.ls.core.internal.contentassist.SortTextHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.syntaxserver.ModelBasedCompletionEngine;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.google.common.collect.Sets;

public class CompletionHandler{

	public final static CompletionOptions DEFAULT_COMPLETION_OPTIONS = new CompletionOptions(Boolean.TRUE, Arrays.asList(".", "@", "#", "*", " "));
	private static final Set<String> UNSUPPORTED_RESOURCES = Sets.newHashSet("module-info.java", "package-info.java");

	static final Comparator<CompletionItem> PROPOSAL_COMPARATOR = new Comparator<>() {

		private final String DEFAULT_SORT_TEXT = String.valueOf(SortTextHelper.MAX_RELEVANCE_VALUE);

		@Override
		public int compare(CompletionItem o1, CompletionItem o2) {
			return getSortText(o1).compareTo(getSortText(o2));
		}

		private String getSortText(CompletionItem ci) {
			return StringUtils.defaultString(ci.getSortText(), DEFAULT_SORT_TEXT);
		}

	};

	private PreferenceManager manager;

	public CompletionHandler(PreferenceManager manager) {
		this.manager = manager;
	}

	public Either<List<CompletionItem>, CompletionList> completion(CompletionParams params,
			IProgressMonitor monitor) {
		long startTime = System.currentTimeMillis();
		CompletionList $ = null;
		try {
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
			$ = this.computeContentAssist(unit, params, monitor);
		} catch (OperationCanceledException ignorable) {
			// No need to pollute logs when query is cancelled
			monitor.setCanceled(true);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Problem with codeComplete for " +  params.getTextDocument().getUri(), e);
			monitor.setCanceled(true);
		}
		if ($ == null) {
			$ = new CompletionList();
		}
		if ($.getItems() == null) {
			$.setItems(Collections.emptyList());
		}
		if (monitor.isCanceled()) {
			$.setIsIncomplete(true);
			JavaLanguageServerPlugin.logInfo("Completion request cancelled");
		} else {
			JavaLanguageServerPlugin.logInfo("Completion request completed");
		}
		long executionTime = System.currentTimeMillis() - startTime;
		for (CompletionItem item : $.getItems()) {
			String requestId = "";
			String proposalId = "";
			Map<String, String> data = (Map<String, String>) item.getData();
			if (data != null) {
				requestId = data.getOrDefault(CompletionResolveHandler.DATA_FIELD_REQUEST_ID, "");
				proposalId = data.getOrDefault(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID, "");
				data.put(CompletionRanking.COMPLETION_EXECUTION_TIME, String.valueOf(executionTime));
			}
			item.setCommand(new Command("", "java.completion.onDidSelect", Arrays.asList(
					requestId,
					proposalId
			)));
		}
		return Either.forRight($);
	}

	public void onDidCompletionItemSelect(String requestId, String proposalId) throws CoreException {
		triggerSignatureHelp();
		if (proposalId.isEmpty() || requestId.isEmpty()) {
			return;
		}
		int pId = Integer.parseInt(proposalId);
		long rId = Long.parseLong(requestId);
		CompletionResponse completionResponse = CompletionResponses.get(rId);
		if (completionResponse == null || completionResponse.getItems().size() <= pId) {
			throw ExceptionFactory.newException("Cannot get completion responses.");
		}

		CompletionItem item = completionResponse.getItems().get(pId);
		if (item == null) {
			throw ExceptionFactory.newException("Cannot get the completion item.");
		}

		List<ICompletionRankingProvider> providers =
				((CompletionContributionService) JavaLanguageServerPlugin.getCompletionContributionService()).getRankingProviders();
		for (ICompletionRankingProvider provider : providers) {
			provider.onDidCompletionItemSelect(item);
		}
	}

	private void triggerSignatureHelp() {
		if (manager.getPreferences().isSignatureHelpEnabled()) {
			String onSelectedCommand = manager.getClientPreferences().getCompletionItemCommand();
			if (!onSelectedCommand.isEmpty()) {
				JavaLanguageServerPlugin.getInstance().getClientConnection()
						.executeClientCommand(onSelectedCommand);
			}
		}
	}

	private CompletionList computeContentAssist(ICompilationUnit unit, CompletionParams params, IProgressMonitor monitor) throws JavaModelException {
		CompletionResponses.clear();
		if (unit == null) {
			return null;
		}

		boolean completionForConstructor = false;
		if (params.getContext() != null && " ".equals(params.getContext().getTriggerCharacter())) {
			completionForConstructor = isCompletionForConstructor(params, unit, monitor);
			if (!completionForConstructor) {
				return null;
			}
		}

		List<CompletionItem> proposals = new ArrayList<>();

		final int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), params.getPosition().getLine(), params.getPosition().getCharacter());
		CompletionProposalRequestor collector = new CompletionProposalRequestor(unit, offset, manager);
		// Allow completions for unresolved types - since 3.3
		collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.TYPE_REF, true);
		collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.TYPE_IMPORT, true);
		collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.FIELD_IMPORT, true);

		collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.TYPE_REF, true);
		collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.TYPE_IMPORT, true);
		collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.METHOD_IMPORT, true);

		collector.setAllowsRequiredProposals(CompletionProposal.CONSTRUCTOR_INVOCATION, CompletionProposal.TYPE_REF, true);

		collector.setAllowsRequiredProposals(CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION, CompletionProposal.TYPE_REF, true);
		collector.setAllowsRequiredProposals(CompletionProposal.ANONYMOUS_CLASS_DECLARATION, CompletionProposal.TYPE_REF, true);

		collector.setAllowsRequiredProposals(CompletionProposal.TYPE_REF, CompletionProposal.TYPE_REF, true);
		collector.setFavoriteReferences(getFavoriteStaticMembers());

		if (offset >-1 && !monitor.isCanceled()) {
			IBuffer buffer = unit.getBuffer();
			if (buffer != null && buffer.getLength() >= offset) {
				IProgressMonitor subMonitor = new ProgressMonitorWrapper(monitor) {
					private long timeLimit;
					private final long TIMEOUT = Long.getLong("completion.timeout", 5000);

					@Override
					public void beginTask(String name, int totalWork) {
						timeLimit = System.currentTimeMillis() + TIMEOUT;
					}

					@Override
					public boolean isCanceled() {
						return super.isCanceled() || timeLimit <= System.currentTimeMillis();
					}

				};
				try {
					if (isIndexEngineEnabled()) {
						unit.codeComplete(offset, collector, subMonitor);
					} else {
						ModelBasedCompletionEngine.codeComplete(unit, offset, collector, DefaultWorkingCopyOwner.PRIMARY, subMonitor);
					}
					proposals.addAll(collector.getCompletionItems());
					if (isSnippetStringSupported() && !UNSUPPORTED_RESOURCES.contains(unit.getResource().getName())) {
						proposals.addAll(SnippetCompletionProposal.getSnippets(unit, collector.getContext(), subMonitor));
					}
					proposals.addAll(new JavadocCompletionProposal().getProposals(unit, offset, collector, subMonitor));
				} catch (OperationCanceledException e) {
					monitor.setCanceled(true);
				}
			}
		}
		proposals.sort(PROPOSAL_COMPARATOR);
		CompletionList list = new CompletionList(proposals);
		list.setIsIncomplete(!collector.isComplete() || completionForConstructor);
		if (isCompletionListItemDefaultsSupport()){
			list.setItemDefaults(collector.getCompletionItemDefaults());
		}
		return list;
	}

	private String[] getFavoriteStaticMembers() {
		PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferenceManager != null) {
			return preferenceManager.getPreferences().getJavaCompletionFavoriteMembers();
		}
		return new String[0];
	}

	private boolean isSnippetStringSupported() {
		return this.manager != null &&  this.manager.getClientPreferences() != null
				&& this.manager.getClientPreferences().isCompletionSnippetsSupported();
	}

	/**
	 * Check whether the completion is triggered for constructors: "new |"
	 * @param params completion parameters
	 * @param unit completion unit
	 * @param monitor progress monitor
	 * @throws JavaModelException
	 */
	private boolean isCompletionForConstructor(CompletionParams params, ICompilationUnit unit, IProgressMonitor monitor) throws JavaModelException {
		Position pos = params.getPosition();
		int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), pos.getLine(), pos.getCharacter());
		if (offset < 4) {
			return false;
		}
		String content = unit.getSource();
		if (content == null) {
			return false;
		}
		String triggerWord = content.substring(offset - 4, offset);
		if (!"new ".equals(triggerWord)) {
			return false;
		}

		CompilationUnit root = SharedASTProviderCore.getAST(unit, SharedASTProviderCore.WAIT_ACTIVE_ONLY, monitor);
		if (root == null || monitor.isCanceled()) {
			return false;
		}

		ASTNode node = NodeFinder.perform(root, offset - 4, 0);
		if (node instanceof StringLiteral || node instanceof SimpleName) {
			return false;
		}

		return true;
	}

	public boolean isIndexEngineEnabled() {
		return !JDTEnvironmentUtils.isSyntaxServer();
	}

	private boolean isCompletionListItemDefaultsSupport() {
		return this.manager != null && this.manager.getClientPreferences() != null
				&& (this.manager.getClientPreferences().isCompletionListItemDefaultsEditRangeSupport() || this.manager.getClientPreferences().isCompletionListItemDefaultsInsertTextFormatSupport());
	}
}
