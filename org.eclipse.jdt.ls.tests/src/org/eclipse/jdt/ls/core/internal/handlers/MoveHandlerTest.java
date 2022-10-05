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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.RefactorWorkspaceEdit;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspVariableBinding;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveDestinationsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveParams;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.PackageNode;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CreateFile;
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
		MoveParams params = new MoveParams("moveResource", new String[] { JDTUtils.toURI(unit) });
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
	public void testGetInstanceMethodDestinations() throws Exception {
		when(preferenceManager.getClientPreferences().isMoveRefactoringSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		pack1.createCompilationUnit("Second.java", "package test1;\n"
				+ "\n"
				+ "public class Second {\n"
				+ "    public void foo() {\n"
				+ "    }\n"
				+ "}",
				false, null);
		//@formatter:on

		//@formatter:off
		pack1.createCompilationUnit("Third.java", "package test1;\n"
				+ "\n"
				+ "public class Third {\n"
				+ "    public void bar() {\n"
				+ "    }\n"
				+ "}",
				false, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", "package test1;\n"
				+ "\n"
				+ "public class E {\n"
				+ "    Second s;\n"
				+ "    String name;\n"
				+ "    int id;\n"
				+ "    public void print(Third t) {\n"
				+ "        s.foo();\n"
				+ "        t.bar();\n"
				+ "    }\n"
				+ "}",
				false, null);
		//@formatter:on

		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, "s.foo");
		MoveParams moveParams = new MoveParams("moveInstanceMethod", new String[] { JDTUtils.toURI(cu) }, params);
		MoveDestinationsResponse response = MoveHandler.getMoveDestinations(moveParams);
		assertNotNull(response);
		assertNull(response.errorMessage);
		assertNotNull(response.destinations);
		assertEquals(2, response.destinations.length);
		assertEquals("t", ((LspVariableBinding) response.destinations[0]).name);
		assertFalse(((LspVariableBinding) response.destinations[0]).isField);
		assertEquals("Third", ((LspVariableBinding) response.destinations[0]).type);
		assertEquals("s", ((LspVariableBinding) response.destinations[1]).name);
		assertTrue(((LspVariableBinding) response.destinations[1]).isField);
		assertEquals("Second", ((LspVariableBinding) response.destinations[1]).type);
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
		assertEquals(3, changes.size());

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
		assertEquals(expected, TextEditUtil.apply(unitB.getSource(), edits));

		RenameFile renameFile = (RenameFile) changes.get(2).getRight();
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
		assertEquals(4, changes.size());

		//@formatter:off
		String expected = "package jdtls.test2;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.stream().filter(
				chg -> chg.isLeft() && chg.getLeft().getTextDocument().getUri().endsWith("B.java"))
				.findFirst().get().getLeft();
		assertNotNull(textEdit);
		List<TextEdit> edits = new ArrayList<>(textEdit.getEdits());
		assertEquals(expected, TextEditUtil.apply(unitB.getSource(), edits));

		RenameFile renameFileB = (RenameFile) changes.stream().filter(
				chg -> chg.isRight() && ((RenameFile) chg.getRight()).getOldUri().endsWith("B.java"))
				.findFirst().get().getRight();
		assertNotNull(renameFileB);
		assertEquals(JDTUtils.toURI(unitB), renameFileB.getOldUri());
		assertEquals(ResourceUtils.fixURI(unitB.getResource().getRawLocationURI()).replace("test1", "test2"), renameFileB.getNewUri());

		//@formatter:off
		expected = "package jdtls.test2;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private B b = new B();\r\n" +
				"}";
		//@formatter:on
		textEdit = changes.stream().filter(
				chg -> chg.isLeft() && chg.getLeft().getTextDocument().getUri().endsWith("A.java"))
				.findFirst().get().getLeft();
		assertNotNull(textEdit);
		edits = new ArrayList<>(textEdit.getEdits());
		assertEquals(expected, TextEditUtil.apply(unitA.getSource(), edits));

		RenameFile renameFileA = (RenameFile) changes.stream().filter(
				chg -> chg.isRight() && ((RenameFile) chg.getRight()).getOldUri().endsWith("A.java"))
				.findFirst().get().getRight();
		assertNotNull(renameFileA);
		assertEquals(JDTUtils.toURI(unitA), renameFileA.getOldUri());
		assertEquals(ResourceUtils.fixURI(unitA.getResource().getRawLocationURI()).replace("test1", "test2"), renameFileA.getNewUri());
	}

	@Test
	public void testMoveInstanceMethod() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		ICompilationUnit cuSecond = pack1.createCompilationUnit("Second.java", "package test1;\n"
				+ "\n"
				+ "public class Second {\n"
				+ "    public void foo() {\n"
				+ "    }\n"
				+ "}",
				false, null);
		//@formatter:on

		//@formatter:off
		pack1.createCompilationUnit("Third.java", "package test1;\n"
				+ "\n"
				+ "public class Third {\n"
				+ "    public void bar() {\n"
				+ "    }\n"
				+ "}",
				false, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", "package test1;\n"
				+ "\n"
				+ "public class E {\n"
				+ "    Second s;\n"
				+ "    String name;\n"
				+ "    int id;\n"
				+ "    public void print(Third t) {\n"
				+ "        System.out.println(name);\n"
				+ "        s.foo();\n"
				+ "        t.bar();\n"
				+ "    }\n"
				+ "\n"
				+ "    public void log(Third t) {\n"
				+ "        print(t);\n"
				+ "    }\n"
				+ "}",
				false, null);
		//@formatter:on

		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, "s.foo");
		MoveParams moveParams = new MoveParams("moveInstanceMethod", new String[] { JDTUtils.toURI(cu) }, params);
		MoveDestinationsResponse response = MoveHandler.getMoveDestinations(moveParams);
		assertNotNull(response);
		assertNull(response.errorMessage);
		assertNotNull(response.destinations);
		assertEquals(2, response.destinations.length);

		RefactorWorkspaceEdit refactorEdit = MoveHandler.move(new MoveParams("moveInstanceMethod", params, response.destinations[1], true), new NullProgressMonitor());
		assertNotNull(refactorEdit);
		assertNotNull(refactorEdit.edit);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = refactorEdit.edit.getDocumentChanges();
		assertEquals(2, changes.size());

		//@formatter:off
		String expected = "package test1;\n"
				+ "\n"
				+ "public class E {\n"
				+ "    Second s;\n"
				+ "    String name;\n"
				+ "    int id;\n"
				+ "    public void log(Third t) {\n"
				+ "        s.print(this, t);\n"
				+ "    }\n"
				+ "}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits()));

		//@formatter:off
		expected = "package test1;\n"
				+ "\n"
				+ "public class Second {\n"
				+ "    public void foo() {\n"
				+ "    }\n"
				+ "\n"
				+ "	public void print(E e, Third t) {\n"
				+ "	    System.out.println(e.name);\n"
				+ "	    foo();\n"
				+ "	    t.bar();\n"
				+ "	}\n"
				+ "}";
		//@formatter:on
		textEdit = changes.get(1).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(cuSecond.getSource(), textEdit.getEdits()));
	}

	@Test
	public void testMoveStaticMethod() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", "package test1;\n"
				+ "\n"
				+ "public class E {\n"
				+ "    public static void foo() {\n"
				+ "        /*[*//*]*/\n"
				+ "    }\n"
				+ "\n"
				+ "    public void bar() {\n"
				+ "        foo();\n"
				+ "    }\n"
				+ "}",
				false, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit unitFoo = pack1.createCompilationUnit("Foo.java", "package test1;\n"
				+ "\n"
				+ "public class Foo {\n"
				+ "}", false, null);
		//@formatter:on

		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, "/*[*//*]*/");
		RefactorWorkspaceEdit refactorEdit = MoveHandler.move(new MoveParams("moveStaticMember", params, "Foo", true), new NullProgressMonitor());
		assertNotNull(refactorEdit);
		assertNotNull(refactorEdit.edit);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = refactorEdit.edit.getDocumentChanges();
		assertEquals(2, changes.size());

		//@formatter:off
		String expected = "package test1;\n"
						+ "\n"
						+ "public class E {\n"
						+ "    public void bar() {\n"
						+ "        Foo.foo();\n"
						+ "    }\n"
						+ "}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits()));

		//@formatter:off
		expected = "package test1;\n"
				+ "\n"
				+ "public class Foo {\n"
				+ "\n"
				+ "	public static void foo() {\n"
				+ "	    /*[*//*]*/\n"
				+ "	}\n"
				+ "}";
		//@formatter:on
		textEdit = changes.get(1).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(unitFoo.getSource(), textEdit.getEdits()));
	}

	@Test
	public void testMoveStaticField() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		pack1.createCompilationUnit("Foo.java", "package test1;\n"
				+ "\n"
				+ "public class Foo {\n"
				+ "    public void foo() {\n"
				+ "    }\n"
				+ "}", false, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit unitUtility = pack1.createCompilationUnit("Utility.java", "package test1;\n"
				+ "\n"
				+ "public class Utility {\n"
				+ "}", false, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", "package test1;\n"
				+ "\n"
				+ "public class E {\n"
				+ "    static Foo foo = new Foo();\n"
				+ "    public void print() {\n"
				+ "        foo.foo();\n"
				+ "    }\n"
				+ "}",
				false, null);
		//@formatter:on

		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, "new Foo()");
		RefactorWorkspaceEdit refactorEdit = MoveHandler.move(new MoveParams("moveStaticMember", params, "Utility", true), new NullProgressMonitor());
		assertNotNull(refactorEdit);
		assertNotNull(refactorEdit.edit);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = refactorEdit.edit.getDocumentChanges();
		assertEquals(2, changes.size());

		//@formatter:off
		String expected = "package test1;\n"
						+ "\n"
						+ "public class E {\n"
						+ "    public void print() {\n"
						+ "        Utility.foo.foo();\n"
						+ "    }\n"
						+ "}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits()));

		//@formatter:off
		expected = "package test1;\n"
				+ "\n"
				+ "public class Utility {\n"
				+ "\n"
				+ "	static Foo foo = new Foo();\n"
				+ "}";
		//@formatter:on
		textEdit = changes.get(1).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(unitUtility.getSource(), textEdit.getEdits()));
	}

	@Test
	public void testMoveStaticInnerType() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		ICompilationUnit unitFoo = pack1.createCompilationUnit("Foo.java", "package test1;\n"
				+ "\n"
				+ "public class Foo {\n"
				+ "}", false, null);
		//@formatter:on

		//@formatter:off
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", "package test1;\n"
				+ "\n"
				+ "public class E {\n"
				+ "    public void print() {\n"
				+ "        new Inner().run();\n"
				+ "    }\n"
				+ "    static class Inner {\n"
				+ "        void run() {\n"
				+ "        }\n"
				+ "    }\n"
				+ "}", false, null);
		//@formatter:on

		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, "class Inner");
		RefactorWorkspaceEdit refactorEdit = MoveHandler.move(new MoveParams("moveTypeToClass", params, "Foo", true), new NullProgressMonitor());
		assertNotNull(refactorEdit);
		assertNotNull(refactorEdit.edit);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = refactorEdit.edit.getDocumentChanges();
		assertEquals(2, changes.size());

		//@formatter:off
		String expected = "package test1;\n"
						+ "\n"
						+ "public class E {\n"
						+ "    public void print() {\n"
						+ "        new Foo.Inner().run();\n"
						+ "    }\n"
						+ "}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits()));

		//@formatter:off
		expected = "package test1;\n"
				+ "\n"
				+ "public class Foo {\n"
				+ "\n"
				+ "	static class Inner {\n"
				+ "	    void run() {\n"
				+ "	    }\n"
				+ "	}\n"
				+ "}";
		//@formatter:on
		textEdit = changes.get(1).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(unitFoo.getSource(), textEdit.getEdits()));
	}

	@Test
	public void testMoveInnerTypeToFile() throws Exception {
		System.setProperty("line.separator", "\n");
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		ICompilationUnit cu = pack1.createCompilationUnit("Top.java", "package test1;\n"
				+ "\n"
				+ "public class Top {\n"
				+ "    String name;\n\n"
				+ "    public class Inner {\n"
				+ "        public void print() {\n"
				+ "            System.out.println(Top.this.name);\n"
				+ "        }\n"
				+ "    }\n"
				+ "}", false, null);
		//@formatter:on

		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, "class Inner");
		RefactorWorkspaceEdit refactorEdit = MoveHandler.move(new MoveParams("moveTypeToNewFile", params, "Foo", true), new NullProgressMonitor());
		assertNotNull(refactorEdit);
		assertNotNull(refactorEdit.edit);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = refactorEdit.edit.getDocumentChanges();
		assertEquals(3, changes.size());

		//@formatter:off
		String expected = "package test1;\n"
						+ "\n"
						+ "public class Top {\n"
						+ "    String name;\n"
						+ "}";
		//@formatter:on
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits()));

		ResourceOperation resourceOperation = changes.get(1).getRight();
		assertNotNull(resourceOperation);
		assertTrue(resourceOperation instanceof CreateFile);
		assertEquals(ResourceUtils.fixURI(cu.getResource().getRawLocationURI()).replace("Top", "Inner"), ((CreateFile) resourceOperation).getUri());

		//@formatter:off
		expected = "package test1;\n"
				+ "\n"
				+ "public class Inner {\n"
				+ "    /**\n"
				+ "	 *\n"
				+ "	 */\n"
				+ "	private final Top top;\n\n"
				+ "	/**\n"
				+ "	 * @param top\n"
				+ "	 */\n"
				+ "	Inner(Top top) {\n"
				+ "		this.top = top;\n"
				+ "	}\n\n"
				+ "	public void print() {\n"
				+ "        System.out.println(this.top.name);\n"
				+ "    }\n"
				+ "}";
		//@formatter:on
		textEdit = changes.get(2).getLeft();
		assertNotNull(textEdit);
		assertEquals(expected, TextEditUtil.apply(pack1.getCompilationUnit("Inner.java").getWorkingCopy(null), textEdit.getEdits()));
	}
}
