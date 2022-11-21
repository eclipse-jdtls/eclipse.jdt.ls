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

import java.util.Map;

/**
 * The ranking result of each completion proposal.
 * <p>
 * Note: Following APIs are in experimental stage which means they might be changed
 * in the future.
 * </p>
 */
public class CompletionRanking {

	/**
	 * The allowed max score.
	 */
	public static final int MAX_SCORE = 100;

	/**
	 * The allowed min score.
	 */
	public static final int MIN_SCORE = 0;

	/**
	 * The key in the completion data which is used to store the completion time.
	 */
	public static final String COMPLETION_EXECUTION_TIME = "COMPLETION_EXECUTION_TIME";
	/**
	 * The score of the completion proposal. Score will be added to the proposal's relevance field.
	 */
	private int score;

	/**
	 * A decorator that can be used as a prefix of the completion item label. Providers can use this
	 * field to highlight the completion items on demand.
	 */
	private char decorator;

	/**
	 * A map data structure that will be appended to completion item's data field. When a completion
	 * item is selected, the selected item will be passed to the provider. Providers can use the stored
	 * data to do post process on demand.
	 */
	private Map<String, String> data;

	/**
	 * The score of the completion proposal. Score will be added to the proposal's relevance field.
	 * <p>
	 * The acceptable values are [{@link #MIN_SCORE}, {@link #MAX_SCORE}]. When adding to the relevance, values larger than
	 * {@link #MAX_SCORE} will be treated as {@link #MAX_SCORE}.Values lower than {@link #MIN_SCORE} will be treated as
	 * {@link #MIN_SCORE}.
	 */
	public int getScore() {
		return score;
	}

	/**
	 * The score of the completion proposal. Score will be added to the proposal's relevance field.
	 * <p>
	 * The acceptable values are [{@link #MIN_SCORE}, {@link #MAX_SCORE}]. When adding to the relevance, values larger than
	 * {@link #MAX_SCORE} will be treated as {@link #MAX_SCORE}.Values lower than {@link #MIN_SCORE} will be treated as
	 * {@link #MIN_SCORE}.
	 */
	public void setScore(int score) {
		this.score = score;
	}

	/**
	 * A decorator that can be used as a prefix of the completion item label. Providers can use this
	 * field to highlight the completion items on demand.
	 */
	public char getDecorator() {
		return decorator;
	}

	/**
	 * A decorator that can be used as a prefix of the completion item label. Providers can use this
	 * field to highlight the completion items on demand.
	 */
	public void setDecorator(char decorator) {
		this.decorator = decorator;
	}

	/**
	 * A map data structure that will be appended to completion item's data field. When a completion
	 * item is selected, the selected item will be passed to the provider. Providers can use the stored
	 * data to do post process on demand.
	 */
	public Map<String, String> getData() {
		return data;
	}

	/**
	 * A map data structure that will be appended to completion item's data field. When a completion
	 * item is selected, the selected item will be passed to the provider. Providers can use the stored
	 * data to do post process on demand.
	 * <p>
	 * The key <code>"COMPLETION_EXECUTION_TIME"</code> is preserved to store the time calculating all the
	 * completion items at the server side in millisecond.
	 * <p>
	 * <strong>Note</strong>: only <code>java.lang.String</code> type is allowed for both keys and values.
	 */
	public void setData(Map<String, String> data) {
		this.data = data;
	}
}
