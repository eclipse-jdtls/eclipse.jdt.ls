/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.contentassist;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalRequestor;
import org.eclipse.lsp4j.CompletionItem;

/**
 * Interface that can be implemented to participate into the completion processing.
 * <p>
 * Note: Following APIs are in experimental stage which means they might be changed
 * in the future.
 * </p>
 */
public interface ICompletionRankingProvider {

	/**
	 * Providers can provide ranking results as an array for the proposal list.
	 * <p>
	 * The method will be invoked before parsing completion proposals to completion items.
	 * <p>
	 * To register the provider to JDT.LS, call {@code JavaLanguageServerPlugin.getCompletionContributionService().registerRankingProvider(...)}
	 *
	 * @param proposals The completion proposals accepted by {@link CompletionProposalRequestor}.
	 * @param context The completion context accepted by {@link CompletionProposalRequestor}.
	 * @param unit The compilation unit where the completion happens.
	 * @param monitor The progress monitor.
	 * 
	 * @return A {@link CompletionProposalRequestor} array. Each element of the array represents the ranking result
	 * of the completion proposal in the same order of the input proposal list. If the provider does not want to add
	 * any additional data for a proposal, set the corresponding element in the array as null.
	 * <p>
	 * <strong>Note:</strong> If the length of the array is not equal to the input proposal list. The entire returned
	 * array will be ignored.
	 */
	CompletionRanking[] rank(List<CompletionProposal> proposals, CompletionContext context, ICompilationUnit unit, IProgressMonitor monitor);

	/**
	 * This method will be invoked when the completion item is selected.
	 * Providers can use this method to do some post process on demand.
	 * 
	 * @param item The selected completion item.
	 */
	void onDidCompletionItemSelect(CompletionItem item);
}
