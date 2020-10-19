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
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.lsp4j.CodeActionKind;
import org.junit.Before;
import org.junit.Test;

public class InlineVariableTest extends AbstractSelectionTest {

	private static final String INLINE_LOCAL_VARIABLE = CorrectionMessages.QuickAssistProcessor_inline_local_description;

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

		Expected expected = new Expected(INLINE_LOCAL_VARIABLE, buf.toString(), CodeActionKind.RefactorInline);
		assertCodeActions(cu, expected);
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
		assertCodeActionNotExists(cu, INLINE_LOCAL_VARIABLE);
	}
}
