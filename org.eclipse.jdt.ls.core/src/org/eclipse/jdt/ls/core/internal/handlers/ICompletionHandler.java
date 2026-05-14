/*******************************************************************************
 * Copyright (c) 2026 Simeon Andreev and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Simeon Andreev - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.contentassist.ICompletionRankingProvider;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;

/**
 * @author Simeon Andreev
 *
 */
public interface ICompletionHandler {

	CompletionList completion(CompletionParams params, IProgressMonitor monitor);

	default void onDidCompletionItemSelect(CompletionItem item) throws CoreException {
		List<ICompletionRankingProvider> providers = ((CompletionContributionService) JavaLanguageServerPlugin.getCompletionContributionService()).getRankingProviders();
		for (ICompletionRankingProvider provider : providers) {
			provider.onDidCompletionItemSelect(item);
		}
	}

}
