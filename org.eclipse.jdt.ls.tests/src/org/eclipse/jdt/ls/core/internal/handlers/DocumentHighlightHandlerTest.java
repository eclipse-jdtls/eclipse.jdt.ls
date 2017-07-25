/*******************************************************************************
 * Copyright (c) 2017 Remy Suen and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Remy Suen - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.core.resources.IProject;
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
	private DocumentHighlightHandler handler;

	private void assertHighlight(DocumentHighlight highlight, int expectedLine, int expectedStart, int expectedEnd, DocumentHighlightKind expectedKind) {
		Lsp4jAssertions.assertRange(expectedLine, expectedStart, expectedEnd, highlight.getRange());
		assertEquals(expectedKind, highlight.getKind());
	}

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		handler = new DocumentHighlightHandler();
	}

	@Test
	public void testDocumentHighlightHandler() throws Exception {
		String uri = ClassFileUtil.getURI(project, "org.sample.Highlight");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		TextDocumentPositionParams params = new TextDocumentPositionParams(identifier, new Position(5, 10));

		List<? extends DocumentHighlight> highlights = handler.documentHighlight(params, monitor);
		assertEquals(4, highlights.size());
		assertHighlight(highlights.get(0), 5, 9, 15, DocumentHighlightKind.Write);
		assertHighlight(highlights.get(1), 6, 2, 8, DocumentHighlightKind.Read);
		assertHighlight(highlights.get(2), 7, 2, 8, DocumentHighlightKind.Write);
		assertHighlight(highlights.get(3), 8, 2, 8, DocumentHighlightKind.Read);
	}

}
