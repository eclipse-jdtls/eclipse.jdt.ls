/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

public class FoldingRangeHandlerTest extends AbstractProjectsManagerBasedTest {

	private IProject project;

	@Before
	public void setup() throws Exception {
		importProjects(Arrays.asList("maven/foldingRange"));
		project = WorkspaceHelper.getProject("foldingRange");
	}

	@Test
	public void testFoldingRanges() throws Exception {
		List<FoldingRange> foldingRanges = getFoldingRanges("org.apache.commons.lang3.text.WordUtils");
		assertHasFoldingRange(18, 23, FoldingRangeKind.Imports, foldingRanges);
		testClassForValidRange("org.apache.commons.lang3.text.WordUtils", foldingRanges);
	}

	@Test
	public void testTypes() throws Exception {
		String className = "org.sample.SimpleFoldingRange";
		List<FoldingRange> foldingRanges = getFoldingRanges(className);
		assertTrue(foldingRanges.size() == 7);
		assertHasFoldingRange(2, 3, FoldingRangeKind.Imports, foldingRanges);
		assertHasFoldingRange(5, 7, FoldingRangeKind.Comment, foldingRanges);
		assertHasFoldingRange(8, 24, null, foldingRanges);
		assertHasFoldingRange(10, 14, FoldingRangeKind.Comment, foldingRanges);
		assertHasFoldingRange(19, 23, null, foldingRanges);
		assertHasFoldingRange(20, 22, null, foldingRanges);
	}

	@Test
	public void testErrorTypes() throws Exception {
		String className = "org.sample.UnmatchFoldingRange";
		List<FoldingRange> foldingRanges = getFoldingRanges(className);
		assertTrue(foldingRanges.size() == 3);
		assertHasFoldingRange(2, 12, null, foldingRanges);
		assertHasFoldingRange(3, 10, null, foldingRanges);
		assertHasFoldingRange(5, 7, null, foldingRanges);
	}

	@Test
	public void testInvalidInput() throws Exception {
		String className = "org.sample.InvalidInputRange";
		List<FoldingRange> foldingRanges = getFoldingRanges(className);
		assertTrue(foldingRanges.size() == 3);
		assertHasFoldingRange(2, 4, "comment", foldingRanges);
		assertHasFoldingRange(5, 10, null, foldingRanges);
		assertHasFoldingRange(7, 9, null, foldingRanges);
	}

	@Test
	public void testRegionFoldingRanges() throws Exception {
		String className = "org.sample.RegionFoldingRange";
		List<FoldingRange> foldingRanges = getFoldingRanges(className);
		assertTrue(foldingRanges.size() == 7);
		assertHasFoldingRange(7, 15, FoldingRangeKind.Region, foldingRanges);
		assertHasFoldingRange(17, 23, FoldingRangeKind.Region, foldingRanges);
		assertHasFoldingRange(18, 20, FoldingRangeKind.Region, foldingRanges);
	}

	@Test
	public void testStatementFoldingRanges() throws Exception {
		String className = "org.sample.StatementFoldingRange";
		List<FoldingRange> foldingRanges = getFoldingRanges(className);
		assertTrue(foldingRanges.size() == 18);
		assertHasFoldingRange(2, 4, FoldingRangeKind.Comment, foldingRanges);
		assertHasFoldingRange(5, 53, null, foldingRanges);
		assertHasFoldingRange(7, 52, null, foldingRanges);

		// First switch statement
		assertHasFoldingRange(10, 23, null, foldingRanges);
		assertHasFoldingRange(11, 18, null, foldingRanges);
		assertHasFoldingRange(19, 20, null, foldingRanges);
		assertHasFoldingRange(21, 22, null, foldingRanges);

		// Try catch:
		assertHasFoldingRange(12, 13, null, foldingRanges);
		assertHasFoldingRange(14, 16, null, foldingRanges);

		// If statement:
		assertHasFoldingRange(26, 27, null, foldingRanges);
		assertHasFoldingRange(28, 29, null, foldingRanges);
		assertHasFoldingRange(30, 32, null, foldingRanges);

		// Second switch statement:
		assertHasFoldingRange(36, 51, null, foldingRanges);
		assertHasFoldingRange(37, 40, null, foldingRanges);
		assertHasFoldingRange(41, 47, null, foldingRanges);
		assertHasFoldingRange(48, 50, null, foldingRanges);
	}

	@Test
	public void testStaticBlockFoldingRange() throws Exception {
		String className = "org.sample.StaticBlockFoldingRange";
		List<FoldingRange> foldingRanges = getFoldingRanges(className);
		assertTrue(foldingRanges.size() == 5);
		assertHasFoldingRange(2, 18, null, foldingRanges);
		assertHasFoldingRange(4, 5, null, foldingRanges);
		assertHasFoldingRange(7, 12, null, foldingRanges);
		assertHasFoldingRange(14, 15, null, foldingRanges);
		assertHasFoldingRange(17, 17, null, foldingRanges);
	}

	private void testClassForValidRange(String className, List<FoldingRange> foldingRanges) throws CoreException {
		for (FoldingRange range : foldingRanges) {
			assertTrue("Class: " + className + ", FoldingRange:" + range.getKind() + " - invalid location.", isValid(range));
		}
	}

	private List<FoldingRange> getFoldingRanges(String className) throws CoreException {
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		FoldingRangeRequestParams params = new FoldingRangeRequestParams();
		params.setTextDocument(identifier);
		return new FoldingRangeHandler().foldingRange(params, monitor);
	}

	private boolean isValid(FoldingRange range) {
		return range != null && range.getStartLine() <= range.getEndLine();
	}

	private void assertHasFoldingRange(int startLine, int endLine, String expectedKind, Collection<FoldingRange> foldingRanges) {
		Optional<FoldingRange> range = foldingRanges.stream().filter(s -> s.getStartLine() == startLine && s.getEndLine() == endLine).findFirst();
		assertTrue("Expected type" + expectedKind, range.get().getKind() == expectedKind);
	}
}