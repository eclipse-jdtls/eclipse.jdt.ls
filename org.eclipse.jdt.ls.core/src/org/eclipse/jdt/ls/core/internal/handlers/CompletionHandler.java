/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.ProgressMonitorWrapper;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalRequestor;
import org.eclipse.jdt.ls.core.internal.contentassist.JavadocCompletionProposal;
import org.eclipse.jdt.ls.core.internal.contentassist.SnippetCompletionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class CompletionHandler{

	public final static CompletionOptions DEFAULT_COMPLETION_OPTIONS = new CompletionOptions(Boolean.TRUE, Arrays.asList(".", "@", "#", "*"));

	Either<List<CompletionItem>, CompletionList> completion(CompletionParams position,
			IProgressMonitor monitor) {
		List<CompletionItem> completionItems = null;
		try {
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(position.getTextDocument().getUri());
			completionItems = this.computeContentAssist(unit,
					position.getPosition().getLine(),
					position.getPosition().getCharacter(), monitor);
		} catch (OperationCanceledException ignorable) {
			// No need to pollute logs when query is cancelled
			monitor.setCanceled(true);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Problem with codeComplete for " +  position.getTextDocument().getUri(), e);
			monitor.setCanceled(true);
		}
		CompletionList $ = new CompletionList();
		if (monitor.isCanceled()) {
			$.setIsIncomplete(true);
			completionItems = null;
			JavaLanguageServerPlugin.logInfo("Completion request cancelled");
		} else {
			JavaLanguageServerPlugin.logInfo("Completion request completed");
		}
		$.setItems(completionItems == null ? Collections.emptyList() : completionItems);
		return Either.forRight($);
	}

	private List<CompletionItem> computeContentAssist(ICompilationUnit unit, int line, int column, IProgressMonitor monitor) throws JavaModelException {
		CompletionResponses.clear();
		if (unit == null) {
			return Collections.emptyList();
		}
		List<CompletionItem> proposals = new ArrayList<>();

		final int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), line, column);
		CompletionProposalRequestor collector = new CompletionProposalRequestor(unit, offset);
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
					private static final long TIMEOUT = 5000;

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
					unit.codeComplete(offset, collector, subMonitor);
					proposals.addAll(collector.getCompletionItems());
					proposals.addAll(SnippetCompletionProposal.getSnippets(unit, collector.getContext(), subMonitor));
					proposals.addAll(new JavadocCompletionProposal().getProposals(unit, offset, collector, subMonitor));
				} catch (OperationCanceledException e) {
					monitor.setCanceled(true);
				}
			}
		}
		return proposals;
	}

	private String[] getFavoriteStaticMembers() {
		PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferenceManager != null) {
			return preferenceManager.getPreferences().getJavaCompletionFavoriteMembers();
		}
		return new String[0];
	}
}
