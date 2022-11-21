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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.ls.core.contentassist.CompletionRanking;
import org.junit.Test;

public class CompletionRankingAggregationTest {
	@Test
	public void testAddNullData() {
		CompletionRankingAggregation aggregation = new CompletionRankingAggregation();
		aggregation.addData(null);
		Map<String, String> data = aggregation.getData();
		assertTrue(data.isEmpty());
	}

	@Test
	public void testAddData() {
		CompletionRankingAggregation aggregation = new CompletionRankingAggregation();
		Map<String, String> data = new HashMap<>();
		data.put("foo", "bar");
		aggregation.addData(data);
		Map<String, String> aggregatedData = aggregation.getData();
		assertEquals("bar", aggregatedData.get("foo"));
	}

	@Test
	public void testAddDecorator() {
		CompletionRankingAggregation aggregation = new CompletionRankingAggregation();
		aggregation.addDecorator('★');
		aggregation.addDecorator('a');
		assertEquals("a★", aggregation.getDecorators());
	}

	@Test
	public void testAddScore() {
		CompletionRankingAggregation aggregation = new CompletionRankingAggregation();
		aggregation.addScore(-1);
		aggregation.addScore(Integer.MAX_VALUE);
		assertEquals(CompletionRanking.MAX_SCORE, aggregation.getScore());
	}
}
