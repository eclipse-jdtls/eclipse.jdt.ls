
/*******************************************************************************
* Copyright (c) 2018 Microsoft Corporation and others.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Before;
import org.junit.Test;

public class NavigateToTypeDefinitionHandlerTest extends AbstractProjectsManagerBasedTest {

	private NavigateToTypeDefinitionHandler handler;
	private IProject project;

	@Before
	public void setUp() throws Exception {
		handler = new NavigateToTypeDefinitionHandler();
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
	}

	@Test
	public void testGetEmptyDefinition() throws Exception {
		List<? extends Location> definitions = handler.typeDefinition(
				new TextDocumentPositionParams(new TextDocumentIdentifier("/foo/bar"), new Position(1, 1)), monitor);
		assertNull(definitions);
	}

	@Test
	public void testAttachedSource() throws Exception {
		testClass("org.apache.commons.lang3.StringUtils", 20, 26);
	}

	@Test
	public void testLocalVariable() throws Exception {
		testClass("java.Foo3", 18, 24);
	}

	@Test
	public void testDisassembledSource() throws JavaModelException {
		String className = "javax.tools.Tool";
		int line = 6;
		int column = 57;
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> definitions = handler.typeDefinition(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(definitions);
		assertEquals("No definition found for " + className, 1, definitions.size());
		assertNotNull(definitions.get(0).getUri());
		assertEquals(3, definitions.get(0).getRange().getStart().getLine());
		assertEquals(12, definitions.get(0).getRange().getStart().getCharacter());
	}

	@Test
	public void testClassField() throws Exception {
		testClass("java.Foo3", 17, 30);
	}

	@Test
	public void testExternalClassField() throws Exception {
		testClass("java.Foo3", 17, 11);
	}

	@Test
	public void testNoClassContentSupport() throws Exception {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		String uri = ClassFileUtil.getURI(project, "org.apache.commons.lang3.StringUtils");
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(false);
		List<? extends Location> definitions = handler.typeDefinition(new TextDocumentPositionParams(new TextDocumentIdentifier(uri), new Position(20, 26)), monitor);
		assertNull(definitions);
	}

	private void testClass(String className, int line, int column) throws JavaModelException {
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> definitions = handler
				.typeDefinition(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(definitions);
		assertEquals("No definition found for " + className, 1, definitions.size());
		assertNotNull(definitions.get(0).getUri());
		assertTrue(definitions.get(0).getRange().getStart().getLine() >= 0);
	}
}
