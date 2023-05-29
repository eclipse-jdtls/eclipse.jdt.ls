/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class NavigateToDefinitionHandlerTest extends AbstractProjectsManagerBasedTest {

	private NavigateToDefinitionHandler handler;
	private IProject project;
	private IProject defaultProject;

	@Before
	public void setUp() throws Exception {
		handler = new NavigateToDefinitionHandler(preferenceManager);
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		defaultProject = linkFilesToDefaultProject("singlefile/Single.java").getProject();
	}

	@Test
	public void testGetEmptyDefinition() throws Exception {
		List<? extends Location> definitions = handler.definition(
				new TextDocumentPositionParams(new TextDocumentIdentifier("/foo/bar"), new Position(1, 1)), monitor);
		assertNotNull(definitions);
		assertEquals(0, definitions.size());
	}

	@Test
	public void testAttachedSource() throws Exception {
		testClass("org.apache.commons.lang3.StringUtils", 20, 26);
	}

	@Test
	public void testNoClassContentSupport() throws Exception {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		String uri = ClassFileUtil.getURI(project, "org.apache.commons.lang3.StringUtils");
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(false);
		List<? extends Location> definitions = handler.definition(new TextDocumentPositionParams(new TextDocumentIdentifier(uri), new Position(20, 26)), monitor);
		assertNotNull(definitions);
		assertEquals(0, definitions.size());
	}

	@Test
	public void testDisassembledSource() throws Exception {
		testClass("javax.tools.Tool", 6, 57);
	}

	@Test
	public void testSourceVersion() throws Exception {
		String className = "javax.tools.Tool";
		int line = 6;
		int column = 57;
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> definitions = handler.definition(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(definitions);
		assertEquals("No definition found for " + className, 1, definitions.size());
		assertNotNull(definitions.get(0).getUri());
		assertEquals(3, definitions.get(0).getRange().getStart().getLine());
		assertEquals(12, definitions.get(0).getRange().getStart().getCharacter());
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1813
	@Test
	public void testMethodInAnonymousClass() throws Exception {
		String className = "org.sample.App";
		int line = 12;
		int column = 28;
		importProjects("eclipse/hello");
		IProject hello = WorkspaceHelper.getProject("hello");
		String uri = ClassFileUtil.getURI(hello, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> definitions = handler.definition(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(definitions);
		assertEquals("No definition found for " + className, 1, definitions.size());
		assertNotNull(definitions.get(0).getUri());
		assertEquals(3, definitions.get(0).getRange().getStart().getLine());
		assertEquals(18, definitions.get(0).getRange().getStart().getCharacter());
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1813
	@Test
	public void testMethodInAnonymousClass2() throws Exception {
		String className = "org.sample.App2";
		int line = 10;
		int column = 24;
		importProjects("eclipse/java11");
		IProject java11 = WorkspaceHelper.getProject("java11");
		String uri = ClassFileUtil.getURI(java11, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> definitions = handler.definition(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(definitions);
		assertEquals("No definition found for " + className, 1, definitions.size());
		assertNotNull(definitions.get(0).getUri());
		assertEquals(7, definitions.get(0).getRange().getStart().getLine());
		assertEquals(10, definitions.get(0).getRange().getStart().getCharacter());
	}

	@Test
	public void testJdkClasses() throws Exception {
		// because for test, we are using fake rt.jar(rtstubs.jar), the issue of issue https://bugs.eclipse.org/bugs/show_bug.cgi?id=541573 will
		// never occur in test cases
		String uri = ClassFileUtil.getURI(defaultProject, "Single");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		handler.definition(new TextDocumentPositionParams(identifier, new Position(1, 31)), monitor);
		testClass("org.apache.commons.lang3.stringutils", 6579, 20);
	}

	// this test should pass when starting with -javaagent:<lombok_jar> (-javagent:~/.m2/repository/org/projectlombok/lombok/1.18.28/lombok-1.18.28.jar)
	// https://github.com/redhat-developer/vscode-java/issues/2805
	@Test
	public void testLombok() throws Exception {
		boolean lombokDisabled = "true".equals(System.getProperty("jdt.ls.lombok.disabled"));
		if (lombokDisabled) {
			return;
		}
		importProjects("maven/mavenlombok");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("mavenlombok");
		List<IMarker> markers = ResourceUtils.getErrorMarkers(project);
		if (!markers.isEmpty()) {
			// there isn't the lombok agent
			return;
		}
		String uri = ClassFileUtil.getURI(project, "org.sample.Main");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> locations = handler.definition(new TextDocumentPositionParams(identifier, new Position(5, 20)), monitor);
		assertNotNull(locations);
		assertEquals(1, locations.size());
		assertEquals(6, locations.get(0).getRange().getStart().getLine());
		assertEquals(6, locations.get(0).getRange().getEnd().getLine());
		assertEquals(19, locations.get(0).getRange().getStart().getCharacter());
		assertEquals(23, locations.get(0).getRange().getEnd().getCharacter());
		assertNotNull(locations.get(0).getUri());
		assertTrue(locations.get(0).getUri().endsWith("org/sample/Test.java"));
	}

	@Test
	public void testBreakContinue() throws JavaModelException {
		String uri = ClassFileUtil.getURI(project, "org.sample.TestBreakContinue");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		// continue
		List<? extends Location> definitions = handler.definition(new TextDocumentPositionParams(identifier, new Position(11, 5)), monitor);
		assertNotNull(definitions);
		assertEquals(1, definitions.size());
		Location definition = definitions.get(0);
		assertEquals(8, definition.getRange().getStart().getLine());
		assertEquals(3, definition.getRange().getStart().getCharacter());
		// outer continue
		definitions = handler.definition(new TextDocumentPositionParams(identifier, new Position(14, 5)), monitor);
		assertNotNull(definitions);
		assertEquals(1, definitions.size());
		definition = definitions.get(0);
		assertEquals(6, definition.getRange().getStart().getLine());
		assertEquals(2, definition.getRange().getStart().getCharacter());
		// break
		definitions = handler.definition(new TextDocumentPositionParams(identifier, new Position(17, 5)), monitor);
		assertNotNull(definitions);
		assertEquals(1, definitions.size());
		definition = definitions.get(0);
		assertEquals(8, definition.getRange().getStart().getLine());
		assertEquals(3, definition.getRange().getStart().getCharacter());
		// outer break
		definitions = handler.definition(new TextDocumentPositionParams(identifier, new Position(20, 5)), monitor);
		assertNotNull(definitions);
		assertEquals(1, definitions.size());
		definition = definitions.get(0);
		assertEquals(6, definition.getRange().getStart().getLine());
		assertEquals(2, definition.getRange().getStart().getCharacter());
	}

	private void testClass(String className, int line, int column) throws JavaModelException {
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> definitions = handler
				.definition(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(definitions);
		assertEquals("No definition found for " + className, 1, definitions.size());
		assertNotNull(definitions.get(0).getUri());
		assertTrue(definitions.get(0).getRange().getStart().getLine() >= 0);
	}

}
