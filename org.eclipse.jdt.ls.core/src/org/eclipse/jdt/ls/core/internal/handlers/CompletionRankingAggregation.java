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

import static org.eclipse.jdt.ls.core.contentassist.CompletionRanking.MAX_SCORE;
import static org.eclipse.jdt.ls.core.contentassist.CompletionRanking.MIN_SCORE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.ls.core.contentassist.CompletionRanking;

/**
 * Aggregated result of all the ranking results from ranking providers.
 */
public class CompletionRankingAggregation {
	private int score;

	private Set<Character> decorators;

	private Map<String, String> data;

	public CompletionRankingAggregation() {
		this.score = 0;
		this.decorators = new HashSet<>();
		this.data = new HashMap<>();
	}

	/**
	 * Get the final score aggregated from all ranking providers.
	 */
	public int getScore() {
		return score;
	}

	/**
	 * Add score from a ranking provider. The acceptable values are [{@link CompletionRanking#MIN_SCORE},
	 * {@link CompletionRanking#MAX_SCORE}]. When adding to the relevance, values larger than {@link CompletionRanking#MAX_SCORE}
	 * will be treated as {@link CompletionRanking#MAX_SCORE} and value lower than {@link CompletionRanking#MIN_SCORE}
	 * will be treated as {@link CompletionRanking#MIN_SCORE}.
	 * @param score score from a ranking provider.
	 */
	public void addScore(int score) {
		if (score <= MIN_SCORE) {
			return;
		}

		this.score += score > MAX_SCORE ? MAX_SCORE : score;
	}

	/**
	 * Get the aggregated decorators from all ranking providers.
	 * The decorator chars will be de-duplicated and sorted.
	 */
	public String getDecorators() {
		return this.decorators.stream().sorted().map(String::valueOf).collect(Collectors.joining());
	}

	/**
	 * Add decorator from a ranking provider.
	 * @param decorator decorator from a ranking provider.
	 */
	public void addDecorator(char decorator) {
		if (decorator != 0) {
			this.decorators.add(decorator);
		}
	}

	/**
	 * Get the aggregated data from all ranking providers.
	 */
	public Map<String, String> getData() {
		return data;
	}

	/**
	 * Add data from a ranking provider.
	 * @param data data from a ranking provider.
	 */
	public void addData(Map<String, String> data) {
		if (data != null) {
			for (String key : data.keySet()) {
				this.data.put(key, data.get(key));
			}
		}
	}
}
