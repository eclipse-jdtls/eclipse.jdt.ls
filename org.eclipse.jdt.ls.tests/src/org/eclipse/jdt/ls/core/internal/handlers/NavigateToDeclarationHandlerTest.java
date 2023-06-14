/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others. All rights reserved. This program
 * and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors: Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Before;
import org.junit.Test;

public class NavigateToDeclarationHandlerTest extends AbstractProjectsManagerBasedTest {

	private NavigateToDeclarationHandler handler;
	private IProject project;

	@Before
	public void setUp() throws Exception {
		handler = new NavigateToDeclarationHandler(preferenceManager);
	}

	@Test
	public void testGetEmptyDeclaration() throws Exception {
		List<? extends Location> declarations = handler.declaration(new TextDocumentPositionParams(new TextDocumentIdentifier("/foo/bar"), new Position(1, 1)), monitor);
		assertNotNull(declarations);
		assertEquals(0, declarations.size());
	}

	@Test
	public void testGetMethodDeclarationSameFile() throws Exception {
		importProjects("eclipse/declaration-test");
		project = WorkspaceHelper.getProject("declaration-test");
		String className = "TestSame";
		int line = 1;
		int column = 20;
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> declarations = handler.declaration(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(declarations);
		assertNotNull(declarations.get(0).getUri());
		assertEquals(9, declarations.get(0).getRange().getStart().getLine());
		assertEquals(16, declarations.get(0).getRange().getStart().getCharacter());
	}

	@Test
	public void testGetMethodDeclaration() throws Exception {
		importProjects("eclipse/declaration-test");
		project = WorkspaceHelper.getProject("declaration-test");
		String className = "Car";
		int line = 4;
		int column = 23;
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> declarations = handler.declaration(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(declarations);
		assertNotNull(declarations.get(0).getUri());
		assertEquals(1, declarations.get(0).getRange().getStart().getLine());
		assertEquals(15, declarations.get(0).getRange().getStart().getCharacter());
	}


	@Test
	public void testGetFieldDeclaration() throws Exception {
		importProjects("eclipse/declaration-test");
		project = WorkspaceHelper.getProject("declaration-test");
		String className = "Car";
		int line = 3;
		int column = 15;
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> declarations = handler.declaration(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(declarations);
		assertEquals(0, declarations.size());
	}

	@Test
	public void testCustomPackage() throws Exception {
		importProjects("eclipse/declaration-test");
		project = WorkspaceHelper.getProject("declaration-test");
		String className = "PackageTwo.Foo2";
		int line = 9;
		int column = 14;
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> declarations = handler.declaration(new TextDocumentPositionParams(identifier, new Position(line, column)), monitor);
		assertNotNull(declarations);
		assertNotNull(declarations.get(0).getUri());
		assertEquals(9, declarations.get(0).getRange().getStart().getLine());
		assertEquals(9, declarations.get(0).getRange().getStart().getCharacter());
	}

}