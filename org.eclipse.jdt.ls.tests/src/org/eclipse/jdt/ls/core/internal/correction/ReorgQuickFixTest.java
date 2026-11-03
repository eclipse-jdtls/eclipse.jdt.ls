/*******************************************************************************
 * Copyright (c) 2000, 2026 IBM Corporation and others.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReorgQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		fJProject1.setOptions(TestOptions.getDefaultOptions());
		// Tests that return no edits do not query this capability.
		lenient().when(preferenceManager.getClientPreferences().isResourceOperationSupported()).thenReturn(true);
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

		Either<Command, CodeAction> codeAction = findAction(codeActions, "Correct package declaration");
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
		if (codeAction.isLeft()) {
			throw new AssertionError("Unexpected Command-only action: " + codeAction.getLeft().getCommand());
		}
		WorkspaceEdit e = codeAction.getRight().getEdit();
		assertNotNull(e);
		return e;
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

		Either<Command, CodeAction> codeAction = findAction(codeActions, "Correct package declaration");
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

		Either<Command, CodeAction> codeAction = findAction(codeActions, "Correct package declaration");
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

		Either<Command, CodeAction> codeAction = findAction(codeActions, "Correct package declaration");
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

	@Test
	public void testNoJavaApplyWorkspaceEditCommand() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(false);
		when(preferenceManager.getClientPreferences().isSupportedCodeActionKind(CodeActionKind.SourceOrganizeImports)).thenReturn(true);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		List<Either<Command, CodeAction>> actions = evaluateCodeActions(cu);
		assertFalse(
			actions.stream().anyMatch(a ->
				a.isLeft() && "java.apply.workspaceEdit".equals(a.getLeft().getCommand())
			),
			"Should not return legacy java.apply.workspaceEdit Command"
		);
		assertFalse(
			actions.stream().anyMatch(a ->
				a.isRight() && a.getRight().getCommand() != null && "java.apply.workspaceEdit".equals(a.getRight().getCommand().getCommand())
			),
			"Should not embed legacy java.apply.workspaceEdit as CodeAction.command"
		);

		CodeAction organize = actions.stream()
			.filter(Either::isRight)
			.map(Either::getRight)
			.filter(ca -> "Organize imports".equals(ca.getTitle()))
			.findFirst()
			.orElse(null);
		assertNotNull(organize, "Expected 'Organize imports' code action");
		assertNotNull(organize.getEdit(), "'Organize imports' should carry WorkspaceEdit");
	}

	@Test
	public void testBumpRequiredCompliance14() throws Exception {
		Map<String, String> options= fJProject1.getOptions(true);
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E");
		buf.append("	public void foo(int a) {\n");
		buf.append("		switch (a) {\n");
		buf.append("			case 1,2 -> System.out.println(\"abc\");\n");
		buf.append("			default -> System.out.println(\"def\");\n");
		buf.append("		}\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		assertCodeActionExists(cu, "Change project compiler compliance to 14");
	}

	@Test
	public void testBumpRequiredCompliance15() throws Exception {
		Map<String, String> options = fJProject1.getOptions(true);
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E");
		buf.append("	public void foo(int a) {\n");
		buf.append("		String s = \"\"\"\n");
		buf.append("			abcdefg\n");
		buf.append("			\"\"\";\n");
		buf.append("		}\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		assertCodeActionExists(cu, "Change project compiler compliance to 15");
	}

	@Test
	public void testImportNotFoundProposals() throws Exception {
		fSourceFolder.createPackageFragment("test2", false, null);
		IPackageFragment pack = fSourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", """
				package test1;
				import test2.Missing;
				public class E {
				}
				""", false, null);
		var actions = getQuickFixes(cu, "test2.Missing");
		String expectedClass = """
				package test2;

				/**
				 * Missing
				 */
				public class Missing {

				}
				""";
		String expectedRemoval = """
				package test1;
				public class E {
				}
				""";
		assertCodeActions(actions,
				new Expected("Create class 'Missing' in package 'test2'", expectedClass, CodeActionKind.QuickFix),
				new Expected("Remove unused import", expectedRemoval, CodeActionKind.QuickFix));
		for (String kind : List.of("interface", "enum", "annotation")) {
			String label = "Create " + kind + " 'Missing' in package 'test2'";
			assertTrue(actions.stream().anyMatch(action -> label.equals(getTitle(action))), "Should prompt code action: " + label);
		}
		assertFalse(actions.stream().anyMatch(action -> getTitle(action).startsWith("Add 'requires ")));
	}

	@Test
	public void testAddRequiresForImport() throws Exception {
		ICompilationUnit moduleInfo = createModuleProject();
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", """
				package test;
				import dep.One;
				public class Test {
				    One one;
				}
				""", false, null);
		assertRequiresModuleProposal(cu, "dep.One", moduleInfo);
	}

	@Test
	public void testAddRequiresForStaticImport() throws Exception {
		ICompilationUnit moduleInfo = createModuleProject();
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", """
				package test;
				import static dep.One.VALUE;
				public class Test {
				    int value = VALUE;
				}
				""", false, null);
		assertRequiresModuleProposal(cu, "dep.One", moduleInfo);
	}

	@Test
	public void testAddRequiresForStaticOnDemandImport() throws Exception {
		ICompilationUnit moduleInfo = createModuleProject();
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", """
				package test;
				import static dep.One.*;
				public class Test {
				    int value = VALUE;
				}
				""", false, null);
		assertRequiresModuleProposal(cu, "dep.One", moduleInfo);
	}

	@Test
	public void testAddRequiresForFullyQualifiedType() throws Exception {
		ICompilationUnit moduleInfo = createModuleProject();
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", """
				package test;
				public class Test {
				    dep.One one;
				}
				""", false, null);
		assertRequiresModuleProposal(cu, "dep.One", moduleInfo);
	}

	@Test
	public void testNoDuplicateRequires() throws Exception {
		ICompilationUnit moduleInfo = createModuleProject();
		moduleInfo.getBuffer().setContents("""
				module test {
				    requires dependency;
				}
				""");
		moduleInfo.save(null, true);
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", """
				package test;
				import dep.One;
				public class Test {
				    One one;
				}
				""", false, null);
		assertCodeActionNotExists(cu, CodeActionUtil.getRange(cu, "dep.One"), "Add 'requires dependency' to module-info.java");
	}

	@Test
	public void testNoRequiresForNonModularProject() throws Exception {
		ICompilationUnit moduleInfo = createModuleProject();
		moduleInfo.delete(false, null);
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", """
				package test;
				import dep.One;
				public class Test {
				    One one;
				}
				""", false, null);
		assertCodeActionNotExists(cu, CodeActionUtil.getRange(cu, "dep.One"), "Add 'requires dependency' to module-info.java");
	}

	private ICompilationUnit createModuleProject() throws Exception {
		IJavaProject dependency = newEmptyProject("dependency");
		Map<String, String> options = fJProject1.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_9, options);
		dependency.setOptions(options);
		fJProject1.setOptions(options);

		IPackageFragmentRoot dependencySource = dependency.getPackageFragmentRoot(dependency.getProject().getFolder("src"));
		dependencySource.createPackageFragment("", false, null).createCompilationUnit("module-info.java", """
				module dependency {
				    exports dep;
				}
				""", false, null);
		dependencySource.createPackageFragment("dep", false, null).createCompilationUnit("One.java", """
				package dep;
				public class One {
				    public static final int VALUE = 1;
				}
				""", false, null);

		IClasspathAttribute[] attributes = { JavaCore.newClasspathAttribute(IClasspathAttribute.MODULE, "true") };
		IClasspathEntry entry = JavaCore.newProjectEntry(dependency.getPath(), null, false, attributes, false);
		JavaProjectHelper.addToClasspath(fJProject1, entry);
		return fSourceFolder.createPackageFragment("", false, null).createCompilationUnit("module-info.java", """
				module test {
				}
				""", false, null);
	}

	private void assertRequiresModuleProposal(ICompilationUnit cu, String selection, ICompilationUnit moduleInfo) throws Exception {
		var actions = getQuickFixes(cu, selection);
		String label = "Add 'requires dependency' to module-info.java";
		String expected = """
				module test {
				    requires dependency;
				}
				""";
		assertCodeActions(actions, new Expected(label, expected, CodeActionKind.QuickFix));
		CodeAction action = actions.stream().filter(a -> label.equals(getTitle(a))).findFirst().orElseThrow().getRight();
		List<Either<TextDocumentEdit, ResourceOperation>> changes = action.getEdit().getDocumentChanges();
		assertEquals(1, changes.size());
		assertEquals(JDTUtils.toURI(moduleInfo), changes.get(0).getLeft().getTextDocument().getUri());
	}

	private List<Either<Command, CodeAction>> getQuickFixes(ICompilationUnit cu, String selection) throws Exception {
		var astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
		int start = cu.getSource().indexOf(selection);
		int end = start + selection.length();
		var problems = Arrays.stream(astRoot.getProblems()).filter(p -> p.getSourceStart() < end && p.getSourceEnd() >= start).toList();
		CodeActionContext context = new CodeActionContext(DiagnosticsHandler.toDiagnosticsArray(cu, problems, true));
		context.setOnly(List.of(CodeActionKind.QuickFix));
		CodeActionParams params = new CodeActionParams(new TextDocumentIdentifier(JDTUtils.toURI(cu)), CodeActionUtil.getRange(cu, selection), context);
		return new CodeActionHandler(preferenceManager).getCodeActionCommands(params, new NullProgressMonitor());
	}

}
