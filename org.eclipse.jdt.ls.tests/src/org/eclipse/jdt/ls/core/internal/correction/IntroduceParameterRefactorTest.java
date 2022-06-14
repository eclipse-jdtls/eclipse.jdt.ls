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
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

public class IntroduceParameterRefactorTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;
	private ClientPreferences clientPreferences;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
		setIgnoredKind(CodeActionKind.Refactor, JavaCodeActionKind.SOURCE_OVERRIDE_METHODS, JavaCodeActionKind.SOURCE_GENERATE_TO_STRING, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS, JavaCodeActionKind.REFACTOR_EXTRACT_FIELD,
				JavaCodeActionKind.REFACTOR_EXTRACT_VARIABLE, JavaCodeActionKind.REFACTOR_EXTRACT_CONSTANT, JavaCodeActionKind.REFACTOR_ASSIGN_FIELD, JavaCodeActionKind.REFACTOR_ASSIGN_VARIABLE, CodeActionKind.RefactorInline);
	}

	@Override
	protected ClientPreferences initPreferenceManager(boolean supportClassFileContents) {
		clientPreferences = super.initPreferenceManager(supportClassFileContents);
		when(clientPreferences.isAdvancedIntroduceParameterRefactoringSupported()).thenReturn(true);
		return clientPreferences;
	}

	@Test
	public void testIntroduceParameter() throws Exception {
		setIgnoredCommands("Assign statement to new field", ActionMessages.GenerateConstructorsAction_ellipsisLabel, ActionMessages.GenerateConstructorsAction_label);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		// @formatter:off
		buf.append("package test1;\n" +
				"public class E {\n" +
				"    public void foo(){\r\n" +
				"        greeting();\r\n" + "    }\r\n" +
				"\r\n" +
				"    private void greeting() {\r\n" +
				"        System.out.println(\"Hello World\");\r\n" +
				"    }\n" +
				"}\n");
		// @formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range range = new Range(new Position(7, 30), new Position(7, 30));
		testIntroduceParameterCommand(cu, range);
		when(clientPreferences.isAdvancedIntroduceParameterRefactoringSupported()).thenReturn(false);
		testIntroduceParameterAction(cu, range);
	}

	private void testIntroduceParameterCommand(ICompilationUnit cu, Range range) throws JavaModelException {
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, range);
		assertEquals(2, codeActions.size());
		Either<Command, CodeAction> codeAction = codeActions.get(0);
		CodeAction action = codeAction.getRight();
		assertEquals(JavaCodeActionKind.REFACTOR_INTRODUCE_PARAMETER, action.getKind());
		assertEquals("Introduce Parameter...", action.getTitle());
		Command c = action.getCommand();
		assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, c.getCommand());
		assertNotNull(c.getArguments());
		assertEquals(RefactorProposalUtility.INTRODUCE_PARAMETER_COMMAND, c.getArguments().get(0));
	}

	private void testIntroduceParameterAction(ICompilationUnit cu, Range range) throws JavaModelException {
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, range);
		assertEquals(2, codeActions.size());
		Either<Command, CodeAction> codeAction = codeActions.get(0);
		CodeAction action = codeAction.getRight();
		assertEquals(JavaCodeActionKind.REFACTOR_INTRODUCE_PARAMETER, action.getKind());
		assertEquals("Introduce Parameter...", action.getTitle());
		Command c = action.getCommand();
		assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
		assertEquals(1, c.getArguments().size());
		WorkspaceEdit workspaceEdit = (WorkspaceEdit) c.getArguments().get(0);
		Map<String, List<TextEdit>> changes = workspaceEdit.getChanges();
		List<TextEdit> textEdits = changes.values().iterator().next();
		assertEquals(1, textEdits.size());
		TextEdit textEdit = textEdits.get(0);
		assertEquals("\"Hello World\");\r\n    }\r\n\r\n    private void greeting(String string) {\r\n        System.out.println(string", textEdit.getNewText());
	}

}
