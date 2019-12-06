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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.FindLinksHandler.LinkLocation;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Before;
import org.junit.Test;

public class FindLinksHandlerTest extends AbstractProjectsManagerBasedTest {
	private IPackageFragmentRoot sourceFolder;

	@Before
	public void setup() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		ClientPreferences clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);
	}

	@Test
	public void testFindSuperMethod() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		ICompilationUnit unitA = pack1.createCompilationUnit("A.java", "package test1;\n" +
				"\n" +
				"public class A {\n" +
				"	public void run() {\n" +
				"	}\n" +
				"}", true, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit unitB = pack1.createCompilationUnit("B.java", "package test1;\n" +
				"\n" +
				"public class B extends A {\n" +
				"	public void run() {\n" +
				"	}\n" +
				"}", true, null);
		//@formatter:on

		String uri = JDTUtils.toURI(unitB);
		List<? extends Location> response = FindLinksHandler.findLinks("superImplementation", new TextDocumentPositionParams(new TextDocumentIdentifier(uri), new Position(3, 14)), new NullProgressMonitor());
		assertTrue(response != null && response.size() == 1);
		LinkLocation location = (LinkLocation) response.get(0);
		assertEquals("test1.A.run", location.displayName);
		assertEquals("method", location.kind);
		assertEquals(JDTUtils.toURI(unitA), location.getUri());
		Range range = location.getRange();
		assertEquals(3, range.getStart().getLine());
		assertEquals(13, range.getStart().getCharacter());
		assertEquals(3, range.getEnd().getLine());
		assertEquals(16, range.getEnd().getCharacter());
	}

	@Test
	public void testFindNearestSuperMethod() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		ICompilationUnit unitA = pack1.createCompilationUnit("A.java", "package test1;\n" +
				"\n" +
				"public class A {\n" +
				"	public void run() {\n" +
				"	}\n" +
				"}", true, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit unitB = pack1.createCompilationUnit("B.java", "package test1;\n" +
				"\n" +
				"public class B extends A {\n" +
				"}", true, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit unitC = pack1.createCompilationUnit("C.java", "package test1;\n" +
		"\n" +
		"public class C extends B {\n" +
		"	public void run() {\n" +
		"	}\n" +
		"}", true, null);
		//@formatter:on

		String uri = JDTUtils.toURI(unitC);
		List<? extends Location> response = FindLinksHandler.findLinks("superImplementation", new TextDocumentPositionParams(new TextDocumentIdentifier(uri), new Position(3, 14)), new NullProgressMonitor());
		assertTrue(response != null && response.size() == 1);
		LinkLocation location = (LinkLocation) response.get(0);
		assertEquals("test1.A.run", location.displayName);
		assertEquals("method", location.kind);
		assertEquals(JDTUtils.toURI(unitA), location.getUri());
		Range range = location.getRange();
		assertEquals(3, range.getStart().getLine());
		assertEquals(13, range.getStart().getCharacter());
		assertEquals(3, range.getEnd().getLine());
		assertEquals(16, range.getEnd().getCharacter());
	}

	@Test
	public void testNoSuperMethod() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		ICompilationUnit unitA = pack1.createCompilationUnit("A.java", "package test1;\n" +
				"\n" +
				"public class A {\n" +
				"	public void run() {\n" +
				"	}\n" +
				"}", true, null);
		//@formatter:on

		String uri = JDTUtils.toURI(unitA);
		List<? extends Location> response = FindLinksHandler.findLinks("superImplementation", new TextDocumentPositionParams(new TextDocumentIdentifier(uri), new Position(3, 14)), new NullProgressMonitor());
		assertTrue(response == null || response.isEmpty());
	}
}