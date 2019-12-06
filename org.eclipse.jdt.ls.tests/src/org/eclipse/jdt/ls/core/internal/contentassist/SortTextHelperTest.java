/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class SortTextHelperTest {

	@Test
	public void testConvertRelevance() throws Exception {
		List<String> result = new ArrayList<>();
		int i=0;
		for (; i < 1000; i++) {
			String relevance = SortTextHelper.convertRelevance(i);
			//System.err.println(relevance);
			if (i > 0) {
				int prev = i-1;
				String previous = result.get(prev);
				assertTrue("relevance "+i +" should be sorted before "+prev+" : "+relevance +" vs "+ previous, relevance.compareTo(previous) < 0);
			}
			result.add(relevance);
		}

		//Try some boundaries
		String min = SortTextHelper.convertRelevance(Integer.MIN_VALUE);
		String zero = SortTextHelper.convertRelevance(0);
		assertEquals(zero, min);//negative relevance is irrelevant

		i = SortTextHelper.MAX_RELEVANCE_VALUE;
		String max = SortTextHelper.convertRelevance(i);
		assertEquals("900000000", max);

		try {
			SortTextHelper.convertRelevance(Integer.MAX_VALUE);
			fail("Values greater than "+ SortTextHelper.MAX_RELEVANCE_VALUE+ " are not supported");
		} catch(IllegalArgumentException e) {
			//this is expected
		}
	}

}
