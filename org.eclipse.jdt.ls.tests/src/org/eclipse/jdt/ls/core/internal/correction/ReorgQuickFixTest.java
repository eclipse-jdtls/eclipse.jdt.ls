/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/ReorgQuickFixTest.java
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 * Microsoft Corporation - adoptions for jdt.ls
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ReorgQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		fJProject1.setOptions(TestOptions.getDefaultOptions());
		when(preferenceManager.getClientPreferences().isResourceOperationSupported()).thenReturn(true);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testUnusedImports() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e2 = new Expected("Organize imports", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testRemoveAllUnusedImports() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove all unused imports", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUnusedImportsInDefaultPackage() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());

		buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e2 = new Expected("Organize imports", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnusedImportOnDemand() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("import java.net.*;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" Vector v;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" Vector v;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());
		Expected e2 = new Expected("Organize imports", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testCollidingImports() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.security.Permission;\n");
		buf.append("import java.security.acl.Permission;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" Permission p;\n");
		buf.append(" Vector v;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.security.Permission;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" Permission p;\n");
		buf.append(" Vector v;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());
		Expected e2 = new Expected("Organize imports", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testWrongPackageStatement() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu);

		Either<Command, CodeAction> codeAction = findAction(codeActions, "Change package declaration to 'test1'");
		assertNotNull(codeAction);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		assertEquals(buf.toString(), evaluateCodeActionCommand(codeAction));

		codeAction = findAction(codeActions, "Move 'E.java' to package 'test2'");
		assertNotNull(codeAction);
		assertRenameFileOperation(codeAction, ResourceUtils.fixURI(cu.getResource().getRawLocationURI()).replace("test1", "test2"));
	}

	private Either<Command, CodeAction> findAction(List<Either<Command, CodeAction>> codeActions, String title) {
		Optional<Either<Command, CodeAction>> any = codeActions.stream().filter((action) -> Objects.equals(title, action.getLeft() == null ? action.getRight().getTitle() : action.getLeft().getTitle())).findFirst();
		return any.isPresent() ? any.get() : null;
	}

	private WorkspaceEdit getWorkspaceEdit(Either<Command, CodeAction> codeAction) {
		Command c = codeAction.isLeft() ? codeAction.getLeft() : codeAction.getRight().getCommand();
		assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
		assertNotNull(c.getArguments());
		assertTrue(c.getArguments().get(0) instanceof WorkspaceEdit);
		return (WorkspaceEdit) c.getArguments().get(0);
	}

	private void assertRenameFileOperation(Either<Command, CodeAction> codeAction, String newUri) {
		WorkspaceEdit edit = getWorkspaceEdit(codeAction);
		List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = edit.getDocumentChanges();
		assertNotNull(documentChanges);
		assertEquals(1, documentChanges.size());
		ResourceOperation resourceOperation = documentChanges.get(0).getRight();
		assertNotNull(resourceOperation);
		assertTrue(resourceOperation instanceof RenameFile);
		assertEquals(newUri, ((RenameFile) resourceOperation).getNewUri());
	}

	@Test
	public void testWrongPackageStatementInEnum() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu);

		Either<Command, CodeAction> codeAction = findAction(codeActions, "Change package declaration to 'test1'");
		assertNotNull(codeAction);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append("}\n");
		assertEquals(buf.toString(), evaluateCodeActionCommand(codeAction));

		codeAction = findAction(codeActions, "Move 'E.java' to package 'test2'");
		assertNotNull(codeAction);
		assertRenameFileOperation(codeAction, ResourceUtils.fixURI(cu.getResource().getRawLocationURI()).replace("test1", "test2"));
	}

	@Test
	public void testWrongPackageStatementFromDefault() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu);

		Either<Command, CodeAction> codeAction = findAction(codeActions, "Remove package declaration 'package test2'");
		assertNotNull(codeAction);
		buf = new StringBuilder();
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		assertEquals(buf.toString(), evaluateCodeActionCommand(codeAction));

		codeAction = findAction(codeActions, "Move 'E.java' to package 'test2'");
		assertNotNull(codeAction);
		assertRenameFileOperation(codeAction, ResourceUtils.fixURI(pack1.getResource().getRawLocation().append("test2/E.java").toFile().toURI()));
	}

	@Test
	public void testWrongDefaultPackageStatement() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test2", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu);

		Either<Command, CodeAction> codeAction = findAction(codeActions, "Add package declaration 'test2;'");
		assertNotNull(codeAction);
		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		assertEquals(buf.toString(), evaluateCodeActionCommand(codeAction));

		codeAction = findAction(codeActions, "Move 'E.java' to the default package");
		assertNotNull(codeAction);
		assertRenameFileOperation(codeAction, ResourceUtils.fixURI(pack1.getResource().getRawLocation().append("../E.java").toFile().toURI()));
	}

	@Test
	public void testWrongTypeName() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Rename type to 'X'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongTypeName_bug180330() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("p", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("public class \\u0042 {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("public class C {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Rename type to 'C'", buf.toString());
		assertCodeActions(cu, e1);

	}

	@Test
	public void testWrongTypeNameButColliding() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Rename type to 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongTypeNameWithConstructor() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append(" public X() {\n");
		buf.append(" X other;\n");
		buf.append(" }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" public E() {\n");
		buf.append(" E other;\n");
		buf.append(" }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Rename type to 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongTypeNameInEnum() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public enum X {\n");
		buf.append(" A;\n");
		buf.append(" X() {\n");
		buf.append(" }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append(" A;\n");
		buf.append(" E() {\n");
		buf.append(" }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Rename type to 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongTypeNameInAnnot() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public @interface X {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public @interface X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public @interface E {\n");
		buf.append("}\n");

		Expected e1 = new Expected("Rename type to 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}
}