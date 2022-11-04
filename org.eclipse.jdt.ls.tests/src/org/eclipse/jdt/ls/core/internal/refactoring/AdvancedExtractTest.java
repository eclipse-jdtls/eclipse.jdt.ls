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

import static org.mockito.Mockito.when;

import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandlerTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AdvancedExtractTest extends AbstractSelectionTest {
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		setOnly(CodeActionKind.Refactor);
	}

	@Override
	protected ClientPreferences initPreferenceManager(boolean supportClassFileContents) {
		ClientPreferences clientPreferences = super.initPreferenceManager(supportClassFileContents);
		when(clientPreferences.isAdvancedExtractRefactoringSupported()).thenReturn(true);
		return clientPreferences;
	}

	@Test
	public void testExtractVariable() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	void m(int i){\n");
		buf.append("		int x= /*]*/0/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getRange(cu, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, selection);
		List<Either<Command, CodeAction>> extractCodeActions = codeActions.stream().filter(codeAction -> codeAction.getRight().getKind().startsWith(CodeActionKind.RefactorExtract)).collect(Collectors.toList());
		Assert.assertEquals(5, extractCodeActions.size());
		Command extractConstantCommand = CodeActionHandlerTest.getCommand(extractCodeActions.get(3));
		Assert.assertNotNull(extractConstantCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, extractConstantCommand.getCommand());
		Assert.assertNotNull(extractConstantCommand.getArguments());
		Assert.assertEquals(2, extractConstantCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.EXTRACT_CONSTANT_COMMAND, extractConstantCommand.getArguments().get(0));

		Command extractFieldCommand = CodeActionHandlerTest.getCommand(extractCodeActions.get(1));
		Assert.assertNotNull(extractFieldCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, extractFieldCommand.getCommand());
		Assert.assertNotNull(extractFieldCommand.getArguments());
		Assert.assertEquals(3, extractFieldCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.EXTRACT_FIELD_COMMAND, extractFieldCommand.getArguments().get(0));

		Command extractMethodCommand = CodeActionHandlerTest.getCommand(extractCodeActions.get(4));
		Assert.assertNotNull(extractMethodCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, extractMethodCommand.getCommand());
		Assert.assertNotNull(extractMethodCommand.getArguments());
		Assert.assertEquals(2, extractMethodCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.EXTRACT_METHOD_COMMAND, extractMethodCommand.getArguments().get(0));

		Command extractVariableAllCommand = CodeActionHandlerTest.getCommand(extractCodeActions.get(0));
		Assert.assertNotNull(extractVariableAllCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, extractVariableAllCommand.getCommand());
		Assert.assertNotNull(extractVariableAllCommand.getArguments());
		Assert.assertEquals(2, extractVariableAllCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.EXTRACT_VARIABLE_ALL_OCCURRENCE_COMMAND, extractVariableAllCommand.getArguments().get(0));

		Command extractVariableCommand = CodeActionHandlerTest.getCommand(extractCodeActions.get(2));
		Assert.assertNotNull(extractVariableCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, extractVariableCommand.getCommand());
		Assert.assertNotNull(extractVariableCommand.getArguments());
		Assert.assertEquals(2, extractVariableCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.EXTRACT_VARIABLE_COMMAND, extractVariableCommand.getArguments().get(0));
	}

	@Test
	public void testExtractMethod() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(boolean b1, boolean b2) {\n");
		buf.append("        int n = 0;\n");
		buf.append("        int i = 0;\n");
		buf.append("        /*[*/\n");
		buf.append("        if (b1)\n");
		buf.append("            i = 1;\n");
		buf.append("        if (b2)\n");
		buf.append("            n = n + i;\n");
		buf.append("        /*]*/\n");
		buf.append("        return n;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getRange(cu, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, selection);
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> extractMethodAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		Assert.assertNotNull(extractMethodAction);
		Command extractMethodCommand = CodeActionHandlerTest.getCommand(extractMethodAction);
		Assert.assertNotNull(extractMethodCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, extractMethodCommand.getCommand());
		Assert.assertNotNull(extractMethodCommand.getArguments());
		Assert.assertEquals(2, extractMethodCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.EXTRACT_METHOD_COMMAND, extractMethodCommand.getArguments().get(0));
	}

	@Test
	public void testMoveFile() throws Exception {
		when(preferenceManager.getClientPreferences().isMoveRefactoringSupported()).thenReturn(true);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public /*[*/class E /*]*/{\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getRange(cu, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, selection);
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> moveAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.REFACTOR_MOVE);
		Assert.assertNotNull(moveAction);
		Command moveCommand = CodeActionHandlerTest.getCommand(moveAction);
		Assert.assertNotNull(moveCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, moveCommand.getCommand());
		Assert.assertNotNull(moveCommand.getArguments());
		Assert.assertEquals(3, moveCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.MOVE_FILE_COMMAND, moveCommand.getArguments().get(0));
		Assert.assertTrue(moveCommand.getArguments().get(2) instanceof RefactorProposalUtility.MoveFileInfo);
		Assert.assertEquals(JDTUtils.toURI(cu), ((RefactorProposalUtility.MoveFileInfo) moveCommand.getArguments().get(2)).uri);
	}

	@Test
	public void testMoveInstanceMethod() throws Exception {
		when(preferenceManager.getClientPreferences().isMoveRefactoringSupported()).thenReturn(true);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		pack1.createCompilationUnit("Second.java", "package test1;\n"
				+ "\n"
				+ "public class Second {\n"
				+ "    public void bar() {\n"
				+ "    }\n"
				+ "}",
				false, null);
		//@formatter:on

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    Second s;\n");
		buf.append("    public void print() {\n");
		buf.append("        /*[*//*]*/s.bar();\n");
		buf.append("    }\n");
		buf.append("}");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getRange(cu, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, selection);
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> moveAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.REFACTOR_MOVE);
		Assert.assertNotNull(moveAction);
		Command moveCommand = CodeActionHandlerTest.getCommand(moveAction);
		Assert.assertNotNull(moveCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, moveCommand.getCommand());
		Assert.assertNotNull(moveCommand.getArguments());
		Assert.assertEquals(3, moveCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.MOVE_INSTANCE_METHOD_COMMAND, moveCommand.getArguments().get(0));
		Assert.assertTrue(moveCommand.getArguments().get(2) instanceof RefactorProposalUtility.MoveMemberInfo);
		Assert.assertEquals("print()", ((RefactorProposalUtility.MoveMemberInfo) moveCommand.getArguments().get(2)).displayName);
	}

	@Test
	public void testMoveStaticMember() throws Exception {
		when(preferenceManager.getClientPreferences().isMoveRefactoringSupported()).thenReturn(true);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public static void print() {\n");
		buf.append("        /*[*//*]*/\n");
		buf.append("    }\n");
		buf.append("}");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getRange(cu, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, selection);
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> moveAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.REFACTOR_MOVE);
		Assert.assertNotNull(moveAction);
		Command moveCommand = CodeActionHandlerTest.getCommand(moveAction);
		Assert.assertNotNull(moveCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, moveCommand.getCommand());
		Assert.assertNotNull(moveCommand.getArguments());
		Assert.assertEquals(3, moveCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.MOVE_STATIC_MEMBER_COMMAND, moveCommand.getArguments().get(0));
		Assert.assertTrue(moveCommand.getArguments().get(2) instanceof RefactorProposalUtility.MoveMemberInfo);
		Assert.assertEquals(fJProject1.getProject().getName(), ((RefactorProposalUtility.MoveMemberInfo) moveCommand.getArguments().get(2)).projectName);
		Assert.assertEquals("print()", ((RefactorProposalUtility.MoveMemberInfo) moveCommand.getArguments().get(2)).displayName);
		Assert.assertEquals(ASTNode.METHOD_DECLARATION, ((RefactorProposalUtility.MoveMemberInfo) moveCommand.getArguments().get(2)).memberType);
		Assert.assertEquals("test1.E", ((RefactorProposalUtility.MoveMemberInfo) moveCommand.getArguments().get(2)).enclosingTypeName);
	}

	@Test
	public void testMoveInnerType() throws Exception {
		when(preferenceManager.getClientPreferences().isMoveRefactoringSupported()).thenReturn(true);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    class Inner {\n");
		buf.append("        /*[*//*]*/\n");
		buf.append("    }\n");
		buf.append("}");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getRange(cu, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, selection);
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> moveAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.REFACTOR_MOVE);
		Assert.assertNotNull(moveAction);
		Command moveCommand = CodeActionHandlerTest.getCommand(moveAction);
		Assert.assertNotNull(moveCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, moveCommand.getCommand());
		Assert.assertNotNull(moveCommand.getArguments());
		Assert.assertEquals(3, moveCommand.getArguments().size());
		Assert.assertEquals(RefactorProposalUtility.MOVE_TYPE_COMMAND, moveCommand.getArguments().get(0));
		Assert.assertTrue(moveCommand.getArguments().get(2) instanceof RefactorProposalUtility.MoveTypeInfo);
		Assert.assertEquals(fJProject1.getProject().getName(), ((RefactorProposalUtility.MoveTypeInfo) moveCommand.getArguments().get(2)).projectName);
		Assert.assertEquals("Inner", ((RefactorProposalUtility.MoveTypeInfo) moveCommand.getArguments().get(2)).displayName);
		Assert.assertEquals("test1.E", ((RefactorProposalUtility.MoveTypeInfo) moveCommand.getArguments().get(2)).enclosingTypeName);
		Assert.assertTrue(((RefactorProposalUtility.MoveTypeInfo) moveCommand.getArguments().get(2)).isMoveAvaiable());
	}
}
