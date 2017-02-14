/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

		i = Integer.MAX_VALUE;
		String max = SortTextHelper.convertRelevance(i);
		assertTrue(max +" should come before "+ zero, max.compareTo(zero) < 0);
		assertTrue("relevance "+i +" should be sorted before 0 : "+ max +" vs "+ zero, max.compareTo(zero) < 0);

	}

}
