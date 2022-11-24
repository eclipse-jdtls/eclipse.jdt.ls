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

/**
 * Service that is used to manage completion contributions.
 * <p>
 * Note: Following APIs are in experimental stage which means they might be changed
 * in the future.
 * </p>
 */
public interface ICompletionContributionService {
	/**
	 * Register a completion ranking provider.
	 */
	void registerRankingProvider(ICompletionRankingProvider provider);

	/**
	 * Unregister a completion ranking provider.
	 */
	void unregisterRankingProvider(ICompletionRankingProvider provider);
}
