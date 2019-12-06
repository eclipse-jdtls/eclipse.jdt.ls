/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
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
import org.junit.Before;
import org.junit.Test;

/**
 * @author qisun
 *
 */
public class GetterSetterQuickFixTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testInvisibleFieldToGetterSetter() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        ++c.test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    int test;\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        ++c.test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'test' to 'package'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return the test\n");
		buf.append("     */\n");
		buf.append("    public int getTest() {\n");
		buf.append("        return test;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @param test the test to set\n");
		buf.append("     */\n");
		buf.append("    public void setTest(int test) {\n");
		buf.append("        this.test = test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.setTest(c.getTest() + 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create getter and setter for 'test'", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testInvisibleFieldToGetterSetter_2() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.test += 1 + 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    int test;\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.test += 1 + 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'test' to 'package'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return the test\n");
		buf.append("     */\n");
		buf.append("    public int getTest() {\n");
		buf.append("        return test;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @param test the test to set\n");
		buf.append("     */\n");
		buf.append("    public void setTest(int test) {\n");
		buf.append("        this.test = test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.setTest(c.getTest() + (1 + 2));\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create getter and setter for 'test'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testInvisibleFieldToGetterSetter_3() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.test -= 1 + 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    int test;\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.test -= 1 + 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'test' to 'package'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return the test\n");
		buf.append("     */\n");
		buf.append("    public int getTest() {\n");
		buf.append("        return test;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @param test the test to set\n");
		buf.append("     */\n");
		buf.append("    public void setTest(int test) {\n");
		buf.append("        this.test = test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.setTest(c.getTest() - (1 + 2));\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create getter and setter for 'test'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testInvisibleFieldToGetterSetter_4() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.test *= 1 + 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    int test;\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.test *= 1 + 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'test' to 'package'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return the test\n");
		buf.append("     */\n");
		buf.append("    public int getTest() {\n");
		buf.append("        return test;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @param test the test to set\n");
		buf.append("     */\n");
		buf.append("    public void setTest(int test) {\n");
		buf.append("        this.test = test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.setTest(c.getTest() * (1 + 2));\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create getter and setter for 'test'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testInvisibleFieldToGetterSetter_5() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return the test\n");
		buf.append("     */\n");
		buf.append("    public int getTest() {\n");
		buf.append("        return test;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @param test the test to set\n");
		buf.append("     */\n");
		buf.append("    public void setTest(int test) {\n");
		buf.append("        this.test = test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        ++c.test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    int test;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return the test\n");
		buf.append("     */\n");
		buf.append("    public int getTest() {\n");
		buf.append("        return test;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @param test the test to set\n");
		buf.append("     */\n");
		buf.append("    public void setTest(int test) {\n");
		buf.append("        this.test = test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        ++c.test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'test' to 'package'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return the test\n");
		buf.append("     */\n");
		buf.append("    public int getTest() {\n");
		buf.append("        return test;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @param test the test to set\n");
		buf.append("     */\n");
		buf.append("    public void setTest(int test) {\n");
		buf.append("        this.test = test;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo(){\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.setTest(c.getTest() + 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Replace c.test with setter", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testCreateFieldUsingSef() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("    private int t;\n");
		buf.append("    {\n");
		buf.append("        System.out.println(t);\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class B {\n");
		buf.append("    {\n");
		buf.append("        new A().t = 5;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("    int t;\n");
		buf.append("    {\n");
		buf.append("        System.out.println(t);\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class B {\n");
		buf.append("    {\n");
		buf.append("        new A().t = 5;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 't' to 'package'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("    private int t;\n");
		buf.append("    {\n");
		buf.append("        System.out.println(getT());\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * @return the t\n");
		buf.append("     */\n");
		buf.append("    public int getT() {\n");
		buf.append("        return t;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * @param t the t to set\n");
		buf.append("     */\n");
		buf.append("    public void setT(int t) {\n");
		buf.append("        this.t = t;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class B {\n");
		buf.append("    {\n");
		buf.append("        new A().setT(5);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create getter and setter for 't'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}
}