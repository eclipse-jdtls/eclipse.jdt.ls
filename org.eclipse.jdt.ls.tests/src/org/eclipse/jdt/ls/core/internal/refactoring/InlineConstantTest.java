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

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.lsp4j.CodeActionKind;
import org.junit.Before;
import org.junit.Test;

public class InlineConstantTest extends AbstractSelectionTest {

	private static final String INLINE_CONSTANT = ActionMessages.InlineConstantRefactoringAction_label;

	private IJavaProject testProject;

	private IPackageFragmentRoot testSourceFolder;

	@Before
	public void setup() throws Exception {
		testProject = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		testProject.setOptions(options);
		testSourceFolder = testProject.getPackageFragmentRoot(testProject.getProject().getFolder("src"));
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

		Expected expected = new Expected(INLINE_CONSTANT, buf.toString(), CodeActionKind.RefactorInline);
		assertCodeActions(cu, expected);
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

		Expected expected = new Expected(INLINE_CONSTANT, buf.toString(), CodeActionKind.RefactorInline);
		assertCodeActions(cu, expected);
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
	}
}
