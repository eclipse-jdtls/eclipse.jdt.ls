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

import org.eclipse.jdt.ls.core.contentassist.ICompletionRankingProvider;
import org.eclipse.jdt.ls.core.contentassist.ICompletionContributionService;

public class CompletionContributionService implements ICompletionContributionService {

	private List<ICompletionRankingProvider> providers;


	public CompletionContributionService() {
		this.providers = new LinkedList<>();
	}

	public List<ICompletionRankingProvider> getRankingProviders() {
		return this.providers;
	}

	@Override
	public void registerRankingProvider(ICompletionRankingProvider provider) {
		if (provider == null) {
			return;
		}
		for (ICompletionRankingProvider p : this.providers) {
			if (p.equals(provider)) {
				return;
			}
		}
		this.providers.add(provider);
	}

	@Override
	public void unregisterRankingProvider(ICompletionRankingProvider provider) {
		if (provider == null) {
			return;
		}
		this.providers.removeIf(p -> p.equals(provider));
	}
}
