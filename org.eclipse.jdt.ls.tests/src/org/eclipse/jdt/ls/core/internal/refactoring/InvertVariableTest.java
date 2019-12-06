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
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class InvertVariableTest extends AbstractSelectionTest {

	private static final String INVERT_BOOLEAN_VARIABLE = CorrectionMessages.AdvancedQuickAssistProcessor_inverseBooleanVariable;

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
	public void testAddingNotPrefixWhenInvertVariable() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        boolean lie = 3 == 5;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        boolean notLie = 3 != 5;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected(INVERT_BOOLEAN_VARIABLE, buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "lie", 0);
		assertCodeActions(cu, replacedRange, expected);
	}

	@Test
	public void testRemovingNotPrefixWhenInvertVariable() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        boolean notLie = 3 != 5;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        boolean lie = 3 == 5;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected(INVERT_BOOLEAN_VARIABLE, buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "notLie", 0);
		assertCodeActions(cu, replacedRange, expected);
	}

	@Test
	public void testComplexInvertVariable() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo() {\n");
		buf.append("        boolean a = 3 != 5;\n");
		buf.append("        boolean b = !a;\n");
		buf.append("        boolean c = bar(a);\n");
		buf.append("        return a;\n");
		buf.append("    }\n");
		buf.append("    public boolean bar(boolean value) {\n");
		buf.append("        return !value;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo() {\n");
		buf.append("        boolean notA = 3 == 5;\n");
		buf.append("        boolean b = notA;\n");
		buf.append("        boolean c = bar(!notA);\n");
		buf.append("        return !notA;\n");
		buf.append("    }\n");
		buf.append("    public boolean bar(boolean value) {\n");
		buf.append("        return !value;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected(INVERT_BOOLEAN_VARIABLE, buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "a = 3 != 5", 0);
		assertCodeActions(cu, replacedRange, expected);
	}
}
