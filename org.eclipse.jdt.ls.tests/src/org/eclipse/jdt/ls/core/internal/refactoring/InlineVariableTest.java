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
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionResolveHandler;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.GetRefactorEditParams;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.RefactorWorkspaceEdit;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class InlineVariableTest extends AbstractSelectionTest {

	private static final String INLINE_LOCAL_VARIABLE = CorrectionMessages.QuickAssistProcessor_inline_local_description;

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
	public void testInlineLocalVariable() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String[] parameters, int j) {\n");
		buf.append("        int /*]*/temp/*[*/ = parameters.length + j;\n");
		buf.append("        int temp1 = temp;\n");
		buf.append("        System.out.println(temp);\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String[] parameters, int j) {\n");
		buf.append("        int temp1 = parameters.length + j;\n");
		buf.append("        System.out.println(parameters.length + j);\n");
		buf.append("    }\n");
		buf.append("}\n");

		assertCommand(cu, INLINE_LOCAL_VARIABLE, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
		RefactorWorkspaceEdit result = request(cu, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
		assertNormalEdit(result);
		assertEquals(buf.toString(), evaluateWorkspaceEdit(result.edit));
	}

	@Test
	public void testInlineLocalVariableWithNoReferences() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String[] parameters, int j) {\n");
		buf.append("        int temp = parameters.length + j;\n");
		buf.append("        int /*]*/temp1/*[*/ = temp;\n");
		buf.append("        System.out.println(temp);\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		String original = cu.getSource();
		assertCodeActionNotExists(cu, INLINE_LOCAL_VARIABLE);
		RefactorWorkspaceEdit result = request(cu, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
		assertFailure(result);
		assertTrue(result.errorMessage.contains("no references"), result.errorMessage);
		assertEquals(original, cu.getSource());
	}

	@Test
	public void testInlineLocalVariableDeclaration() throws Exception {
		assertInlineLocalVariable("class E { int m() { int /*]*/value/*[*/ = 7; return value + value; } }");
	}

	@Test
	public void testInlineLocalVariableReference() throws Exception {
		assertInlineLocalVariable("class E { int m() { int value = 7; return /*]*/value/*[*/ + value; } }");
	}

	private void assertInlineLocalVariable(String source) throws Exception {
		ICompilationUnit unit = unit(source);
		assertCommand(unit, CorrectionMessages.QuickAssistProcessor_inline_local_description, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
		assertNormalEdit(result);
		String inlined = AbstractQuickFixTest.evaluateWorkspaceEdit(result.edit);
		assertFalse(inlined.contains("value"), inlined);
		assertTrue(inlined.contains("7"), inlined);
	}

	@Test
	public void testInlineLocalVariableCompileProblemIsNotOfferedButDirectRequestRequiresConfirmation() throws Exception {
		ICompilationUnit unit = unit("class E { String m() { Integer /*]*/value/*[*/ = null; return value.toString(); } }");
		assertCodeActionNotExists(unit, INLINE_LOCAL_VARIABLE);
		assertConfirmation(unit, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testFatalInlineLocalVariableFailure(boolean confirmationSupported) throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(confirmationSupported);
		ICompilationUnit unit = unit("class E { int m() { int /*]*/value/*[*/ = 1; value = 2; return value; } }");
		String original = unit.getSource();
		assertCodeActionNotExists(unit, INLINE_LOCAL_VARIABLE);
		assertFailure(request(unit, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND));
		assertEquals(original, unit.getSource());
	}

	@Test
	public void testNonFatalInlineLocalVariableDirectRequestWithoutConfirmationSupport() throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(false);
		ICompilationUnit unit = unit("class E { String m() { Integer /*]*/value/*[*/ = null; return value.toString(); } }");
		String original = unit.getSource();
		// The original local-variable proposal required all conditions to be OK.
		assertCodeActionNotExists(unit, INLINE_LOCAL_VARIABLE);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
		assertNormalEdit(result);
		assertFalse(AbstractQuickFixTest.evaluateWorkspaceEdit(result.edit).contains("value"));
		assertEquals(original, unit.getSource());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testUninitializedInlineLocalVariableFailure(boolean confirmationSupported) throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(confirmationSupported);
		ICompilationUnit unit = unit("class E { void m() { int /*]*/value/*[*/; } }");
		String original = unit.getSource();
		assertCodeActionNotExists(unit, INLINE_LOCAL_VARIABLE);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
		assertFailure(result);
		assertTrue(result.errorMessage.contains("not initialized"), result.errorMessage);
		assertEquals(original, unit.getSource());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testInlineLocalVariablePreservesLegacyEdit(boolean declarationSelected) throws Exception {
		ICompilationUnit unit = unit(declarationSelected
				? "class E { int m() { int /*]*/value/*[*/ = 7; return value + value; } }"
				: "class E { int m() { int value = 7; return /*]*/value/*[*/ + value; } }");
		String original = unit.getSource();
		String legacySource = evaluateWorkspaceEdit(getLegacyEdit(unit, false));
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(true);
		assertCommand(unit, INLINE_LOCAL_VARIABLE, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
		RefactorWorkspaceEdit result = request(unit, RefactorProposalUtility.INLINE_LOCAL_VARIABLE_COMMAND);
		assertNormalEdit(result);
		assertEquals(legacySource, evaluateWorkspaceEdit(result.edit));
		assertEquals(original, unit.getSource());
	}

	@ParameterizedTest
	@CsvSource({ "false, true", "false, false", "true, true", "true, false" })
	public void testLegacyInlineLocalVariableCodeAction(boolean resolveSupported, boolean declarationSelected) throws Exception {
		ICompilationUnit unit = unit(declarationSelected
				? "class E { int m() { int /*]*/value/*[*/ = 7; return value + value; } }"
				: "class E { int m() { int value = 7; return /*]*/value/*[*/ + value; } }");
		String original = unit.getSource();
		String inlined = evaluateWorkspaceEdit(getLegacyEdit(unit, resolveSupported));
		assertFalse(inlined.contains("value"), inlined);
		assertTrue(inlined.contains("7"), inlined);
		assertEquals(original, unit.getSource());
	}

	@Test
	public void testUnusedLocalVariableIsNotOfferedToLegacyClient() throws Exception {
		when(preferenceManager.getClientPreferences().isInlineRefactoringConfirmationSupported()).thenReturn(false);
		ICompilationUnit unit = unit("class E { void m() { int /*]*/value/*[*/ = 7; } }");
		assertCodeActionNotExists(unit, INLINE_LOCAL_VARIABLE);
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
		var candidate = evaluateCodeActions(unit, getRange(unit, null)).stream().filter(action -> INLINE_LOCAL_VARIABLE.equals(getTitle(action))).findFirst().orElse(null);
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

	private RefactorWorkspaceEdit assertConfirmation(ICompilationUnit unit, String operation) throws Exception {
		CodeActionParams context = CodeActionUtil.constructCodeActionParams(unit, getRange(unit, null));
		RefactorWorkspaceEdit first = GetRefactorEditHandler.getEditsForRefactor(new GetRefactorEditParams(operation, context));
		assertNotNull(first);
		assertNull(first.edit);
		assertNotNull(first.errorMessage);
		assertNotNull(first.confirmationToken, first.errorMessage);
		RefactorWorkspaceEdit invalid = GetRefactorEditHandler.getEditsForRefactor(new GetRefactorEditParams(operation, null, context, null, "invalid-token"));
		assertFailure(invalid);
		assertTrue(invalid.errorMessage.contains("conditions changed"), invalid.errorMessage);

		String original = unit.getSource();
		try {
			unit.getBuffer().setContents(original + "\n// source changed\n");
			unit.save(null, true);
			RefactorWorkspaceEdit stale = GetRefactorEditHandler.getEditsForRefactor(new GetRefactorEditParams(operation, null, context, null, first.confirmationToken));
			assertFailure(stale);
			assertTrue(stale.errorMessage.contains("conditions changed"), stale.errorMessage);
		} finally {
			unit.getBuffer().setContents(original);
			unit.save(null, true);
		}
		context.getContext().setDiagnostics(List.of(new Diagnostic(context.getRange(), "Refreshed client diagnostics")));
		context.setWorkDoneToken("updated-progress-token");
		context.setPartialResultToken("updated-results-token");
		RefactorWorkspaceEdit confirmed = GetRefactorEditHandler.getEditsForRefactor(new GetRefactorEditParams(operation, null, context, null, first.confirmationToken));
		assertNormalEdit(confirmed);
		assertEquals(original, unit.getSource());
		return confirmed;
	}
}
