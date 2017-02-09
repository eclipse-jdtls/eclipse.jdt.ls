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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.CancellableProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalRequestor;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class CompletionHandler{

	CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(TextDocumentPositionParams position){
		return CompletableFutures.computeAsync(cancelChecker->{
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(position.getTextDocument().getUri());
			List<CompletionItem> completionItems = this.computeContentAssist(unit,
					position.getPosition().getLine(),
					position.getPosition().getCharacter(), new CancellableProgressMonitor(cancelChecker));
			CompletionList $ = new CompletionList();
			$.setItems(completionItems);
			JavaLanguageServerPlugin.logInfo("Completion request completed");
			return Either.forRight($);
		});
	}

	private List<CompletionItem> computeContentAssist(ICompilationUnit unit, int line, int column, IProgressMonitor monitor) {
		CompletionResponses.clear();
		if (unit == null) {
			return Collections.emptyList();
		}
		List<CompletionItem> proposals = new ArrayList<>();
		try {
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

			if (offset >-1 && !monitor.isCanceled()) {
				unit.codeComplete(offset, collector, monitor);
				proposals.addAll(collector.getCompletionItems());
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem with codeComplete for " +  unit.getElementName(), e);
		}
		return proposals;
	}
}
