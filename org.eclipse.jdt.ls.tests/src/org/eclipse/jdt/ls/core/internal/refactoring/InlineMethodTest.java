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

public class InlineMethodTest extends AbstractSelectionTest {

	private static final String INLINE_METHOD = ActionMessages.InlineMethodRefactoringAction_label;

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

		Expected expected = new Expected(INLINE_METHOD, buf.toString(), CodeActionKind.RefactorInline);
		assertCodeActions(cu, expected);
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

		Expected expected = new Expected(INLINE_METHOD, buf.toString(), CodeActionKind.RefactorInline);
		assertCodeActions(cu, expected);
	}
}
