/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

import java.util.Collections;
import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MoveTest extends AbstractSelectionTest {
	private static final String MOVE = ActionMessages.MoveRefactoringAction_label;

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		ClientPreferences clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isMoveRefactoringSupported()).thenReturn(true);

		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		setOnly(CodeActionKind.Refactor);

	}

	@Test
	public void testMove_class() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Expected expected = new Expected(MOVE, "", JavaCodeActionKind.REFACTOR_MOVE);
		Range range = CodeActionUtil.getRange(cu, "E {", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testMove_staticMethod() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public static void foo() {\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Expected expected = new Expected(MOVE, "", JavaCodeActionKind.REFACTOR_MOVE);
		Range range = CodeActionUtil.getRange(cu, "foo() {", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testMove_staticField() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public static String bar;\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Expected expected = new Expected(MOVE, "", JavaCodeActionKind.REFACTOR_MOVE);
		Range range = CodeActionUtil.getRange(cu, "bar;", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testMove_innerClass() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public class Inner {\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Expected expected = new Expected(MOVE, "", JavaCodeActionKind.REFACTOR_MOVE);
		Range range = CodeActionUtil.getRange(cu, "Inner {", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testMove_noShowInClassBody() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	// body");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Expected expected = new Expected(MOVE, "", JavaCodeActionKind.REFACTOR_MOVE);
		Range range = CodeActionUtil.getRange(cu, "// body", 0);
		assertCodeActions(cu, range, expected);

		setOnly();
		assertCodeActions(cu, range, Collections.emptyList());
	}

	@Test
	public void testMove_noShowInMethodBody() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public static void foo() {\n");
		buf.append("		// body");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Expected expected = new Expected(MOVE, "", JavaCodeActionKind.REFACTOR_MOVE);
		Range range = CodeActionUtil.getRange(cu, "// body", 0);
		assertCodeActions(cu, range, expected);

		setOnly();
		assertCodeActions(cu, range, Collections.emptyList());
	}

	@Override
	protected String evaluateCodeActionCommand(Either<Command, CodeAction> codeAction) throws BadLocationException, JavaModelException {
		Command c = codeAction.isLeft() ? codeAction.getLeft() : codeAction.getRight().getCommand();
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, c.getCommand());
		Assert.assertNotNull(c.getArguments());
		return "";
	}

}
