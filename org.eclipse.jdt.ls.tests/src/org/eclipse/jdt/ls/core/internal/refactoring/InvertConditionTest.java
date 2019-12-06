/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.refactoring;

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class InvertConditionTest extends AbstractSelectionTest {

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
	public void testInvertLessOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 < 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 >= 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "3 < 5", "3 < 5".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "3 < 5", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertGreaterOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 > 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 <= 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "3 > 5", "3 > 5".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "3 > 5", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertLessEqualsOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 <= 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 > 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "3 <= 5", "3 <= 5".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "3 <= 5", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertGreaterEqualsOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 >= 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 < 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "3 >= 5", "3 >= 5".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "3 >= 5", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertEqualsOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 == 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 != 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "3 == 5", "3 == 5".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "3 == 5", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertNotEqualsOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 != 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (3 == 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "3 != 5", "3 != 5".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "3 != 5", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertConditionalAndOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true && true)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (false || false)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "true && true", "true && true".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "true && true", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertConditionalOrOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true || true)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (false && false)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "true || true", "true || true".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "true || true", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertAndOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true & true)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (false | false)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "true & true", "true & true".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "true & true", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertOrOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true | true)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (false & false)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "true | true", "true | true".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "true | true", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertXorOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true ^ true)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (!(true ^ true))\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "true ^ true", "true ^ true".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "true ^ true", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertBooleanLiteral() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (true)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (false)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "true", "true".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "true", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertBooleanLiteralWithParentheses() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (!(!true))\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (!true)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "!(!true)", "!(!true)".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "!(!true)", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertComplexCondition() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int i = 4; i > 3 && i < 10; i++)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int i = 4; i <= 3 || i >= 10; i++)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "i > 3 && i < 10", "i > 3 && i < 10".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "i > 3 && i < 10", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertVariable() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        boolean isValid = true;\n");
		buf.append("        if (isValid)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        boolean isValid = true;\n");
		buf.append("        if (!isValid)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "isValid", "isValid".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "isValid", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testInvertMethodCalling() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean isValid() {\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (isValid())\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean isValid() {\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (!isValid())\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "isValid()", "isValid()".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "isValid()", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testConditionalOperator() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 3 > 5 ? 0 : -1;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 3 <= 5 ? 0 : -1;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "3 > 5", "3 > 5".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "3 > 5", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testCombinedCondition() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean isValid() {\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (isValid() || 3 < 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean isValid() {\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (!isValid() && 3 >= 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "isValid() || 3 < 5", "isValid() || 3 < 5".length());
		assertCodeActions(cu, replacedRange, expected);

		Range nonSelectionRange = CodeActionUtil.getRange(cu, "isValid()", 0);
		assertCodeActions(cu, nonSelectionRange, expected);

		nonSelectionRange = CodeActionUtil.getRange(cu, "3 < 5", 0);
		assertCodeActions(cu, nonSelectionRange, expected);

		nonSelectionRange = CodeActionUtil.getRange(cu, "||", 0);
		assertCodeActions(cu, nonSelectionRange, expected);
	}

	@Test
	public void testCombinedConditionWithPartialSelection() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean isValid() {\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (isValid() || 3 < 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean isValid() {\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (!isValid() || 3 < 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "isValid()", "isValid()".length());
		assertCodeActions(cu, replacedRange, expected);
	}

	@Test
	public void testCombinedConditionWithPartialSelection2() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean isValid() {\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (isValid() || 3 < 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean isValid() {\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (isValid() || 3 >= 5)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Invert conditions", buf.toString(), CodeActionKind.Refactor);
		Range replacedRange = CodeActionUtil.getRange(cu, "3 < 5", "3 < 5".length());
		assertCodeActions(cu, replacedRange, expected);
	}
}
