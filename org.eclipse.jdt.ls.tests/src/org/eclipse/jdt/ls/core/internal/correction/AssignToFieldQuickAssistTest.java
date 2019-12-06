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

package org.eclipse.jdt.ls.core.internal.correction;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class AssignToFieldQuickAssistTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testAssignParamToField() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public  E(int count) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int count;\n");
		buf.append("\n");
		buf.append("    public  E(int count) {\n");
		buf.append("        this.count = count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Assign parameter to new field", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "count");
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testAssignParamToField2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public  E(int count, Vector vec[]) {\n");
		buf.append("        super();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private Vector[] vec;\n");
		buf.append("\n");
		buf.append("    public  E(int count, Vector vec[]) {\n");
		buf.append("        super();\n");
		buf.append("        this.vec = vec;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Assign parameter to new field", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private int count;\n");
		buf.append("    private Vector[] vec;\n");
		buf.append("\n");
		buf.append("    public  E(int count, Vector vec[]) {\n");
		buf.append("        super();\n");
		buf.append("        this.count = count;\n");
		buf.append("        this.vec = vec;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Assign all parameters to new fields", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "vec");
		assertCodeActions(cu, selection, e1, e2);
	}

	@Test
	public void testAssignParamToField3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private int vec;\n");
		buf.append("\n");
		buf.append("    public static void foo(int count, Vector vec[]) {\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private int vec;\n");
		buf.append("    private static Vector[] vec2;\n");
		buf.append("\n");
		buf.append("    public static void foo(int count, Vector vec[]) {\n");
		buf.append("        vec2 = vec;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Assign parameter to new field", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private int vec;\n");
		buf.append("    private static int count;\n");
		buf.append("    private static Vector[] vec2;\n");
		buf.append("\n");
		buf.append("    public static void foo(int count, Vector vec[]) {\n");
		buf.append("        E.count = count;\n");
		buf.append("        vec2 = vec;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Assign all parameters to new fields", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "vec");
		assertCodeActions(cu, selection, e1, e2);
	}

	@Test
	public void testAssignParamToField4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private long count;\n");
		buf.append("\n");
		buf.append("    public void foo(int count) {\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private long count;\n");
		buf.append("    private int count2;\n");
		buf.append("\n");
		buf.append("    public void foo(int count) {\n");
		buf.append("        count2 = count;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Assign parameter to new field", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private long count;\n");
		buf.append("\n");
		buf.append("    public void foo(int count) {\n");
		buf.append("        this.count = count;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Assign parameter to field 'count'", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "int count", 0);
		assertCodeActions(cu, selection, e1, e2);
	}

	@Test
	public void testAssignParamToField5() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int p1;\n");
		buf.append("\n");
		buf.append("    public void foo(int p1, int p2) {\n");
		buf.append("        this.p1 = p1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int p1;\n");
		buf.append("    private int p2;\n");
		buf.append("\n");
		buf.append("    public void foo(int p1, int p2) {\n");
		buf.append("        this.p1 = p1;\n");
		buf.append("        this.p2 = p2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Assign parameter to new field", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int p1;\n");
		buf.append("    private int p12;\n");
		buf.append("    private int p2;\n");
		buf.append("\n");
		buf.append("    public void foo(int p1, int p2) {\n");
		buf.append("        p12 = p1;\n");
		buf.append("        this.p1 = p1;\n");
		buf.append("        this.p2 = p2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Assign all parameters to new fields", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int p1;\n");
		buf.append("\n");
		buf.append("    public void foo(int p1, int p2) {\n");
		buf.append("        this.p1 = p1;\n");
		buf.append("        p1 = p2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Assign parameter to field 'p1'", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "int p2", 0);
		assertCodeActions(cu, selection, e1, e2, e3);
	}
}
