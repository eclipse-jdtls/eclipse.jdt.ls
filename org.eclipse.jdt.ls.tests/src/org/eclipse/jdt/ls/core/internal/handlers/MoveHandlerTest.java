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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.ToolProvider;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
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
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class MoveHandlerTest extends AbstractProjectsManagerBasedTest {
	private IPackageFragmentRoot sourceFolder;

	@BeforeEach
	public void setup() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		ClientPreferences clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);
		when(clientPreferences.isMoveRefactoringConfirmationSupported()).thenReturn(true);
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
		assertEquals(expected, TextEditUtil.apply(unitA.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));

		//@formatter:off
		expected = "package jdtls.test3;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}";
		//@formatter:on
		textEdit = changes.get(1).getLeft();
		assertNotNull(textEdit);
		List<TextEdit> edits = textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList();
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
		List<TextEdit> edits = textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList();
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
		edits = textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList();
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
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));

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
		assertEquals(expected, TextEditUtil.apply(cuSecond.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));
	}

	@Test
	public void testMoveInstanceMethodConfirmsTargetNullCheck() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		ICompilationUnit cu = pack1.createCompilationUnit("Demo.java", "package test1;\n"
				+ "\n"
				+ "public class Demo {\n"
				+ "    public static void main(String[] args) {\n"
				+ "        A a = new A();\n"
				+ "        System.out.println(a.process());\n"
				+ "    }\n"
				+ "}\n"
				+ "\n"
				+ "class A {\n"
				+ "    C c;\n"
				+ "\n"
				+ "    int process() {\n"
				+ "        if (c != null) return 2;\n"
				+ "        return 0;\n"
				+ "    }\n"
				+ "}\n"
				+ "\n"
				+ "class B extends A {\n"
				+ "}\n"
				+ "\n"
				+ "class C extends B {\n"
				+ "}\n",
				false, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, "c != null");
		MoveParams moveParams = new MoveParams("moveInstanceMethod", new String[] { JDTUtils.toURI(cu) }, params);
		MoveDestinationsResponse response = MoveHandler.getMoveDestinations(moveParams);
		assertNotNull(response);
		assertNull(response.errorMessage);
		assertNotNull(response.destinations);
		assertEquals(1, response.destinations.length);
		assertEquals("c", ((LspVariableBinding) response.destinations[0]).name);

		when(preferenceManager.getClientPreferences().isMoveRefactoringConfirmationSupported()).thenReturn(false);
		RefactorWorkspaceEdit unsupportedClientEdit = MoveHandler.move(new MoveParams("moveInstanceMethod", params, response.destinations[0], true), new NullProgressMonitor());
		assertNotNull(unsupportedClientEdit);
		assertNotNull(unsupportedClientEdit.edit);
		assertNull(unsupportedClientEdit.errorMessage);
		assertNull(unsupportedClientEdit.confirmationToken);

		when(preferenceManager.getClientPreferences().isMoveRefactoringConfirmationSupported()).thenReturn(true);
		RefactorWorkspaceEdit refactorEdit = MoveHandler.move(new MoveParams("moveInstanceMethod", params, response.destinations[0], true), new NullProgressMonitor());
		assertNotNull(refactorEdit);
		assertNull(refactorEdit.edit);
		assertNotNull(refactorEdit.errorMessage);
		assertTrue(refactorEdit.errorMessage.contains("compared to null"), refactorEdit.errorMessage);
		assertNotNull(refactorEdit.confirmationToken);

		String originalSource = cu.getSource();
		cu.getBuffer().setContents(originalSource + "\n// source changed\n");
		cu.save(null, true);
		MoveParams staleConfirmation = new MoveParams("moveInstanceMethod", null, params, response.destinations[0], true, refactorEdit.confirmationToken);
		RefactorWorkspaceEdit staleEdit = MoveHandler.move(staleConfirmation, new NullProgressMonitor());
		assertNotNull(staleEdit);
		assertNull(staleEdit.edit);
		assertNull(staleEdit.confirmationToken);
		assertTrue(staleEdit.errorMessage.contains("conditions changed"), staleEdit.errorMessage);

		cu.getBuffer().setContents(originalSource);
		cu.save(null, true);
		MoveParams confirmedMove = new MoveParams("moveInstanceMethod", null, params, response.destinations[0], true, refactorEdit.confirmationToken);
		assertConfirmationIgnoresClientDiagnostics(confirmedMove);
		RefactorWorkspaceEdit confirmedEdit = MoveHandler.move(confirmedMove, new NullProgressMonitor());
		assertNotNull(confirmedEdit);
		assertNotNull(confirmedEdit.edit);
		assertNull(confirmedEdit.errorMessage);
		List<Either<TextDocumentEdit, ResourceOperation>> changes = confirmedEdit.edit.getDocumentChanges();
		assertEquals(1, changes.size());
		TextDocumentEdit textEdit = changes.get(0).getLeft();
		assertNotNull(textEdit);
		String movedSource = TextEditUtil.apply(originalSource, textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList());
		assertTrue(movedSource.contains("return c.process();"), movedSource);
		assertTrue(movedSource.contains("if (this != null) return 2;"), movedSource);
	}

	@Test
	public void testConfirmationTokenFingerprintsInputsAndProblems() {
		RefactoringStatus status = new RefactoringStatus();
		status.addError("problem");

		String token = RefactoringConfirmation.createToken("operation:v1", status, "request", "source");
		assertEquals(64, token.length());
		assertEquals(token, RefactoringConfirmation.createToken("operation:v1", status, "request", "source"));
		assertNotEquals(token, RefactoringConfirmation.createToken("operation:v1", status, "request", "changed source"));

		status.addWarning("another problem");
		assertNotEquals(token, RefactoringConfirmation.createToken("operation:v1", status, "request", "source"));
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
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));

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
		assertEquals(expected, TextEditUtil.apply(unitFoo.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));
	}

	@Test
	public void testMoveStaticMethodRequiresConfirmation() throws Exception {
		assertStaticMemberConfirmation("public static int value() { return 2; }", "value()", "return value();", "moveStaticMember");
	}

	@Test
	public void testMoveStaticFieldRequiresConfirmation() throws Exception {
		assertStaticMemberConfirmation("public static int field = 2;", "field =", "return field;", "moveStaticMember");
	}

	@Test
	public void testMoveTypeToClassRequiresConfirmation() throws Exception {
		IPackageFragment pack = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit source = pack.createCompilationUnit("Source.java", "package test1; public class Source { public static class Nested {} }", false, null);
		pack.createCompilationUnit("Outer.java", "package test1; public class Outer { public class Target {} }", false, null);
		CodeActionParams context = CodeActionUtil.constructCodeActionParams(source, "class Nested");
		RefactorWorkspaceEdit edit = assertMoveConfirmation(new MoveParams("moveTypeToClass", context, "test1.Outer.Target", true), "Static members", source);
		assertEquals(2, edit.edit.getDocumentChanges().size());
	}

	private void assertStaticMemberConfirmation(String member, String selection, String targetBody, String moveKind) throws Exception {
		IPackageFragment pack = sourceFolder.createPackageFragment("test1", false, null);
		pack.createCompilationUnit("Parent.java", """
				package test1;
				public class Parent {
				    public static int value() { return 1; }
				    public static int field = 1;
				    public static class Nested {}
				}
				""", false, null);
		ICompilationUnit source = pack.createCompilationUnit("Source.java", "package test1; public class Source { " + member + " }", false, null);
		pack.createCompilationUnit("Target.java", "package test1; public class Target extends Parent { int use() { " + targetBody + " } }", false, null);
		CodeActionParams context = CodeActionUtil.constructCodeActionParams(source, selection);
		MoveParams params = new MoveParams(moveKind, context, "test1.Target", true);
		RefactorWorkspaceEdit edit = assertMoveConfirmation(params, "override references", source);
		assertEquals(2, edit.edit.getDocumentChanges().size());
	}

	@Test
	public void testMoveConfirmationCannotBeReusedForAnotherDestinationOrKind() throws Exception {
		IPackageFragment pack = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit source = pack.createCompilationUnit("Source.java", "package test1; public class Source { public static class Nested {} }", false, null);
		pack.createCompilationUnit("Outer.java", "package test1; public class Outer { public class Target {} public class Other {} }", false, null);
		CodeActionParams context = CodeActionUtil.constructCodeActionParams(source, "class Nested");
		MoveParams params = new MoveParams("moveTypeToClass", context, "test1.Outer.Target", true);
		RefactorWorkspaceEdit confirmation = MoveHandler.move(params, new NullProgressMonitor());
		assertNotNull(confirmation.confirmationToken, confirmation.errorMessage);
		assertStaleMoveConfirmation(withConfirmation(new MoveParams("moveTypeToClass", context, "test1.Outer.Other", true), confirmation.confirmationToken));
		assertStaleMoveConfirmation(withConfirmation(new MoveParams("moveStaticMember", context, "test1.Outer.Target", true), confirmation.confirmationToken));
	}

	@Test
	public void testMoveConfirmationIsNotRequiredWhenTheProblemDisappears() throws Exception {
		IPackageFragment pack = sourceFolder.createPackageFragment("test1", false, null);
		pack.createCompilationUnit("Parent.java", "package test1; public class Parent { public static int value() { return 1; } }", false, null);
		ICompilationUnit source = pack.createCompilationUnit("Source.java", "package test1; public class Source { public static int value() { return 2; } }", false, null);
		ICompilationUnit target = pack.createCompilationUnit("Target.java", "package test1; public class Target extends Parent { int use() { return value(); } }", false, null);
		CodeActionParams context = CodeActionUtil.constructCodeActionParams(source, "value()");
		MoveParams params = new MoveParams("moveStaticMember", context, "test1.Target", true);
		RefactorWorkspaceEdit confirmation = MoveHandler.move(params, new NullProgressMonitor());
		assertNotNull(confirmation.confirmationToken, confirmation.errorMessage);
		target.getBuffer().setContents("package test1; public class Target extends Parent {}");
		target.save(null, true);
		RefactorWorkspaceEdit confirmed = MoveHandler.move(withConfirmation(params, confirmation.confirmationToken), new NullProgressMonitor());
		assertNotNull(confirmed.edit, confirmed.errorMessage);
		assertNull(confirmed.errorMessage);
		assertNull(confirmed.confirmationToken);
		RefactorWorkspaceEdit safeMove = MoveHandler.move(params, new NullProgressMonitor());
		assertNotNull(safeMove.edit, safeMove.errorMessage);
		assertNull(safeMove.confirmationToken);
		assertEquals(safeMove.edit, confirmed.edit);
	}

	@Test
	public void testMoveConfirmationDoesNotFingerprintDestinationSource() throws Exception {
		IPackageFragment pack = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit source = pack.createCompilationUnit("Source.java", "package test1; public class Source { public static class Nested {} }", false, null);
		ICompilationUnit target = pack.createCompilationUnit("Outer.java", "package test1; public class Outer { public class Target {} }", false, null);
		CodeActionParams context = CodeActionUtil.constructCodeActionParams(source, "class Nested");
		MoveParams params = new MoveParams("moveTypeToClass", context, "test1.Outer.Target", true);
		RefactorWorkspaceEdit confirmation = MoveHandler.move(params, new NullProgressMonitor());
		assertNotNull(confirmation.confirmationToken, confirmation.errorMessage);

		target.getBuffer().setContents("package test1; public class Outer { /* destination changed */ public class Target {} }");
		target.save(null, true);
		RefactorWorkspaceEdit currentConfirmation = MoveHandler.move(params, new NullProgressMonitor());
		assertEquals(confirmation.confirmationToken, currentConfirmation.confirmationToken, currentConfirmation.errorMessage);
		RefactorWorkspaceEdit confirmed = MoveHandler.move(withConfirmation(params, confirmation.confirmationToken), new NullProgressMonitor());
		assertNotNull(confirmed.edit, confirmed.errorMessage);
		assertNull(confirmed.errorMessage);
		assertNull(confirmed.confirmationToken);
	}

	@Test
	public void testMoveTypeToNewFileRequiresConfirmation() throws Exception {
		IPackageFragment pack = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit source = pack.createCompilationUnit("Top.java", """
				package test1;
				public class Top {
				    int value;
				    public class Inner {
				        public Inner(int top) {
				            System.out.println(Top.this.value + top);
				        }
				    }
				}
				""", false, null);
		CodeActionParams context = CodeActionUtil.constructCodeActionParams(source, "class Inner");
		RefactorWorkspaceEdit edit = assertMoveConfirmation(new MoveParams("moveTypeToNewFile", context, null, true), "parameter name", source);
		assertTrue(edit.edit.getDocumentChanges().stream().anyMatch(change -> change.isRight() && change.getRight() instanceof CreateFile));
		assertFalse(pack.getCompilationUnit("Inner.java").exists());
	}

	@Test
	public void testMoveResourceRequiresConfirmationForBinaryReferences() throws Exception {
		IPackageFragment pack = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit source = pack.createCompilationUnit("A.java", "package test1; public class A {}", false, null);
		IPackageFragment destination = sourceFolder.createPackageFragment("test2", false, null);
		Path temporaryDirectory = Files.createDirectories(source.getJavaProject().getProject().getLocation().toFile().toPath().resolve("binary-references"));
		Path clientSource = temporaryDirectory.resolve("Client.java");
		Files.writeString(clientSource, "package binary; public class Client { public test1.A value; }");
		Path binaryOutput = Files.createDirectory(temporaryDirectory.resolve("bin"));
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"--release", "21", "-proc:none", "-sourcepath", sourceFolder.getResource().getLocation().toOSString(),
				"-d", binaryOutput.toString(), clientSource.toString()));
		Files.delete(binaryOutput.resolve("test1/A.class"));
		JavaProjectHelper.addLibrary(source.getJavaProject(), org.eclipse.core.runtime.Path.fromOSString(binaryOutput.toString()));

		MoveParams params = new MoveParams("moveResource", new String[] { JDTUtils.toURI(source) }, JDTUtils.getFileURI(destination.getResource()), true);
		RefactorWorkspaceEdit edit = assertMoveConfirmation(params, "Binary references", source);
		assertTrue(edit.edit.getDocumentChanges().stream().anyMatch(change -> change.isRight() && change.getRight() instanceof RenameFile));
		assertTrue(source.exists());
		assertFalse(destination.getCompilationUnit("A.java").exists());
	}

	@Test
	public void testMoveStaticMethodWarningDoesNotRequireConfirmation() throws Exception {
		when(preferenceManager.getClientPreferences().isMoveRefactoringConfirmationSupported()).thenReturn(true);
		IPackageFragment pack = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit source = pack.createCompilationUnit("Source.java", "package test1; public class Source { public static native void method(); }", false, null);
		pack.createCompilationUnit("Target.java", "package test1; public class Target {}", false, null);
		CodeActionParams context = CodeActionUtil.constructCodeActionParams(source, "method()");
		RefactorWorkspaceEdit edit = MoveHandler.move(new MoveParams("moveStaticMember", context, "test1.Target", true), new NullProgressMonitor());
		assertNotNull(edit.edit, edit.errorMessage);
		assertNull(edit.errorMessage);
		assertNull(edit.confirmationToken);
	}

	@Test
	public void testFatalMoveErrorsDoNotOfferConfirmation() throws Exception {
		when(preferenceManager.getClientPreferences().isMoveRefactoringConfirmationSupported()).thenReturn(true);
		IPackageFragment pack = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit source = pack.createCompilationUnit("Source.java", "package test1; public class Source { public static int value = 1; public static class Nested {} }", false, null);
		pack.createCompilationUnit("Nested.java", "package test1; public class Nested {}", false, null);
		CodeActionParams member = CodeActionUtil.constructCodeActionParams(source, "value =");
		CodeActionParams type = CodeActionUtil.constructCodeActionParams(source, "class Nested");
		MoveParams[] requests = {
				new MoveParams("moveStaticMember", member, "test1.Source", true),
				new MoveParams("moveTypeToClass", type, "test1.Source", true),
				new MoveParams("moveTypeToNewFile", type, null, true),
				new MoveParams("moveResource", new String[] { JDTUtils.toURI(source) }, "file:///missing-move-target", true)
		};
		for (MoveParams params : requests) {
			RefactorWorkspaceEdit edit = MoveHandler.move(params, new NullProgressMonitor());
			assertNotNull(edit);
			assertNull(edit.edit);
			assertNotNull(edit.errorMessage);
			assertNull(edit.confirmationToken);
		}
	}

	private RefactorWorkspaceEdit assertMoveConfirmation(MoveParams params, String expectedProblem, ICompilationUnit source) throws Exception {
		when(preferenceManager.getClientPreferences().isMoveRefactoringConfirmationSupported()).thenReturn(false);
		RefactorWorkspaceEdit legacy = MoveHandler.move(params, new NullProgressMonitor());
		assertNotNull(legacy.edit, legacy.errorMessage);
		assertNull(legacy.errorMessage);
		assertNull(legacy.confirmationToken);

		when(preferenceManager.getClientPreferences().isMoveRefactoringConfirmationSupported()).thenReturn(true);
		RefactorWorkspaceEdit confirmation = MoveHandler.move(params, new NullProgressMonitor());
		assertNull(confirmation.edit);
		assertNotNull(confirmation.confirmationToken, confirmation.errorMessage);
		assertTrue(confirmation.errorMessage.contains(expectedProblem), confirmation.errorMessage);
		assertEquals(confirmation.confirmationToken, MoveHandler.move(params, new NullProgressMonitor()).confirmationToken);

		assertStaleMoveConfirmation(withConfirmation(params, "invalid-token"));
		MoveParams confirmedParams = withConfirmation(params, confirmation.confirmationToken);
		assertConfirmationIgnoresClientDiagnostics(confirmedParams);
		String original = source.getSource();
		try {
			source.getBuffer().setContents(original + "\n// changed after confirmation\n");
			source.save(null, true);
			assertStaleMoveConfirmation(confirmedParams);
		} finally {
			source.getBuffer().setContents(original);
			source.save(null, true);
		}

		RefactorWorkspaceEdit confirmed = MoveHandler.move(confirmedParams, new NullProgressMonitor());
		assertNotNull(confirmed.edit, confirmed.errorMessage);
		assertNull(confirmed.errorMessage);
		assertNull(confirmed.confirmationToken);
		assertEquals(legacy.edit, confirmed.edit);
		return confirmed;
	}

	private void assertConfirmationIgnoresClientDiagnostics(MoveParams params) {
		Gson gson = new Gson();
		JsonObject request = gson.toJsonTree(params).getAsJsonObject();
		if (!request.has("params")) {
			return;
		}
		CodeActionParams context = gson.fromJson(request.get("params"), CodeActionParams.class);
		context.getContext().setDiagnostics(List.of(new Diagnostic(context.getRange(), "Updated client diagnostic")));
		request.add("params", gson.toJsonTree(context));

		RefactorWorkspaceEdit confirmed = MoveHandler.move(gson.fromJson(request, MoveParams.class), new NullProgressMonitor());
		assertNotNull(confirmed.edit, confirmed.errorMessage);
		assertNull(confirmed.errorMessage);
		assertNull(confirmed.confirmationToken);
	}

	private MoveParams withConfirmation(MoveParams params, String token) {
		Gson gson = new Gson();
		JsonObject request = gson.toJsonTree(params).getAsJsonObject();
		request.addProperty("confirmationToken", token);
		return gson.fromJson(request, MoveParams.class);
	}

	private void assertStaleMoveConfirmation(MoveParams params) {
		RefactorWorkspaceEdit rejected = MoveHandler.move(params, new NullProgressMonitor());
		assertNull(rejected.edit);
		assertNull(rejected.confirmationToken);
		assertNotNull(rejected.errorMessage);
		assertTrue(rejected.errorMessage.contains("conditions changed"), rejected.errorMessage);
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
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));

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
		assertEquals(expected, TextEditUtil.apply(unitUtility.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));
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
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));

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
		assertEquals(expected, TextEditUtil.apply(unitFoo.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));
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
		assertEquals(expected, TextEditUtil.apply(cu.getSource(), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));
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
		assertEquals(expected, TextEditUtil.apply(pack1.getCompilationUnit("Inner.java").getWorkingCopy(null), textEdit.getEdits().stream().filter(Either::isLeft).map(Either::getLeft).toList()));
	}
}
