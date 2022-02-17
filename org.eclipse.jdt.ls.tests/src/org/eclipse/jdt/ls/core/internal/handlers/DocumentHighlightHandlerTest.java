/*******************************************************************************
 * Copyright (c) 2017-2022 Remy Suen and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Remy Suen - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.Lsp4jAssertions;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Before;
import org.junit.Test;

public class DocumentHighlightHandlerTest extends AbstractProjectsManagerBasedTest {

	private IProject project;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
	}

	@Test
	public void testDocumentHighlight_ExceptionOccurences() throws JavaModelException {
		List<DocumentHighlight> result = requestHighlights("org.sample.Highlight", 8, 34);
		Iterator<DocumentHighlight> it = result.iterator();

		assertEquals(2, result.size());
		assertHighlight(it.next(), 8, 31, 42, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 10, 3, 8, DocumentHighlightKind.Read);
	}

	@Test
	public void testDocumentHighlight_MethodExits() throws JavaModelException {
		List<DocumentHighlight> result = requestHighlights("org.sample.Highlight", 8, 11);
		Iterator<DocumentHighlight> it = result.iterator();

		assertEquals(4, result.size());
		assertHighlight(it.next(), 8, 8, 14, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 10, 3, 8, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 14, 3, 8, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 25, 2, 21, DocumentHighlightKind.Read);
	}

	@Test
	public void testDocumentHighlight_BreakContinueTarget() throws JavaModelException {
		List<DocumentHighlight> result = requestHighlights("org.sample.Highlight", 19, 7);
		Iterator<DocumentHighlight> it = result.iterator();

		assertEquals(2, result.size());
		assertHighlight(it.next(), 16, 2, 6, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 23, 2, 3, DocumentHighlightKind.Read);
	}

	@Test
	public void testDocumentHighlight_ImplementOccurrences() throws JavaModelException {
		List<DocumentHighlight> result = requestHighlights("org.sample.Highlight", 4, 38);
		Iterator<DocumentHighlight> it = result.iterator();

		assertEquals(3, result.size());
		assertHighlight(it.next(), 4, 34, 46, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 34, 13, 16, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 39, 13, 16, DocumentHighlightKind.Read);
	}

	@Test
	public void testDocumentHighlight_Occurrences() throws JavaModelException {
		List<DocumentHighlight> result = requestHighlights("org.sample.Highlight", 6, 18);
		Iterator<DocumentHighlight> it = result.iterator();

		assertEquals(9, result.size());
		assertHighlight(it.next(), 6, 16, 19, DocumentHighlightKind.Write);
		assertHighlight(it.next(), 9, 6, 9, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 12, 2, 5, DocumentHighlightKind.Write);
		assertHighlight(it.next(), 13, 6, 9, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 16, 16, 19, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 18, 8, 11, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 24, 2, 5, DocumentHighlightKind.Write);
		assertHighlight(it.next(), 24, 22, 25, DocumentHighlightKind.Read);
		assertHighlight(it.next(), 25, 9, 12, DocumentHighlightKind.Read);
	}

	private List<DocumentHighlight> requestHighlights(String compilationUnit, int line, int character) throws JavaModelException {
		String uri = ClassFileUtil.getURI(project, compilationUnit);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		TextDocumentPositionParams params = new TextDocumentPositionParams(identifier, new Position(line, character));
		List<DocumentHighlight> highlights = DocumentHighlightHandler.documentHighlight(params, monitor);
		// Sorting the highlights to make testing easier
		highlights.sort(Comparator.comparingInt(this::getStartLine).thenComparingInt(this::getStartCharacter));
		return highlights;
	}

	private int getStartLine(DocumentHighlight highlight) {
		return highlight.getRange().getStart().getLine();
	}

	private int getStartCharacter(DocumentHighlight highlight) {
		return highlight.getRange().getStart().getCharacter();
	}

	private void assertHighlight(DocumentHighlight highlight, int expectedLine, int expectedStart, int expectedEnd, DocumentHighlightKind expectedKind) {
		Lsp4jAssertions.assertRange(expectedLine, expectedStart, expectedEnd, highlight.getRange());
		assertEquals(expectedKind, highlight.getKind());
	}

}
