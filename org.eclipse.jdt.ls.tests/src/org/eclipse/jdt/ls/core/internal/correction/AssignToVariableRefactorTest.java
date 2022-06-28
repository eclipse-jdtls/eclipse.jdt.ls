/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

public class AssignToVariableRefactorTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
		setIgnoredKind(CodeActionKind.Refactor, JavaCodeActionKind.SOURCE_OVERRIDE_METHODS, JavaCodeActionKind.SOURCE_GENERATE_TO_STRING, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS, JavaCodeActionKind.SOURCE_GENERATE_FINAL_MODIFIERS,
				JavaCodeActionKind.REFACTOR_EXTRACT_FIELD, JavaCodeActionKind.REFACTOR_EXTRACT_VARIABLE, JavaCodeActionKind.REFACTOR_INTRODUCE_PARAMETER, CodeActionKind.RefactorInline);
	}

	@Override
	protected ClientPreferences initPreferenceManager(boolean supportClassFileContents) {
		ClientPreferences clientPreferences = super.initPreferenceManager(supportClassFileContents);
		when(clientPreferences.isAdvancedExtractRefactoringSupported()).thenReturn(true);
		return clientPreferences;
	}

	@Test
	public void testAssignStatementToVariable() throws Exception {
		setIgnoredCommands("Assign statement to new field", ActionMessages.GenerateConstructorsAction_ellipsisLabel, ActionMessages.GenerateConstructorsAction_label);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void main(String[] args) {\n");
		buf.append("        E test = new E();\n");
		buf.append("        test.foo();\n");
		buf.append("    }\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range range = new Range(new Position(4, 10), new Position(4, 10));
		testAssignVariable(cu, range);
		range = new Range(new Position(4, 15), new Position(4, 15));
		testAssignVariable(cu, range);
	}

	private void testAssignVariable(ICompilationUnit cu, Range range) throws JavaModelException {
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, range);
		assertEquals(2, codeActions.size());
		Either<Command, CodeAction> codeAction = codeActions.get(0);
		CodeAction action = codeAction.getRight();
		assertEquals(JavaCodeActionKind.REFACTOR_ASSIGN_VARIABLE, action.getKind());
		assertEquals("Assign statement to new local variable", action.getTitle());
		Command c = action.getCommand();
		assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, c.getCommand());
		assertNotNull(c.getArguments());
		assertEquals(RefactorProposalUtility.ASSIGN_VARIABLE_COMMAND, c.getArguments().get(0));
	}

	@Test
	public void testAssignStatementToField() throws Exception {
		setIgnoredCommands("Assign statement to new local variable", ActionMessages.GenerateConstructorsAction_ellipsisLabel, ActionMessages.GenerateConstructorsAction_label);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void main(String[] args) {\n");
		buf.append("        E test = new E();\n");
		buf.append("        test.foo();\n");
		buf.append("    }\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range range = new Range(new Position(4, 10), new Position(4, 10));
		testAssignField(cu, range);
		range = new Range(new Position(4, 15), new Position(4, 15));
		testAssignField(cu, range);
	}

	private void testAssignField(ICompilationUnit cu, Range range) throws JavaModelException {
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, range);
		assertEquals(2, codeActions.size());
		Either<Command, CodeAction> codeAction = codeActions.get(0);
		CodeAction action = codeAction.getRight();
		assertEquals(JavaCodeActionKind.REFACTOR_ASSIGN_FIELD, action.getKind());
		assertEquals("Assign statement to new field", action.getTitle());
		Command c = action.getCommand();
		assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, c.getCommand());
		assertNotNull(c.getArguments());
		assertEquals(RefactorProposalUtility.ASSIGN_FIELD_COMMAND, c.getArguments().get(0));
	}

}
