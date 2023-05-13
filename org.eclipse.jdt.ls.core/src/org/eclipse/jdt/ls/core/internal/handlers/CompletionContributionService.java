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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.ls.core.contentassist.ICompletionContributionService;
import org.eclipse.jdt.ls.core.contentassist.ICompletionProposalProvider;
import org.eclipse.jdt.ls.core.contentassist.ICompletionRankingProvider;

public class CompletionContributionService implements ICompletionContributionService {

	private List<ICompletionRankingProvider> rankingProviders;

	private List<ICompletionProposalProvider> proposalProviders;

	public CompletionContributionService() {
		this.rankingProviders = new LinkedList<>();
		this.proposalProviders = new LinkedList<>();
	}

	public List<ICompletionRankingProvider> getRankingProviders() {
		return this.rankingProviders;
	}

	@Override
	public void registerRankingProvider(ICompletionRankingProvider provider) {
		if (provider == null) {
			return;
		}
		for (ICompletionRankingProvider p : this.rankingProviders) {
			if (p.equals(provider)) {
				return;
			}
		}
		this.rankingProviders.add(provider);
	}

	@Override
	public void unregisterRankingProvider(ICompletionRankingProvider provider) {
		if (provider == null) {
			return;
		}
		this.rankingProviders.removeIf(p -> p.equals(provider));
	}

	public List<ICompletionProposalProvider> getProposalProviders() {
		return proposalProviders;
	}

	@Override
	public void registerProposalProvider(ICompletionProposalProvider provider) {
		if (provider == null) {
			return;
		}
		for (ICompletionProposalProvider p : this.proposalProviders) {
			if (p.equals(provider)) {
				return;
			}
		}
		this.proposalProviders.add(provider);
	}

	@Override
	public void unregisterProposalProvider(ICompletionProposalProvider provider) {
		if (provider == null) {
			return;
		}
		this.proposalProviders.removeIf(p -> p.equals(provider));
	}
}
