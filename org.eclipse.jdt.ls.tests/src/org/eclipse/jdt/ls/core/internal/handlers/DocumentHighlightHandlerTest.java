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

	@Before
	public void setup() throws Exception {
		importProjects("gradle/simple-gradle");
		project = WorkspaceHelper.getProject("simple-gradle");
		handler = new DocumentHighlightHandler();
	}

	@Test
	public void testDocumentHighlightHandler() throws Exception {
		String uri = ClassFileUtil.getURI(project, "LibraryTest");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		TextDocumentPositionParams params = new TextDocumentPositionParams(identifier, new Position(11, 17));
		List<? extends DocumentHighlight> highlights = handler.documentHighlight(params, monitor);
		assertEquals(2, highlights.size());

		DocumentHighlight write = highlights.get(0);
		assertEquals(DocumentHighlightKind.Write, write.getKind());
		Lsp4jAssertions.assertRange(11, 16, 30, write.getRange());
		DocumentHighlight read = highlights.get(1);
		assertEquals(DocumentHighlightKind.Read, read.getKind());
		Lsp4jAssertions.assertRange(12, 61, 75, read.getRange());
	}

}
