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

package org.eclipse.jdt.ls.core.internal.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.correction.AbstractQuickFixTest;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionResolveHandler;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.GetRefactorEditParams;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.RefactorWorkspaceEdit;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class InlineMethodTest extends AbstractSelectionTest {

	private static final String INLINE_METHOD = ActionMessages.InlineMethodRefactoringAction_label;

	private IJavaProject testProject;

	private IPackageFragmentRoot testSourceFolder;

	@BeforeEach
	public void setup() throws Exception {
		testProject = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		testProject.setOptions(options);
		testSourceFolder = testProject.getPackageFragmentRoot(testProject.getProject().getFolder("src"));
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(true);
	}

	@Test
	public void testInlineMethod_DeclarationSelected() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public String /*]*/foo/*[*/() {\n");
		buf.append("        String temp = \"method declaration\";\n");
		buf.append("        return temp;\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        String value = foo();\n");
		buf.append("    }\n");
		buf.append("    public void bar2() {\n");
		buf.append("        String value = foo();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void bar() {\n");
		buf.append("        String temp = \"method declaration\";\n");
		buf.append("        String value = temp;\n");
		buf.append("    }\n");
		buf.append("    public void bar2() {\n");
		buf.append("        String temp = \"method declaration\";\n");
		buf.append("        String value = temp;\n");
		buf.append("    }\n");
		buf.append("}\n");

		assertCommand(cu, INLINE_METHOD, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		RefactorWorkspaceEdit result = request(cu, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertNormalEdit(result);
		assertEquals(buf.toString(), evaluateWorkspaceEdit(result.edit));
	}

	@Test
	public void testInlineMethod_InvocationSelected() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo() {\n");
		buf.append("        String temp = \"method declaration\";\n");
		buf.append("        return temp;\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        String value = /*]*/foo/*[*/();\n");
		buf.append("    }\n");
		buf.append("    public void bar2() {\n");
		buf.append("        String value = foo();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo() {\n");
		buf.append("        String temp = \"method declaration\";\n");
		buf.append("        return temp;\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        String temp = \"method declaration\";\n");
		buf.append("        String value = /*]*/temp;\n");
		buf.append("    }\n");
		buf.append("    public void bar2() {\n");
		buf.append("        String value = foo();\n");
		buf.append("    }\n");
		buf.append("}\n");

		assertCommand(cu, INLINE_METHOD, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		RefactorWorkspaceEdit result = request(cu, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertNormalEdit(result);
		assertEquals(buf.toString(), evaluateWorkspaceEdit(result.edit));
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testInlineMethod_OverriddenMethodDeclarationWithNoEditIsNotOffered(boolean confirmationSupported) throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(confirmationSupported);
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		String source = """
				package test;

				public class Main {
				    public static void main(String[] args) {
				        A a1 = new A();
				        A a2 = new B();

				        a1.test();
				        a2.test();
				    }
				}

				class A {
				    int /*]*/f/*[*/() {
				        return 1;
				    }

				    void test() {
				        int x = f();
				        System.out.println(x);
				    }
				}

				class B extends A {
				    @Override
				    int f() {
				        return 2;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Main.java", source, false, null);
		Range selection = getRange(cu, null);
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);

		assertCodeActionNotExists(cu, selection, INLINE_METHOD);

		GetRefactorEditParams editParams = new GetRefactorEditParams(RefactorProposalUtility.INLINE_METHOD_COMMAND, params);
		RefactorWorkspaceEdit refactorEdit = GetRefactorEditHandler.getEditsForRefactor(editParams);
		assertNotNull(refactorEdit);
		assertNull(refactorEdit.edit);
		assertNotNull(refactorEdit.errorMessage);
		assertNull(refactorEdit.confirmationToken);
		assertEquals(source, cu.getSource());
	}

	@Test
	public void testInlineMethod_OverriddenMethodInvocationRequiresConfirmation() throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(true);
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		String source = """
				package test;

				class A {
				    int f() {
				        return 1;
				    }

				    void test() {
				        int x = /*]*/f/*[*/();
				        System.out.println(x);
				    }
				}

				class B extends A {
				    @Override
				    int f() {
				        return 2;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", source, false, null);
		Range selection = getRange(cu, null);
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);

		List<Either<Command, CodeAction>> actions = evaluateCodeActions(cu, selection);
		Either<Command, CodeAction> inlineAction = actions.stream().filter(action -> INLINE_METHOD.equals(getTitle(action))).findFirst().orElse(null);
		assertNotNull(inlineAction);
		assertTrue(inlineAction.isRight());
		assertNull(inlineAction.getRight().getEdit());
		Command command = inlineAction.getRight().getCommand();
		assertNotNull(command);
		assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, command.getCommand());
		assertEquals(RefactorProposalUtility.INLINE_METHOD_COMMAND, command.getArguments().get(0));

		GetRefactorEditParams editParams = new GetRefactorEditParams(RefactorProposalUtility.INLINE_METHOD_COMMAND, params);
		RefactorWorkspaceEdit refactorEdit = GetRefactorEditHandler.getEditsForRefactor(editParams);
		assertNotNull(refactorEdit);
		assertNull(refactorEdit.edit);
		assertNotNull(refactorEdit.errorMessage);
		assertNotNull(refactorEdit.confirmationToken);

		GetRefactorEditParams confirmedParams = new GetRefactorEditParams(RefactorProposalUtility.INLINE_METHOD_COMMAND, null, params, null, refactorEdit.confirmationToken);
		params.getContext().setDiagnostics(List.of(new Diagnostic(params.getRange(), "Refreshed client diagnostics")));
		params.setWorkDoneToken("updated-progress-token");
		params.setPartialResultToken("updated-results-token");
		RefactorWorkspaceEdit confirmedEdit = GetRefactorEditHandler.getEditsForRefactor(confirmedParams);
		assertNotNull(confirmedEdit);
		assertNotNull(confirmedEdit.edit);
		assertNull(confirmedEdit.errorMessage);
		String inlinedSource = AbstractQuickFixTest.evaluateWorkspaceEdit(confirmedEdit.edit);
		assertTrue(inlinedSource.contains("int x = /*]*/1;"), inlinedSource);
		assertTrue(inlinedSource.contains("int f()"), inlinedSource);
		assertTrue(inlinedSource.contains("class B extends A"), inlinedSource);

		cu.getBuffer().setContents(source + "\n// source changed\n");
		cu.save(null, true);
		GetRefactorEditParams staleParams = new GetRefactorEditParams(RefactorProposalUtility.INLINE_METHOD_COMMAND, null, params, null, refactorEdit.confirmationToken);
		RefactorWorkspaceEdit staleEdit = GetRefactorEditHandler.getEditsForRefactor(staleParams);
		assertNotNull(staleEdit);
		assertNull(staleEdit.edit);
		assertNull(staleEdit.confirmationToken);
		assertTrue(staleEdit.errorMessage.contains("conditions changed"), staleEdit.errorMessage);
	}

	@Test
	public void testInlineMethodNormalResponse() throws Exception {
		ICompilationUnit unit = unit("class E { int /*]*/answer/*[*/() { return 7; } int m() { return answer(); } }");
		assertCommand(unit, ActionMessages.InlineMethodRefactoringAction_label, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertNormalEdit(result);
		assertFalse(AbstractQuickFixTest.evaluateWorkspaceEdit(result.edit).contains("answer"));
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testUnusedMethodCanBeInlined(boolean confirmationSupported) throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(confirmationSupported);
		ICompilationUnit unit = unit("class E { int /*]*/answer/*[*/() { return 7; } }");
		String original = unit.getSource();
		if (confirmationSupported) {
			assertCommand(unit, INLINE_METHOD, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		} else {
			assertFalse(evaluateWorkspaceEdit(getLegacyEdit(unit, false)).contains("answer"));
		}
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertNormalEdit(result);
		assertFalse(evaluateWorkspaceEdit(result.edit).contains("answer"));
		assertEquals(original, unit.getSource());
	}

	@Test
	public void testInlineMethodReferenceIsNotOffered() throws Exception {
		ICompilationUnit unit = unit("class E { int answer() { return 7; } java.util.function.IntSupplier supplier = this::/*]*/answer/*[*/; }");
		String original = unit.getSource();
		assertCodeActionNotExists(unit, INLINE_METHOD);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertFailure(result);
		assertTrue(result.errorMessage.contains("selection"), result.errorMessage);
		assertEquals(original, unit.getSource());
	}

	@Test
	public void testInlineMethodReferencesInAnotherFile() throws Exception {
		ICompilationUnit unit = unit("public class E { public int /*]*/answer/*[*/() { return 7; } }");
		testSourceFolder.createPackageFragment("test", false, null).createCompilationUnit("Consumer.java",
				"package test; class Consumer { int m() { return new E().answer(); } }", false, null);
		assertCommand(unit, INLINE_METHOD, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertNormalEdit(request(unit, RefactorProposalUtility.INLINE_METHOD_COMMAND));
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testUnusedNativeMethodWithFatalErrorIsNotOffered(boolean confirmationSupported) throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(confirmationSupported);
		ICompilationUnit unit = unit("class E { static native int /*]*/answer/*[*/(); }");
		String original = unit.getSource();
		assertCodeActionNotExists(unit, INLINE_METHOD);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertFailure(result);
		assertTrue(result.errorMessage.toLowerCase().contains("native"), result.errorMessage);
		assertEquals(original, unit.getSource());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testFatalInlineMethodFailure(boolean confirmationSupported) throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(confirmationSupported);
		ICompilationUnit unit = unit("class E { int /*]*/answer/*[*/() { return answer(); } int m() { return answer(); } }");
		String original = unit.getSource();
		assertCodeActionNotExists(unit, INLINE_METHOD);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertFailure(result);
		assertTrue(result.errorMessage.toLowerCase().contains("recurs"), result.errorMessage);
		assertEquals(original, unit.getSource());
	}

	@Test
	public void testNonFatalInlineMethodReturnsEditWithoutConfirmationSupport() throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(false);
		ICompilationUnit unit = unit("class E { int answer() { return 7; } int m() { int result = /*]*/answer/*[*/(); return result; } } class Child extends E { @Override int answer() { return 8; } }");
		String original = unit.getSource();
		WorkspaceEdit legacyEdit = getLegacyEdit(unit, false);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertNormalEdit(result);
		String inlined = AbstractQuickFixTest.evaluateWorkspaceEdit(result.edit);
		assertTrue(inlined.contains("int result = /*]*/7;"), inlined);
		assertEquals(inlined, evaluateWorkspaceEdit(legacyEdit));
		assertEquals(original, unit.getSource());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testInlineMethodPreservesLegacyEdit(boolean declarationSelected) throws Exception {
		ICompilationUnit unit = unit(declarationSelected
				? "class E { int /*]*/answer/*[*/() { return 7; } int m() { return answer() + answer(); } }"
				: "class E { int answer() { return 7; } int m() { return /*]*/answer/*[*/() + answer(); } }");
		String original = unit.getSource();
		String legacySource = evaluateWorkspaceEdit(getLegacyEdit(unit, false));
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(true);
		assertCommand(unit, INLINE_METHOD, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_METHOD_COMMAND);
		assertNormalEdit(result);
		assertEquals(legacySource, evaluateWorkspaceEdit(result.edit));
		assertEquals(original, unit.getSource());
	}

	@ParameterizedTest
	@CsvSource({ "false, true", "false, false", "true, true", "true, false" })
	public void testLegacyInlineMethodCodeAction(boolean resolveSupported, boolean declarationSelected) throws Exception {
		ICompilationUnit unit = unit(declarationSelected
				? "class E { int /*]*/answer/*[*/() { return 7; } int m() { return answer(); } }"
				: "class E { int answer() { return 7; } int m() { return /*]*/answer/*[*/(); } }");
		String original = unit.getSource();
		String inlined = evaluateWorkspaceEdit(getLegacyEdit(unit, resolveSupported));
		assertFalse(inlined.contains("answer();"), inlined);
		assertEquals(!declarationSelected, inlined.contains("int answer()"), inlined);
		assertEquals(original, unit.getSource());
	}

	private ICompilationUnit unit(String source) throws Exception {
		return testSourceFolder.createPackageFragment("test", false, null).createCompilationUnit("E.java", "package test;\n" + source, false, null);
	}

	private void assertCommand(ICompilationUnit unit, String title, String operation) throws Exception {
		var action = evaluateCodeActions(unit, getRange(unit, null)).stream().filter(candidate -> title.equals(getTitle(candidate))).findFirst().orElse(null);
		assertNotNull(action, title);
		assertTrue(action.isRight());
		assertEquals(CodeActionKind.RefactorInline, action.getRight().getKind());
		assertNull(action.getRight().getEdit());
		Command command = action.getRight().getCommand();
		assertNotNull(command);
		assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, command.getCommand());
		assertEquals(operation, command.getArguments().get(0));
	}

	private RefactorWorkspaceEdit request(ICompilationUnit unit, String operation) throws Exception {
		return GetRefactorEditHandler.getEditsForRefactor(new GetRefactorEditParams(operation, CodeActionUtil.constructCodeActionParams(unit, getRange(unit, null))));
	}

	private WorkspaceEdit getLegacyEdit(ICompilationUnit unit, boolean resolveSupported) throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(false);
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(resolveSupported);
		var candidate = evaluateCodeActions(unit, getRange(unit, null)).stream().filter(action -> INLINE_METHOD.equals(getTitle(action))).findFirst().orElse(null);
		assertNotNull(candidate);
		assertTrue(candidate.isRight());
		CodeAction action = candidate.getRight();
		assertEquals(CodeActionKind.RefactorInline, action.getKind());
		assertNull(action.getCommand(), "Older clients must not receive the new Inline command");
		if (resolveSupported) {
			assertNull(action.getEdit());
			assertNotNull(action.getData());
			action = new CodeActionResolveHandler().resolve(action, new NullProgressMonitor());
		}
		assertNull(action.getCommand());
		assertNotNull(action.getEdit());
		return action.getEdit();
	}

	private void assertNormalEdit(RefactorWorkspaceEdit result) {
		assertNotNull(result);
		assertNotNull(result.edit, result.errorMessage);
		assertNull(result.errorMessage);
		assertNull(result.confirmationToken);
	}

	private void assertFailure(RefactorWorkspaceEdit result) {
		assertNotNull(result);
		assertNull(result.edit);
		assertNotNull(result.errorMessage);
		assertNull(result.confirmationToken);
	}
}
