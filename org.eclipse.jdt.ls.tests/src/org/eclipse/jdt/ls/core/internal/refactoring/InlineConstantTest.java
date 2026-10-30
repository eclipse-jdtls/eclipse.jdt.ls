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
import java.util.Map;

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
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class InlineConstantTest extends AbstractSelectionTest {

	private static final String INLINE_CONSTANT = ActionMessages.InlineConstantRefactoringAction_label;

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
	public void testInlineConstant_DeclarationSelected() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private static final String /*]*/LOGGER_NAME/*[*/ = \"TEST.E\";\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(LOGGER_NAME);\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        String value = LOGGER_NAME;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(\"TEST.E\");\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        String value = \"TEST.E\";\n");
		buf.append("    }\n");
		buf.append("}\n");

		assertInlineConstant(cu, buf.toString());
	}

	@Test
	public void testInlineConstant_InvocationSelected() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private static final String LOGGER_NAME = \"TEST.E\";\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(/*]*/LOGGER_NAME/*[*/);\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        String value = LOGGER_NAME;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private static final String LOGGER_NAME = \"TEST.E\";\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(/*]*/\"TEST.E\"/*[*/);\n");
		buf.append("    }\n");
		buf.append("    public void bar() {\n");
		buf.append("        String value = LOGGER_NAME;\n");
		buf.append("    }\n");
		buf.append("}\n");

		assertInlineConstant(cu, buf.toString());
	}

	@Test
	public void testInlineConstant_NoReference() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private static final String /*]*/LOGGER_NAME/*[*/ = \"TEST.E\";\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		assertCodeActionNotExists(cu, INLINE_CONSTANT);
		assertEquals(buf.toString(), cu.getSource());
	}

	@Test
	public void testInlineConstant_NoReferencesWithImports() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.Map;\n");
		buf.append("public class E {\n");
		buf.append("    public static final Map /*]*/map/*[*/ = new HashMap<>();\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		assertCodeActionNotExists(cu, INLINE_CONSTANT);
		assertEquals(buf.toString(), cu.getSource());
	}

	private void assertInlineConstant(ICompilationUnit unit, String expectedSource) throws Exception {
		var action = evaluateCodeActions(unit, getRange(unit, null)).stream().filter(candidate -> INLINE_CONSTANT.equals(getTitle(candidate))).findFirst().orElse(null);
		assertNotNull(action, "Inline Constant should be offered");
		assertEquals(CodeActionKind.RefactorInline, action.getRight().getKind());
		assertNotNull(action.getRight().getCommand());
		assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, action.getRight().getCommand().getCommand());
		assertEquals(RefactorProposalUtility.INLINE_CONSTANT_COMMAND, action.getRight().getCommand().getArguments().get(0));
		var params = new GetRefactorEditParams(RefactorProposalUtility.INLINE_CONSTANT_COMMAND, CodeActionUtil.constructCodeActionParams(unit, getRange(unit, null)));
		var result = GetRefactorEditHandler.getEditsForRefactor(params);
		assertNotNull(result);
		assertNull(result.errorMessage, result.errorMessage);
		assertNotNull(result.edit);
		assertNull(result.confirmationToken);
		assertEquals(expectedSource, evaluateWorkspaceEdit(result.edit));
	}

	@Test
	public void testInlineConstantDeclaration() throws Exception {
		ICompilationUnit unit = unit("class E { static final int /*]*/VALUE/*[*/ = 7; int a() { return VALUE; } int b() { return VALUE; } }");
		assertCommand(unit, ActionMessages.InlineConstantRefactoringAction_label, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		assertNormalEdit(result);
		String source = AbstractQuickFixTest.evaluateWorkspaceEdit(result.edit);
		assertFalse(source.contains("VALUE"), source);
		assertEquals(2, source.split("return 7", -1).length - 1, source);
	}

	@Test
	public void testInlineConstantReference() throws Exception {
		ICompilationUnit unit = unit("class E { static final int VALUE = 7; int a() { return /*]*/VALUE/*[*/; } int b() { return VALUE; } }");
		assertCommand(unit, ActionMessages.InlineConstantRefactoringAction_label, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		assertNormalEdit(result);
		String source = AbstractQuickFixTest.evaluateWorkspaceEdit(result.edit);
		assertTrue(source.contains("static final int VALUE = 7"), source);
		assertTrue(source.contains("/*]*/7/*[*/"), source);
		assertTrue(source.contains("int b() { return VALUE; }"), source);
	}

	@Test
	public void testInlineConstantReferencesInAnotherFile() throws Exception {
		ICompilationUnit unit = unit("public class E { public static final int /*]*/VALUE/*[*/ = 7; }");
		testSourceFolder.createPackageFragment("test", false, null).createCompilationUnit("Consumer.java",
				"package test; class Consumer { int m() { return E.VALUE; } }", false, null);
		assertCommand(unit, ActionMessages.InlineConstantRefactoringAction_label, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		assertNormalEdit(result);
		assertEquals(2, result.edit.getChanges().size());
		for (var change : result.edit.getChanges().entrySet()) {
			String inlined = AbstractQuickFixTest.evaluateChanges(Map.of(change.getKey(), change.getValue()));
			assertFalse(inlined.contains("VALUE"), inlined);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testFatalInlineConstantFailure(boolean confirmationSupported) throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(confirmationSupported);
		ICompilationUnit unit = unit("class E { static final int /*]*/VALUE/*[*/; static { VALUE = 1; } int m() { return VALUE; } }");
		String original = unit.getSource();
		assertCodeActionNotExists(unit, INLINE_CONSTANT);
		assertFailure(request(unit, RefactorProposalUtility.INLINE_CONSTANT_COMMAND));
		assertEquals(original, unit.getSource());
	}

	@Test
	public void testNormalInlineConstantReturnsEditWithoutConfirmationSupport() throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(false);
		ICompilationUnit unit = unit("class E { static final int /*]*/VALUE/*[*/ = 7; int m() { return VALUE; } }");
		assertFalse(evaluateWorkspaceEdit(getLegacyEdit(unit, false)).contains("VALUE"));
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		assertNormalEdit(result);
		assertFalse(AbstractQuickFixTest.evaluateWorkspaceEdit(result.edit).contains("VALUE"));
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testUnusedConstantIsNotOffered(boolean confirmationSupported) throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(confirmationSupported);
		ICompilationUnit unit = unit("class E { static final int /*]*/VALUE/*[*/ = 7; }");
		String original = unit.getSource();
		assertCodeActionNotExists(unit, INLINE_CONSTANT);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		assertFailure(result);
		assertEquals("Failed to inline constant because no references were found.", result.errorMessage);
		assertEquals(original, unit.getSource());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testInlineConstantPreservesLegacyEdit(boolean declarationSelected) throws Exception {
		ICompilationUnit unit = unit(declarationSelected
				? "class E { static final int /*]*/VALUE/*[*/ = 7; int m() { return VALUE + VALUE; } }"
				: "class E { static final int VALUE = 7; int m() { return /*]*/VALUE/*[*/ + VALUE; } }");
		String original = unit.getSource();
		String legacySource = evaluateWorkspaceEdit(getLegacyEdit(unit, false));
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(true);
		assertCommand(unit, INLINE_CONSTANT, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_CONSTANT_COMMAND);
		assertNormalEdit(result);
		assertEquals(legacySource, evaluateWorkspaceEdit(result.edit));
		assertEquals(original, unit.getSource());
	}

	@ParameterizedTest
	@CsvSource({ "false, true", "false, false", "true, true", "true, false" })
	public void testLegacyInlineConstantCodeAction(boolean resolveSupported, boolean declarationSelected) throws Exception {
		ICompilationUnit unit = unit(declarationSelected
				? "class E { static final int /*]*/VALUE/*[*/ = 7; int m() { return VALUE; } }"
				: "class E { static final int VALUE = 7; int m() { return /*]*/VALUE/*[*/; } }");
		String original = unit.getSource();
		String inlined = evaluateWorkspaceEdit(getLegacyEdit(unit, resolveSupported));
		assertEquals(!declarationSelected, inlined.contains("static final int VALUE"), inlined);
		assertTrue(inlined.contains("7"), inlined);
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
		var candidate = evaluateCodeActions(unit, getRange(unit, null)).stream().filter(action -> INLINE_CONSTANT.equals(getTitle(action))).findFirst().orElse(null);
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
