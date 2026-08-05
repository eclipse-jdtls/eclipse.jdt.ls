/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.decompiler.FernFlowerDecompiler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.TextDocumentContentParams;
import org.eclipse.lsp4j.TextDocumentContentResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WorkspaceTextDocumentContentTest extends AbstractProjectsManagerBasedTest {

	private JDTLanguageServer server;
	private IProject project;

	@BeforeEach
	public void setup() throws Exception {
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		server = new JDTLanguageServer(projectsManager, preferenceManager);
	}

	@AfterEach
	public void teardown() throws Exception {
		if (server != null) {
			server.shutdown();
		}
	}

	@Test
	public void testTextDocumentContent() throws Exception {
		URI uri = JDTUtils.toURI(ClassFileUtil.getURI(project, "org.apache.commons.lang3.text.WordUtils"));

		TextDocumentContentParams params = new TextDocumentContentParams();
		params.setUri(uri.toString());

		TextDocumentContentResult result = server.textDocumentContent(params).get();

		assertNotNull(result);
		assertNotNull(result.getText());
		assertTrue(result.getText().contains("Operations on Strings that contain words."), "unexpected body content: " + result.getText());
	}

	@Test
	public void testTextDocumentContentDecompile() throws Exception {
		URI uri = JDTUtils.toURI(ClassFileUtil.getURI(project, "java.math.BigDecimal"));

		TextDocumentContentParams params = new TextDocumentContentParams();
		params.setUri(uri.toString());

		TextDocumentContentResult result = server.textDocumentContent(params).get();

		assertNotNull(result);
		assertNotNull(result.getText());
		assertTrue(result.getText().startsWith(FernFlowerDecompiler.DECOMPILER_HEADER), "disassembler header missing from " + result.getText());
		assertTrue(result.getText().contains("class BigDecimal"), "unexpected body content: " + result.getText());
	}

	@Test
	public void testTextDocumentContentMissingFile() throws Exception {
		URI uri = JDTUtils.toURI("file:///this/is/Missing.class");

		TextDocumentContentParams params = new TextDocumentContentParams();
		params.setUri(uri.toString());

		TextDocumentContentResult result = server.textDocumentContent(params).get();

		assertNotNull(result);
		assertNotNull(result.getText());
		assertTrue(result.getText().isEmpty(), "expected empty content, got: " + result.getText());
	}
}
