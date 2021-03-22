/*******************************************************************************
* Copyright (c) 2020 Microsoft Corporation and others.
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileEventHandlerTest extends AbstractProjectsManagerBasedTest {
	private ClientPreferences clientPreferences;
	private IPackageFragmentRoot sourceFolder;
	private IJavaProject javaProject;

	@Before
	public void setup() throws Exception {
		javaProject = newEmptyProject();
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		clientPreferences = preferenceManager.getClientPreferences();
	}

	@After
	public void tearDown() throws Exception {
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
	}

	@Test
	public void testRenameFiles() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder builderA = new StringBuilder();
		builderA.append("package test1;\n");
		builderA.append("public class ObjectA {\n");
		builderA.append("	public void foo() {\n");
		builderA.append("	}\n");
		builderA.append("}\n");
		ICompilationUnit cuA = pack1.createCompilationUnit("ObjectA.java", builderA.toString(), false, null);

		StringBuilder builderB = new StringBuilder();
		builderB.append("package test1;\n");
		builderB.append("public class B {\n");
		builderB.append("	public void foo() {\n");
		builderB.append("		ObjectA a = new ObjectA();\n");
		builderB.append("		a.foo();\n");
		builderB.append("	}\n");
		builderB.append("}\n");
		ICompilationUnit cuB = pack1.createCompilationUnit("B.java", builderB.toString(), false, null);

		String uriA = JDTUtils.toURI(cuA);
		String newUriA = uriA.replace("ObjectA", "ObjectA1");
		WorkspaceEdit edit = FileEventHandler.handleWillRenameFiles(new RenameFilesParams(Arrays.asList(new FileRename(uriA, newUriA))), new NullProgressMonitor());
		assertNotNull(edit);
		assertEquals(2, edit.getDocumentChanges().size());

		assertTrue(edit.getDocumentChanges().get(0).isLeft());
		assertEquals(edit.getDocumentChanges().get(0).getLeft().getTextDocument().getUri(), JDTUtils.toURI(cuB));
		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getDocumentChanges().get(0).getLeft().getEdits()),
				"package test1;\n" +
				"public class B {\n" +
				"	public void foo() {\n" +
				"		ObjectA1 a = new ObjectA1();\n" +
				"		a.foo();\n" +
				"	}\n" +
				"}\n"
				);

		assertTrue(edit.getDocumentChanges().get(1).isLeft());
		assertEquals(edit.getDocumentChanges().get(1).getLeft().getTextDocument().getUri(), uriA);
		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getDocumentChanges().get(1).getLeft().getEdits()),
				"package test1;\n" +
				"public class ObjectA1 {\n" +
				"	public void foo() {\n" +
				"	}\n" +
				"}\n"
				);
	}

	@Test
	public void testRenameFiles_refactoringExists() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder builderA = new StringBuilder();
		builderA.append("package test1;\n");
		builderA.append("public class ObjectA1 {\n");
		builderA.append("	public void foo() {\n");
		builderA.append("	}\n");
		builderA.append("}\n");
		ICompilationUnit cuA = pack1.createCompilationUnit("ObjectA.java", builderA.toString(), false, null);

		String uriA = JDTUtils.toURI(cuA);
		String newUriA = uriA.replace("ObjectA", "ObjectA1");
		WorkspaceEdit edit = FileEventHandler.handleWillRenameFiles(new RenameFilesParams(Arrays.asList(new FileRename(uriA, newUriA))), new NullProgressMonitor());
		assertNull(edit);
	}

	// Test renaming package from "parent.pack2" to "parent.newpack2"
	@Test
	public void testRenamePackage() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("parent.pack1", false, null);
		IPackageFragment pack2 = sourceFolder.createPackageFragment("parent.pack2", false, null);

		StringBuilder codeA = new StringBuilder();
		codeA.append("package parent.pack1;\n");
		codeA.append("import parent.pack2.B;\n");
		codeA.append("public class A {\n");
		codeA.append("	public void foo() {\n");
		codeA.append("		B b = new B();\n");
		codeA.append("		b.foo();\n");
		codeA.append("	}\n");
		codeA.append("}\n");

		StringBuilder codeB = new StringBuilder();
		codeB.append("package parent.pack2;\n");
		codeB.append("public class B {\n");
		codeB.append("	public B() {}\n");
		codeB.append("	public void foo() {}\n");
		codeB.append("}\n");

		ICompilationUnit cuA = pack1.createCompilationUnit("A.java", codeA.toString(), false, null);
		ICompilationUnit cuB = pack2.createCompilationUnit("B.java", codeB.toString(), false, null);

		String pack2Uri = JDTUtils.getFileURI(pack2.getResource());
		String newPack2Uri = pack2Uri.replace("pack2", "newpack2");
		WorkspaceEdit edit = FileEventHandler.handleWillRenameFiles(new RenameFilesParams(Arrays.asList(new FileRename(pack2Uri, newPack2Uri))), new NullProgressMonitor());
		assertNotNull(edit);
		List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = edit.getDocumentChanges();
		assertEquals(2, documentChanges.size());

		assertTrue(documentChanges.get(0).isLeft());
		assertEquals(documentChanges.get(0).getLeft().getTextDocument().getUri(), JDTUtils.toURI(cuA));
		assertEquals(TextEditUtil.apply(codeA.toString(), documentChanges.get(0).getLeft().getEdits()),
				"package parent.pack1;\n" +
				"import parent.newpack2.B;\n" +
				"public class A {\n" +
				"	public void foo() {\n" +
				"		B b = new B();\n" +
				"		b.foo();\n" +
				"	}\n" +
				"}\n"
				);

		assertTrue(documentChanges.get(1).isLeft());
		assertEquals(documentChanges.get(1).getLeft().getTextDocument().getUri(), JDTUtils.toURI(cuB));
		assertEquals(TextEditUtil.apply(codeB.toString(), documentChanges.get(1).getLeft().getEdits()),
				"package parent.newpack2;\n" +
				"public class B {\n" +
				"	public B() {}\n" +
				"	public void foo() {}\n" +
				"}\n"
				);
	}

	// Test renaming package from "parent.pack2" to "newparent.newpack2"
	@Test
	public void testRenamePackage2() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("parent.pack1", false, null);
		IPackageFragment pack2 = sourceFolder.createPackageFragment("parent.pack2", false, null);

		StringBuilder codeA = new StringBuilder();
		codeA.append("package parent.pack1;\n");
		codeA.append("import parent.pack2.B;\n");
		codeA.append("public class A {\n");
		codeA.append("	public void foo() {\n");
		codeA.append("		B b = new B();\n");
		codeA.append("		b.foo();\n");
		codeA.append("	}\n");
		codeA.append("}\n");

		StringBuilder codeB = new StringBuilder();
		codeB.append("package parent.pack2;\n");
		codeB.append("public class B {\n");
		codeB.append("	public B() {}\n");
		codeB.append("	public void foo() {}\n");
		codeB.append("}\n");

		ICompilationUnit cuA = pack1.createCompilationUnit("A.java", codeA.toString(), false, null);
		ICompilationUnit cuB = pack2.createCompilationUnit("B.java", codeB.toString(), false, null);

		String pack2Uri = JDTUtils.getFileURI(pack2.getResource());
		String newPack2Uri = pack2Uri.replace("pack2", "newpack2").replace("parent", "newparent");
		WorkspaceEdit edit = FileEventHandler.handleWillRenameFiles(new RenameFilesParams(Arrays.asList(new FileRename(pack2Uri, newPack2Uri))), new NullProgressMonitor());
		assertNotNull(edit);
		List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = edit.getDocumentChanges();
		assertEquals(2, documentChanges.size());

		assertTrue(documentChanges.get(0).isLeft());
		assertEquals(documentChanges.get(0).getLeft().getTextDocument().getUri(), JDTUtils.toURI(cuA));
		assertEquals(TextEditUtil.apply(codeA.toString(), documentChanges.get(0).getLeft().getEdits()),
				"package parent.pack1;\n" +
				"import newparent.newpack2.B;\n" +
				"public class A {\n" +
				"	public void foo() {\n" +
				"		B b = new B();\n" +
				"		b.foo();\n" +
				"	}\n" +
				"}\n"
				);

		assertTrue(documentChanges.get(1).isLeft());
		assertEquals(documentChanges.get(1).getLeft().getTextDocument().getUri(), JDTUtils.toURI(cuB));
		assertEquals(TextEditUtil.apply(codeB.toString(), documentChanges.get(1).getLeft().getEdits()),
				"package newparent.newpack2;\n" +
				"public class B {\n" +
				"	public B() {}\n" +
				"	public void foo() {}\n" +
				"}\n"
				);
	}

	@Test
	public void testRenameSubPackage() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment parentPack = sourceFolder.createPackageFragment("parent", false, null);
		IPackageFragment pack1 = sourceFolder.createPackageFragment("parent.pack1", false, null);
		IPackageFragment pack2 = sourceFolder.createPackageFragment("parent.pack2", false, null);

		StringBuilder codeA = new StringBuilder();
		codeA.append("package parent.pack1;\n");
		codeA.append("import parent.pack2.B;\n");
		codeA.append("public class A {\n");
		codeA.append("	public void foo() {\n");
		codeA.append("		B b = new B();\n");
		codeA.append("		b.foo();\n");
		codeA.append("	}\n");
		codeA.append("}\n");

		StringBuilder codeB = new StringBuilder();
		codeB.append("package parent.pack2;\n");
		codeB.append("public class B {\n");
		codeB.append("	public B() {}\n");
		codeB.append("	public void foo() {}\n");
		codeB.append("}\n");

		ICompilationUnit cuA = pack1.createCompilationUnit("A.java", codeA.toString(), false, null);
		ICompilationUnit cuB = pack2.createCompilationUnit("B.java", codeB.toString(), false, null);

		String parentPackUri = JDTUtils.getFileURI(parentPack.getResource());
		String newParentPackUri = parentPackUri.replace("parent", "newparent");
		WorkspaceEdit edit = FileEventHandler.handleWillRenameFiles(new RenameFilesParams(Arrays.asList(new FileRename(parentPackUri, newParentPackUri))), new NullProgressMonitor());
		assertNotNull(edit);
		List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = edit.getDocumentChanges();
		assertEquals(3, documentChanges.size());

		assertTrue(documentChanges.get(0).isLeft());
		assertEquals(documentChanges.get(0).getLeft().getTextDocument().getUri(), JDTUtils.toURI(cuA));
		assertTrue(documentChanges.get(1).isLeft());
		assertEquals(documentChanges.get(1).getLeft().getTextDocument().getUri(), JDTUtils.toURI(cuA));
		List<TextEdit> edits = new ArrayList<>();
		edits.addAll(documentChanges.get(0).getLeft().getEdits());
		edits.addAll(documentChanges.get(1).getLeft().getEdits());
		assertEquals(TextEditUtil.apply(codeA.toString(), edits),
				"package newparent.pack1;\n" +
				"import newparent.pack2.B;\n" +
				"public class A {\n" +
				"	public void foo() {\n" +
				"		B b = new B();\n" +
				"		b.foo();\n" +
				"	}\n" +
				"}\n"
				);

		assertTrue(documentChanges.get(2).isLeft());
		assertEquals(documentChanges.get(2).getLeft().getTextDocument().getUri(), JDTUtils.toURI(cuB));
		assertEquals(TextEditUtil.apply(codeB.toString(), documentChanges.get(2).getLeft().getEdits()),
				"package newparent.pack2;\n" +
				"public class B {\n" +
				"	public B() {}\n" +
				"	public void foo() {}\n" +
				"}\n"
				);
	}

	@Test
	public void testMoveMultiFiles() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

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

		//@formatter:off
		ICompilationUnit unitC = pack1.createCompilationUnit("C.java", "package jdtls.test1;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"	private B b = new B();\r\n" +
				"}", true, null);
		//@formatter:on

		IPackageFragment pack2 = sourceFolder.createPackageFragment("jdtls.test2", false, null);

		String uriA = JDTUtils.toURI(unitA);
		String uriB = JDTUtils.toURI(unitB);
		String newUriA = uriA.replace("test1", "test2");
		String newUriB = uriB.replace("test1", "test2");
		WorkspaceEdit edit = FileEventHandler.handleWillRenameFiles(
			new RenameFilesParams(Arrays.asList(
				new FileRename(uriA, newUriA),
				new FileRename(uriB, newUriB))),
			new NullProgressMonitor());

		assertNotNull(edit);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = edit.getDocumentChanges();
		assertEquals(3, changes.size());

		//@formatter:off
		String expected = "package jdtls.test1;\r\n" +
				"\r\n" +
				"import jdtls.test2.B;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"	private B b = new B();\r\n" +
				"}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		List<TextEdit> edits = new ArrayList<>(textEdit.getEdits());
		assertEquals(expected, TextEditUtil.apply(unitC.getSource(), edits));

		//@formatter:off
		expected = "package jdtls.test2;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}";
		//@formatter:on
		textEdit = changes.get(1).getLeft();
		assertNotNull(textEdit);
		edits = new ArrayList<>(textEdit.getEdits());
		assertEquals(expected, TextEditUtil.apply(unitB.getSource(), edits));

		//@formatter:off
		expected = "package jdtls.test2;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private B b = new B();\r\n" +
				"}";
		//@formatter:on
		textEdit = changes.get(2).getLeft();
		assertNotNull(textEdit);
		edits = new ArrayList<>(textEdit.getEdits());
		assertEquals(expected, TextEditUtil.apply(unitA.getSource(), edits));
	}

	@Test
	public void testMoveMultiFiles_differentDestination() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

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

		//@formatter:off
		ICompilationUnit unitC = pack1.createCompilationUnit("C.java", "package jdtls.test1;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"	private B b = new B();\r\n" +
				"}", true, null);
		//@formatter:on

		IPackageFragment pack2 = sourceFolder.createPackageFragment("jdtls.test2", false, null);
		IPackageFragment pack3 = sourceFolder.createPackageFragment("jdtls.test3", false, null);

		String uriA = JDTUtils.toURI(unitA);
		String uriB = JDTUtils.toURI(unitB);
		String newUriA = uriA.replace("test1", "test2");
		String newUriB = uriB.replace("test1", "test3");
		WorkspaceEdit edit = FileEventHandler.handleWillRenameFiles(
			new RenameFilesParams(Arrays.asList(
				new FileRename(uriA, newUriA),
				new FileRename(uriB, newUriB))),
			new NullProgressMonitor());
		assertNull(edit);
	}

	@Test
	public void testMoveNonClasspathFile() throws Exception {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("jdtls.test1", true, null);
		File projectRoot = javaProject.getProject().getLocation().toFile();
		File file = new File(projectRoot, "Bar.java");
		file.createNewFile();
		String contents = "public class Bar {\r\n}";
		FileUtils.writeStringToFile(file, contents);
		ICompilationUnit bar = JDTUtils.resolveCompilationUnit(file.toURI());
		bar.getResource().refreshLocal(IResource.DEPTH_ONE, null);

		String uri = JDTUtils.toURI(bar);
		String newUri = uri.replace("Bar.java", "src/jdtls/test1/Bar.java");
		WorkspaceEdit edit = FileEventHandler.handleWillRenameFiles(
			new RenameFilesParams(Arrays.asList(new FileRename(uri, newUri))),
			new NullProgressMonitor());

		assertNotNull(edit);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = edit.getDocumentChanges();
		assertEquals(1, changes.size());

		//@formatter:off
		String expected = "package jdtls.test1;\r\n" +
				"public class Bar {\r\n" +
				"}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		List<TextEdit> edits = new ArrayList<>(textEdit.getEdits());
		bar.becomeWorkingCopy(null);
		assertEquals(expected, TextEditUtil.apply(bar.getSource(), edits));
		bar.discardWorkingCopy();
	}
}
