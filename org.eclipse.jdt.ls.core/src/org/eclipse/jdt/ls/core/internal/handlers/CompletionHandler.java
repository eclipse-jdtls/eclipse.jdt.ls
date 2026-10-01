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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.eclipse.jdt.ls.core.internal.JDTEnvironmentUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.ChainCompletionProposalComputer;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalRequestor;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalUtils;
import org.eclipse.jdt.ls.core.internal.contentassist.JavadocCompletionProposal;
import org.eclipse.jdt.ls.core.internal.contentassist.SnippetCompletionProposal;
import org.eclipse.jdt.ls.core.internal.contentassist.SortTextHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.syntaxserver.ModelBasedCompletionEngine;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemOptions;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.Position;


public class CompletionHandler implements ICompletionHandler {

	public final static CompletionOptions getDefaultCompletionOptions(PreferenceManager preferenceManager) {
		CompletionOptions completionOptions = new CompletionOptions(Boolean.TRUE, List.of(".", "@", "#", "*", " "));
		if (preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()) {
			CompletionItemOptions completionItemOptions = new CompletionItemOptions();
			completionItemOptions.setLabelDetailsSupport(Boolean.TRUE);
			completionOptions.setCompletionItem(completionItemOptions);
		}
		return completionOptions;
	}

	private static final Set<String> UNSUPPORTED_RESOURCES = Set.of("module-info.java", "package-info.java");

	static final Comparator<CompletionItem> PROPOSAL_COMPARATOR = new Comparator<>() {

		private final String DEFAULT_SORT_TEXT = String.valueOf(SortTextHelper.MAX_RELEVANCE_VALUE);

		@Override
		public int compare(CompletionItem o1, CompletionItem o2) {
			return getSortText(o1).compareTo(getSortText(o2));
		}

		private String getSortText(CompletionItem ci) {
			return Objects.toString(ci.getSortText(), DEFAULT_SORT_TEXT);
		}

	};

	static final Comparator<CompletionItem> LABEL_COMPARATOR = new Comparator<>() {

		@Override
		public int compare(CompletionItem o1, CompletionItem o2) {
			return o1.getLabel().compareTo(o2.getLabel());
		}

	};

	private PreferenceManager manager;

	public CompletionHandler(PreferenceManager manager) {
		this.manager = manager;
	}

	@Override
	public CompletionList completion(CompletionParams params,
			IProgressMonitor monitor) {
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
		return $;
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

		CompletionProposalUtils.addStaticImportsAsFavoriteImports(unit);
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
					// chain completions are added into collector while computing, so we need me compute before adding completion items to proposals.
					if (manager.getPreferences().isChainCompletionEnabled() && params.getContext().getTriggerKind() != CompletionTriggerKind.TriggerCharacter) {
						ChainCompletionProposalComputer chain = new ChainCompletionProposalComputer(unit, collector, this.isSnippetStringSupported());
						chain.computeCompletionProposals();
					}
					proposals.addAll(collector.getCompletionItems());
					if (isSnippetStringSupported() && !UNSUPPORTED_RESOURCES.contains(unit.getResource().getName())) {
						proposals.addAll(SnippetCompletionProposal.getSnippets(unit, collector, subMonitor));
					}
					proposals.addAll(new JavadocCompletionProposal().getProposals(unit, offset, collector, subMonitor));
				} catch (OperationCanceledException e) {
					monitor.setCanceled(true);
				}
			}
		}
		// When at least one snippet has the same label as a keyword, we raise the relevance of all matching snippets above that keyword
		List<CompletionItem> tempProposals = proposals.stream().filter(prop -> prop.getKind() == CompletionItemKind.Keyword || prop.getKind() == CompletionItemKind.Snippet).collect(Collectors.toList());
		tempProposals.sort(LABEL_COMPARATOR);
		int newSortText = SortTextHelper.CEILING;
		for (int i = 0; i < tempProposals.size() - 1; i++) {
			CompletionItem currentItem = tempProposals.get(i);
			CompletionItem nextItem = tempProposals.get(i + 1);
			if (currentItem.getLabel().equals(nextItem.getLabel())) {
				int tempSortText;
				if (currentItem.getKind() == CompletionItemKind.Keyword) {
					tempSortText = Integer.parseInt(currentItem.getSortText()) - 1;
				} else {
					tempSortText = Integer.parseInt(nextItem.getSortText()) - 1;
				}
				if (tempSortText < newSortText) {
					newSortText = tempSortText;
				}
			}
		}
		if (newSortText != -1) {
			String finalSortText = Integer.toString(newSortText);
			tempProposals.stream().filter(prop -> prop.getKind() == CompletionItemKind.Snippet).forEach(p -> p.setSortText(finalSortText));
		}
		proposals.sort(PROPOSAL_COMPARATOR);
		CompletionList list = new CompletionList(proposals);
		list.setIsIncomplete(!collector.isComplete() || completionForConstructor);
		if (this.manager.getClientPreferences().isCompletionListItemDefaultsSupport()){
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
}
