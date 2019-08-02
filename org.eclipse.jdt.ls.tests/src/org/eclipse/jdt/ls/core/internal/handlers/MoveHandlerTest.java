/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.RefactorWorkspaceEdit;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveDestinationsParams;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveDestinationsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveParams;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.PackageNode;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

public class MoveHandlerTest extends AbstractProjectsManagerBasedTest {
	private IPackageFragmentRoot sourceFolder;

	@Before
	public void setup() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		ClientPreferences clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);
	}

	@Test
	public void testGetPackageDestinations() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("jdtls.test1", false, null);
		//@formatter:off
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", "package jdtls.test1;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"}", true, null);
		//@formatter:on
		MoveDestinationsParams params = new MoveDestinationsParams("package", new String[] { JDTUtils.toURI(unit) });
		MoveDestinationsResponse response = MoveHandler.getMoveDestinations(params);
		assertNotNull(response);
		assertNotNull(response.destinations);
		assertEquals(3, response.destinations.length);
		assertTrue(((PackageNode) response.destinations[0]).isDefaultPackage);
		assertEquals("jdtls", ((PackageNode) response.destinations[1]).displayName);
		assertEquals("jdtls.test1", ((PackageNode) response.destinations[2]).displayName);
		assertTrue(((PackageNode) response.destinations[2]).isParentOfSelectedFile);
	}

	@Test
	public void testMoveFile() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("jdtls.test1", false, null);
		//@formatter:off
		ICompilationUnit unitA = pack1.createCompilationUnit("A.java", "package jdtls.test1;\r\n" +
				"import jdtls.test2.B;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private B b = new B();\r\n" +
				"}", true, null);
		//@formatter:on

		IPackageFragment pack2 = sourceFolder.createPackageFragment("jdtls.test2", false, null);
		//@formatter:off
		ICompilationUnit unitB = pack2.createCompilationUnit("B.java", "package jdtls.test2;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}", true, null);
		//@formatter:on

		IPackageFragment pack3 = sourceFolder.createPackageFragment("jdtls.test3", false, null);
		String packageUri = JDTUtils.getFileURI(pack3.getResource());
		RefactorWorkspaceEdit refactorEdit = MoveHandler.move(new MoveParams("moveResource", new String[] { JDTUtils.toURI(unitB) }, packageUri, true), new NullProgressMonitor());
		assertNotNull(refactorEdit);
		assertNotNull(refactorEdit.edit);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = refactorEdit.edit.getDocumentChanges();
		assertEquals(4, changes.size());

		//@formatter:off
		String expected = "package jdtls.test1;\r\n" +
				"import jdtls.test3.B;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private B b = new B();\r\n" +
				"}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(unitA.getSource(), textEdit.getEdits()));

		//@formatter:off
		expected = "package jdtls.test3;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}";
		//@formatter:on
		textEdit = changes.get(1).getLeft();
		assertNotNull(textEdit);
		List<TextEdit> edits = new ArrayList<>(textEdit.getEdits());
		textEdit = changes.get(2).getLeft();
		assertNotNull(textEdit);
		edits.addAll(textEdit.getEdits());
		assertEquals(expected, TextEditUtil.apply(unitB.getSource(), edits));

		RenameFile renameFile = (RenameFile) changes.get(3).getRight();
		assertNotNull(renameFile);
		assertEquals(JDTUtils.toURI(unitB), renameFile.getOldUri());
		assertEquals(ResourceUtils.fixURI(unitB.getResource().getRawLocationURI()).replace("test2", "test3"), renameFile.getNewUri());
	}

	@Test
	public void testMoveMultiFiles() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("jdtls.test1", false, null);
		//@formatter:off
		ICompilationUnit unitA = pack1.createCompilationUnit("A.java", "package jdtls.test1;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private B b = new B();\r\n" +
				"}", true, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit unitB = pack1.createCompilationUnit("B.java", "package jdtls.test1;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}", true, null);
		//@formatter:on

		IPackageFragment pack2 = sourceFolder.createPackageFragment("jdtls.test2", false, null);
		String packageUri = JDTUtils.getFileURI(pack2.getResource());
		RefactorWorkspaceEdit refactorEdit = MoveHandler.move(new MoveParams("moveResource", new String[] { JDTUtils.toURI(unitA), JDTUtils.toURI(unitB) }, packageUri, true), new NullProgressMonitor());
		assertNotNull(refactorEdit);
		assertNotNull(refactorEdit.edit);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = refactorEdit.edit.getDocumentChanges();
		assertEquals(6, changes.size());

		//@formatter:off
		String expected = "package jdtls.test2;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private B b = new B();\r\n" +
				"}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		List<TextEdit> edits = new ArrayList<>(textEdit.getEdits());
		textEdit = changes.get(4).getLeft();
		assertNotNull(textEdit);
		edits.addAll(textEdit.getEdits());
		assertEquals(expected, TextEditUtil.apply(unitA.getSource(), edits));

		//@formatter:off
		expected = "package jdtls.test2;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}";
		//@formatter:on
		textEdit = changes.get(1).getLeft();
		assertNotNull(textEdit);
		edits = new ArrayList<>(textEdit.getEdits());
		textEdit = changes.get(2).getLeft();
		assertNotNull(textEdit);
		edits.addAll(textEdit.getEdits());
		assertEquals(expected, TextEditUtil.apply(unitB.getSource(), edits));

		RenameFile renameFileB = (RenameFile) changes.get(3).getRight();
		assertNotNull(renameFileB);
		assertEquals(JDTUtils.toURI(unitB), renameFileB.getOldUri());
		assertEquals(ResourceUtils.fixURI(unitB.getResource().getRawLocationURI()).replace("test1", "test2"), renameFileB.getNewUri());

		RenameFile renameFileA = (RenameFile) changes.get(5).getRight();
		assertNotNull(renameFileA);
		assertEquals(JDTUtils.toURI(unitA), renameFileA.getOldUri());
		assertEquals(ResourceUtils.fixURI(unitA.getResource().getRawLocationURI()).replace("test1", "test2"), renameFileA.getNewUri());
	}
}
