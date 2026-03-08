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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link SearchEngine#getSearchParticipants()} provides the
 * participants used by jdtls search call sites.
 */
public class SearchParticipantsTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testGetSearchParticipantsIncludesDefault() {
		SearchParticipant[] participants = SearchEngine.getSearchParticipants();
		assertNotNull(participants);
		assertTrue(participants.length >= 1, "Should contain at least the default participant");
		// First participant should be the default Java search participant
		assertNotNull(participants[0]);
	}

	@Test
	public void testGetSearchParticipantsDefaultIsJavaParticipant() {
		SearchParticipant[] participants = SearchEngine.getSearchParticipants();
		SearchParticipant defaultParticipant = SearchEngine.getDefaultSearchParticipant();
		// Both should be the same type (JavaSearchParticipant)
		assertEquals(defaultParticipant.getClass(), participants[0].getClass(),
				"First participant should be the default Java search participant");
	}

	@Test
	public void testGetSearchParticipantsConsistentResults() {
		SearchParticipant[] first = SearchEngine.getSearchParticipants();
		SearchParticipant[] second = SearchEngine.getSearchParticipants();
		assertEquals(first.length, second.length,
				"Consecutive calls should return same number of participants");
	}

	@Test
	public void testDefaultParticipantGetCompilationUnitReturnsNull() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		// Use an existing Java file as IFile — the default participant should still
		// return null from getCompilationUnit() since it does not implement the method
		IFile javaFile = project.getFile("src/java/Foo.java");
		assertTrue(javaFile.exists(), "Test file should exist");
		SearchParticipant defaultParticipant = SearchEngine.getDefaultSearchParticipant();
		ICompilationUnit cu = defaultParticipant.getCompilationUnit(javaFile);
		assertNull(cu, "Default search participant should return null from getCompilationUnit()");
	}

	@Test
	public void testResolveCompilationUnitNonJavaFileReturnsNull() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		// A non-Java file in a Java project — exercises the participant fallback loop
		IFile nonJavaFile = project.getFile("src/java/Foo.kt");
		// File doesn't exist on disk, but resolveCompilationUnit(IFile) handles
		// non-existent files gracefully — the important thing is the fallback runs
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(nonJavaFile);
		assertNull(cu, "Non-Java file with no contributing participant should resolve to null");
	}

	@Test
	public void testResolveCompilationUnitJavaFileStillWorks() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		IFile javaFile = project.getFile("src/java/Foo.java");
		assertTrue(javaFile.exists(), "Test file should exist");
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(javaFile);
		assertNotNull(cu, "Java file should still resolve via the normal path");
	}
}
