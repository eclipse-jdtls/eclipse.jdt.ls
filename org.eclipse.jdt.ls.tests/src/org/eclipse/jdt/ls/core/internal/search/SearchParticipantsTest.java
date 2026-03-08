/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Arcadiy Ivanov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.ls.core.internal.FakeSearchParticipant;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.jupiter.api.Test;

public class SearchParticipantsTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testGetSearchParticipantsIncludesDefault() {
		SearchParticipant[] participants = SearchParticipants.getSearchParticipants();
		assertNotNull(participants);
		assertTrue(participants.length >= 1, "Should contain at least the default participant");
		// First participant should be the default Java search participant
		assertNotNull(participants[0]);
	}

	@Test
	public void testGetSearchParticipantsIncludesContributed() {
		SearchParticipant[] participants = SearchParticipants.getSearchParticipants();
		assertNotNull(participants);
		// Should have default + 2 fake participants registered in test plugin.xml
		assertTrue(participants.length >= 3,
				"Expected at least 3 participants (default + 2 contributed), got " + participants.length);
		List<SearchParticipant> contributed = Arrays.asList(participants).subList(1, participants.length);
		long fakeCount = contributed.stream()
				.filter(p -> p instanceof FakeSearchParticipant)
				.count();
		assertEquals(2, fakeCount, "Expected exactly 2 FakeSearchParticipant instances");
	}

	@Test
	public void testGetSearchParticipantsIsCached() {
		// First call ensures participants are loaded (may already be cached from other tests)
		SearchParticipant[] first = SearchParticipants.getSearchParticipants();
		// Record the instance count after first call
		int countAfterFirst = FakeSearchParticipant.instanceCount.get();
		// Second call should return cached participants without creating new instances
		SearchParticipant[] second = SearchParticipants.getSearchParticipants();
		assertEquals(countAfterFirst, FakeSearchParticipant.instanceCount.get(),
				"Second call should not create new instances (cache should be used)");
		assertEquals(first.length, second.length, "Both calls should return same number of participants");
		// The contributed participants (index 1+) should be the same cached instances
		for (int i = 1; i < first.length; i++) {
			assertTrue(first[i] == second[i],
					"Contributed participant at index " + i + " should be the same cached instance");
		}
	}
}
