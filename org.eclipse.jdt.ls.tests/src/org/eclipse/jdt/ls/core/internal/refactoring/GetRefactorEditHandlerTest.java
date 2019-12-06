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

import java.util.Arrays;
import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.GetRefactorEditParams;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.RefactorWorkspaceEdit;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility.InitializeScope;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GetRefactorEditHandlerTest extends AbstractSelectionTest {
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
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);
		GetRefactorEditParams editParams = new GetRefactorEditParams(RefactorProposalUtility.EXTRACT_VARIABLE_COMMAND, params);
		RefactorWorkspaceEdit refactorEdit = GetRefactorEditHandler.getEditsForRefactor(editParams);
		Assert.assertNotNull(refactorEdit);
		Assert.assertNotNull(refactorEdit.edit);
		String actual = evaluateChanges(refactorEdit.edit.getChanges());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	void m(int i){\n");
		buf.append("		int j = 0;\n");
		buf.append("        int x= /*]*/j/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		AbstractSourceTestCase.compareSource(buf.toString(), actual);

		Assert.assertNotNull(refactorEdit.command);
		Assert.assertEquals(GetRefactorEditHandler.RENAME_COMMAND, refactorEdit.command.getCommand());
		Assert.assertNotNull(refactorEdit.command.getArguments());
		Assert.assertEquals(1, refactorEdit.command.getArguments().size());
	}

	@Test
	public void testExtractVariableAllOccurrence() throws Exception {
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
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);
		GetRefactorEditParams editParams = new GetRefactorEditParams(RefactorProposalUtility.EXTRACT_VARIABLE_ALL_OCCURRENCE_COMMAND, params);
		RefactorWorkspaceEdit refactorEdit = GetRefactorEditHandler.getEditsForRefactor(editParams);
		Assert.assertNotNull(refactorEdit);
		Assert.assertNotNull(refactorEdit.edit);
		String actual = evaluateChanges(refactorEdit.edit.getChanges());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	void m(int i){\n");
		buf.append("		int j = 0;\n");
		buf.append("        int x= /*]*/j/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		AbstractSourceTestCase.compareSource(buf.toString(), actual);

		Assert.assertNotNull(refactorEdit.command);
		Assert.assertEquals(GetRefactorEditHandler.RENAME_COMMAND, refactorEdit.command.getCommand());
		Assert.assertNotNull(refactorEdit.command.getArguments());
		Assert.assertEquals(1, refactorEdit.command.getArguments().size());
	}

	@Test
	public void testExtractField() throws Exception {
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
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);
		GetRefactorEditParams editParams = new GetRefactorEditParams(RefactorProposalUtility.EXTRACT_FIELD_COMMAND, Arrays.asList(InitializeScope.CURRENT_METHOD.getName()), params);
		RefactorWorkspaceEdit refactorEdit = GetRefactorEditHandler.getEditsForRefactor(editParams);
		Assert.assertNotNull(refactorEdit);
		Assert.assertNotNull(refactorEdit.edit);
		String actual = evaluateChanges(refactorEdit.edit.getChanges());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	private int i;\n\n");
		buf.append("    void m(int i){\n");
		buf.append("		this.i = 0;\n");
		buf.append("        int x= /*]*/this.i/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		AbstractSourceTestCase.compareSource(buf.toString(), actual);

		Assert.assertNotNull(refactorEdit.command);
		Assert.assertEquals(GetRefactorEditHandler.RENAME_COMMAND, refactorEdit.command.getCommand());
		Assert.assertNotNull(refactorEdit.command.getArguments());
		Assert.assertEquals(1, refactorEdit.command.getArguments().size());
	}

	@Test
	public void testExtractConstant() throws Exception {
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
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);
		GetRefactorEditParams editParams = new GetRefactorEditParams(RefactorProposalUtility.EXTRACT_CONSTANT_COMMAND, params);
		RefactorWorkspaceEdit refactorEdit = GetRefactorEditHandler.getEditsForRefactor(editParams);
		Assert.assertNotNull(refactorEdit);
		Assert.assertNotNull(refactorEdit.edit);
		String actual = evaluateChanges(refactorEdit.edit.getChanges());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	private static final int _0 = /*]*/0/*[*/;\n");
		buf.append("\n");
		buf.append("    void m(int i){\n");
		buf.append("		int x= _0;\n");
		buf.append("	}\n");
		buf.append("}\n");
		AbstractSourceTestCase.compareSource(buf.toString(), actual);

		Assert.assertNotNull(refactorEdit.command);
		Assert.assertEquals(GetRefactorEditHandler.RENAME_COMMAND, refactorEdit.command.getCommand());
		Assert.assertNotNull(refactorEdit.command.getArguments());
		Assert.assertEquals(1, refactorEdit.command.getArguments().size());
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
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);
		GetRefactorEditParams editParams = new GetRefactorEditParams(RefactorProposalUtility.EXTRACT_METHOD_COMMAND, params);
		RefactorWorkspaceEdit refactorEdit = GetRefactorEditHandler.getEditsForRefactor(editParams);
		Assert.assertNotNull(refactorEdit);
		Assert.assertNotNull(refactorEdit.edit);
		String actual = evaluateChanges(refactorEdit.edit.getChanges());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(boolean b1, boolean b2) {\n");
		buf.append("        int n = 0;\n");
		buf.append("        int i = 0;\n");
		buf.append("        n = extracted(b1, b2, n, i);\n");
		buf.append("        return n;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int extracted(boolean b1, boolean b2, int n, int i) {\n");
		buf.append("        /*[*/\n");
		buf.append("        if (b1)\n");
		buf.append("            i = 1;\n");
		buf.append("        if (b2)\n");
		buf.append("            n = n + i;\n");
		buf.append("        /*]*/\n");
		buf.append("        return n;\n");
		buf.append("    }\n");
		buf.append("}\n");
		AbstractSourceTestCase.compareSource(buf.toString(), actual);

		Assert.assertNotNull(refactorEdit.command);
		Assert.assertEquals(GetRefactorEditHandler.RENAME_COMMAND, refactorEdit.command.getCommand());
		Assert.assertNotNull(refactorEdit.command.getArguments());
		Assert.assertEquals(1, refactorEdit.command.getArguments().size());
	}
}
