/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * @author qisun
 *
 */
public class ModifierCorrectionsQuickFixTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;
	private static final String proposal = "Remove invalid modifiers";

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testInvalidInterfaceModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public static interface E {\n");
		buf.append("    public void foo();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public void foo();\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidMemberInterfaceModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private interface Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    interface Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidInterfaceFieldModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public native int i;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public int i;\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidInterfaceMethodModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private strictfp void foo();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    void foo();\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidClassModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public volatile class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidMemberClassModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private class Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    class Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidLocalClassModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo() {\n");
		buf.append("        static class Local {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo() {\n");
		buf.append("        class Local {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidClassFieldModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    strictfp public native int i;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public int i;\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidClassMethodModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public abstract class E {\n");
		buf.append("    volatile abstract void foo();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public abstract class E {\n");
		buf.append("    abstract void foo();\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidConstructorModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class Bug {\n");
		buf.append("    public static Bug() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Bug.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class Bug {\n");
		buf.append("    public Bug() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidParamModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo(private int x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo(int x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidVariableModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo() {\n");
		buf.append("        native int x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo() {\n");
		buf.append("        int x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidEnumModifier() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("private final strictfp enum E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("strictfp enum E {\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidEnumModifier2() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public abstract enum E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidEnumConstructorModifier() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("enum E {\n");
		buf.append("	WHITE(1);\n");
		buf.append("\n");
		buf.append("	public final E(int foo) {\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("enum E {\n");
		buf.append("	WHITE(1);\n");
		buf.append("\n");
		buf.append("	E(int foo) {\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidMemberEnumModifier() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("class E {\n");
		buf.append("	final enum A {\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("class E {\n");
		buf.append("	enum A {\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidArgumentModifier() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public void foo(static String a);\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public void foo(String a);\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidModifierCombinationFinalVolatileForField() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public final volatile String A;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public final String A;\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidStaticModifierForField() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private static String A = \"Test\";\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    static String A = \"Test\";\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidStaticModifierForMethod() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private static void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    static void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testOverrideStaticMethod() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    public static void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'static' modifier of 'C.foo'(..)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testOverrideFinalMethod() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    protected final void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    protected void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    protected void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    protected void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'final' modifier of 'C.foo'(..)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvisibleFieldRequestedInSamePackage1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    public void foo (C c) {\n");
		buf.append("         c.test = 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    int test;\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    public void foo (C c) {\n");
		buf.append("         c.test = 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'test' to 'package'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
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
		buf.append("public class E {\n");
		buf.append("    public void foo (C c) {\n");
		buf.append("         c.setTest(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create getter and setter for 'test'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testInvisibleFieldRequestedInSamePackage2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    public void foo () {\n");
		buf.append("         test = 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    protected int test;\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    public void foo () {\n");
		buf.append("         test = 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'test' to 'protected'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    public void foo () {\n");
		buf.append("         int test = 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create local variable 'test'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    private int test;\n");
		buf.append("\n");
		buf.append("    public void foo () {\n");
		buf.append("         test = 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create field 'test'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    public void foo (int test) {\n");
		buf.append("         test = 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create parameter 'test'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    private int test;\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    public void foo () {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e5 = new Expected("Remove assignment", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
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
		buf.append("public class E extends C {\n");
		buf.append("    public void foo () {\n");
		buf.append("         setTest(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e6 = new Expected("Create getter and setter for 'test'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4, e5, e6);
	}

	@Test
	public void testInvisibleMethodRequestedInOtherType() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private void test() {}\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo () {\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.test();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    void test() {}\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo () {\n");
		buf.append("        C c = new C();\n");
		buf.append("        c.test();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'test()' to 'package'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvisibleMethodRequestedInOtherPackage() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private void add() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("C.java", buf.toString(), false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("test1", false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test.C;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo (C c) {\n");
		buf.append("         c.add();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack2.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    public void add() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'add()' to 'public'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvisibleConstructorRequestedInOtherType() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private C() {}\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo () {\n");
		buf.append("        C c = new C();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    C() {}\n");
		buf.append("}\n");
		buf.append("class D {\n");
		buf.append("    public void foo () {\n");
		buf.append("        C c = new C();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'C()' to 'package'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvisibleConstructorRequestedInInSuperType() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    private C() {}\n");
		buf.append("}\n");
		pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("class D extends C{\n");
		buf.append("    public D() {\n");
		buf.append("        super();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    protected C() {}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'C()' to 'protected'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvisibleTypeRequestedInOtherPackage() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A {}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("test2", false, null);
		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("public class B {\n");
		buf.append("    public void foo () {\n");
		buf.append("        test1.A a = null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack2.createCompilationUnit("B.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {}\n");
		Expected e1 = new Expected("Change visibility of 'A' to 'public'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvisibleTypeRequestedInGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A<T> {}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("test2", false, null);
		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("public class B {\n");
		buf.append("    public void foo () {\n");
		buf.append("        test1.A<String> a = null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack2.createCompilationUnit("B.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {}\n");
		Expected e1 = new Expected("Change visibility of 'A' to 'public'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvisibleTypeRequestedFromSuperClass() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("    private class InnerA {}\n");
		buf.append("}\n");
		pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class B extends A{\n");
		buf.append("    public void foo () {\n");
		buf.append("        InnerA a = null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("B.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("    class InnerA {}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change visibility of 'InnerA' to 'package'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testNonBlankFinalLocalAssignment() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class X {\n");
		buf.append("  void foo() {\n");
		buf.append("    final String s = \"\";\n");
		buf.append("    if (false) {\n");
		buf.append("      s = \"\";\n");
		buf.append("    }\n");
		buf.append("  }\n");
		buf.append("}");

		ICompilationUnit cu = pack.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class X {\n");
		buf.append("  void foo() {\n");
		buf.append("    String s = \"\";\n");
		buf.append("    if (false) {\n");
		buf.append("      s = \"\";\n");
		buf.append("    }\n");
		buf.append("  }\n");
		buf.append("}");

		Expected e1 = new Expected("Remove 'final' modifier of 's'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testDuplicateFinalLocalInitialization() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class X {\n");
		buf.append("   private int a;");
		buf.append("	public X (int a) {\n");
		buf.append("		this.a = a;\n");
		buf.append("	}\n");
		buf.append("	public int returnA () {\n");
		buf.append("		return a;\n");
		buf.append("	}\n");
		buf.append("	public static boolean comparison (X x, int val) {\n");
		buf.append("		return (x.returnA() == val);\n");
		buf.append("	}\n");
		buf.append("	public void foo() {\n");
		buf.append("		final X abc;\n");
		buf.append("		boolean comp = X.comparison((abc = new X(2)), (abc = new X(1)).returnA());\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class X {\n");
		buf.append("   private int a;");
		buf.append("	public X (int a) {\n");
		buf.append("		this.a = a;\n");
		buf.append("	}\n");
		buf.append("	public int returnA () {\n");
		buf.append("		return a;\n");
		buf.append("	}\n");
		buf.append("	public static boolean comparison (X x, int val) {\n");
		buf.append("		return (x.returnA() == val);\n");
		buf.append("	}\n");
		buf.append("	public void foo() {\n");
		buf.append("		X abc;\n");
		buf.append("		boolean comp = X.comparison((abc = new X(2)), (abc = new X(1)).returnA());\n");
		buf.append("	}\n");
		buf.append("}\n");

		Expected e1 = new Expected("Remove 'final' modifier of 'abc'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testFinalFieldAssignment() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class X {\n");
		buf.append("	final int contents;\n");
		buf.append("	\n");
		buf.append("	X() {\n");
		buf.append("		contents = 3;\n");
		buf.append("	}\n");
		buf.append("	X(X other) {\n");
		buf.append("		other.contents = 5;\n");
		buf.append("	}\n");
		buf.append("	\n");
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		X one = new X();\n");
		buf.append("		System.out.println(\"one.contents: \" + one.contents);\n");
		buf.append("		X two = new X(one);\n");
		buf.append("		System.out.println(\"one.contents: \" + one.contents);\n");
		buf.append("		System.out.println(\"two.contents: \" + two.contents);\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class X {\n");
		buf.append("	int contents;\n");
		buf.append("	\n");
		buf.append("	X() {\n");
		buf.append("		contents = 3;\n");
		buf.append("	}\n");
		buf.append("	X(X other) {\n");
		buf.append("		other.contents = 5;\n");
		buf.append("	}\n");
		buf.append("	\n");
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		X one = new X();\n");
		buf.append("		System.out.println(\"one.contents: \" + one.contents);\n");
		buf.append("		X two = new X(one);\n");
		buf.append("		System.out.println(\"one.contents: \" + one.contents);\n");
		buf.append("		System.out.println(\"two.contents: \" + two.contents);\n");
		buf.append("	}\n");
		buf.append("}\n");

		Expected e1 = new Expected("Remove 'final' modifier of 'contents'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testDuplicateBlankFinalFieldInitialization() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class X {\n");
		buf.append("   private int a;\n");
		buf.append("	final int x;\n");
		buf.append("	{\n");
		buf.append("		x = new X(x = 2).returnA();");
		buf.append("	}\n");
		buf.append("	public X (int a) {\n");
		buf.append("		this.a = a;\n");
		buf.append("	}\n");
		buf.append("	public int returnA () {\n");
		buf.append("		return a;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class X {\n");
		buf.append("   private int a;\n");
		buf.append("	int x;\n");
		buf.append("	{\n");
		buf.append("		x = new X(x = 2).returnA();");
		buf.append("	}\n");
		buf.append("	public X (int a) {\n");
		buf.append("		this.a = a;\n");
		buf.append("	}\n");
		buf.append("	public int returnA () {\n");
		buf.append("		return a;\n");
		buf.append("	}\n");
		buf.append("}\n");

		Expected e1 = new Expected("Remove 'final' modifier of 'x'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testAnonymousClassCannotExtendFinalClass() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public final class X implements Serializable {\n");
		buf.append("    class SMember extends String {}  \n");
		buf.append("    @Annot(value = new SMember())\n");
		buf.append("     void bar() {}\n");
		buf.append("    @Annot(value = \n");
		buf.append("            new X(){\n");
		buf.append("                    ZorkAnonymous1 z;\n");
		buf.append("                    void foo() {\n");
		buf.append("                            this.bar();\n");
		buf.append("                            Zork2 z;\n");
		buf.append("                    }\n");
		buf.append("            })\n");
		buf.append("	void foo() {}\n");
		buf.append("}\n");
		buf.append("@interface Annot {\n");
		buf.append("        String value();\n");
		buf.append("}\n");

		ICompilationUnit cu = pack.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class X implements Serializable {\n");
		buf.append("    class SMember extends String {}  \n");
		buf.append("    @Annot(value = new SMember())\n");
		buf.append("     void bar() {}\n");
		buf.append("    @Annot(value = \n");
		buf.append("            new X(){\n");
		buf.append("                    ZorkAnonymous1 z;\n");
		buf.append("                    void foo() {\n");
		buf.append("                            this.bar();\n");
		buf.append("                            Zork2 z;\n");
		buf.append("                    }\n");
		buf.append("            })\n");
		buf.append("	void foo() {}\n");
		buf.append("}\n");
		buf.append("@interface Annot {\n");
		buf.append("        String value();\n");
		buf.append("}\n");

		Expected e1 = new Expected("Remove 'final' modifier of 'X'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testClassExtendFinalClass() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("\n");
		buf.append("public final class X implements Serializable {\n");
		buf.append("\n");
		buf.append("        void bar() {}\n");
		buf.append("\n");
		buf.append("        interface IM {}\n");
		buf.append("        class SMember extends String {}\n");
		buf.append("\n");
		buf.append("        class Member extends X {  \n");
		buf.append("                ZorkMember z;\n");
		buf.append("                void foo() {\n");
		buf.append("                        this.bar();\n");
		buf.append("                        Zork1 z;\n");
		buf.append("                } \n");
		buf.append("        }\n");
		buf.append("\n");
		buf.append("        void foo() {\n");
		buf.append("                new X().new IM();\n");
		buf.append("                class Local extends X { \n");
		buf.append("                        ZorkLocal z;\n");
		buf.append("                        void foo() {\n");
		buf.append("                                this.bar();\n");
		buf.append("                                Zork3 z;\n");
		buf.append("                        }\n");
		buf.append("                }\n");
		buf.append("                new X() {\n");
		buf.append("                        ZorkAnonymous2 z;                       \n");
		buf.append("                        void foo() {\n");
		buf.append("                                this.bar();\n");
		buf.append("                                Zork4 z;\n");
		buf.append("                        }\n");
		buf.append("                };\n");
		buf.append("        }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("\n");
		buf.append("public class X implements Serializable {\n");
		buf.append("\n");
		buf.append("        void bar() {}\n");
		buf.append("\n");
		buf.append("        interface IM {}\n");
		buf.append("        class SMember extends String {}\n");
		buf.append("\n");
		buf.append("        class Member extends X {  \n");
		buf.append("                ZorkMember z;\n");
		buf.append("                void foo() {\n");
		buf.append("                        this.bar();\n");
		buf.append("                        Zork1 z;\n");
		buf.append("                } \n");
		buf.append("        }\n");
		buf.append("\n");
		buf.append("        void foo() {\n");
		buf.append("                new X().new IM();\n");
		buf.append("                class Local extends X { \n");
		buf.append("                        ZorkLocal z;\n");
		buf.append("                        void foo() {\n");
		buf.append("                                this.bar();\n");
		buf.append("                                Zork3 z;\n");
		buf.append("                        }\n");
		buf.append("                }\n");
		buf.append("                new X() {\n");
		buf.append("                        ZorkAnonymous2 z;                       \n");
		buf.append("                        void foo() {\n");
		buf.append("                                this.bar();\n");
		buf.append("                                Zork4 z;\n");
		buf.append("                        }\n");
		buf.append("                };\n");
		buf.append("        }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Remove 'final' modifier of 'X'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testAddSealedMissingClassModifierProposal() throws Exception {
		Map<String, String> options20 = new HashMap<>();
		JavaModelUtil.setComplianceOptions(options20, JavaCore.VERSION_20);
		options20.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
		options20.put(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
		fJProject.setOptions(options20);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test", false, null);
		assertNoErrors(fJProject.getResource());

		StringBuilder buf = new StringBuilder();
		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public sealed class Shape permits Square {}\n");
		buf.append("\n");
		buf.append("class Square extends Shape {}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Shape.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public sealed class Shape permits Square {}\n");
		buf.append("\n");
		buf.append("final class Square extends Shape {}\n");
		Expected e1 = new Expected("Change 'Square' to 'final'", buf.toString());
		assertCodeActions(cu, e1);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public sealed class Shape permits Square {}\n");
		buf.append("\n");
		buf.append("non-sealed class Square extends Shape {}\n");
		Expected e2 = new Expected("Change 'Square' to 'non-sealed'", buf.toString());
		assertCodeActions(cu, e2);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public sealed class Shape permits Square {}\n");
		buf.append("\n");
		buf.append("sealed class Square extends Shape {}\n");
		Expected e3 = new Expected("Change 'Square' to 'sealed'", buf.toString());
		assertCodeActions(cu, e3);
	}

	@Test
	public void testAddSealedAsDirectSuperClass() throws Exception {
		Map<String, String> options20 = new HashMap<>();
		JavaModelUtil.setComplianceOptions(options20, JavaCore.VERSION_20);
		options20.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
		options20.put(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
		fJProject.setOptions(options20);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test", false, null);
		assertNoErrors(fJProject.getResource());

		StringBuilder buf = new StringBuilder();
		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public sealed class Shape permits Square {}\n");
		buf.append("\n");
		buf.append("final class Square {}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Shape.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public sealed class Shape permits Square {}\n");
		buf.append("\n");
		buf.append("final class Square extends Shape {}\n");
		Expected e1 = new Expected("Declare 'Shape' as direct super class of 'Square'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testAddPermitsToDirectSuperClass() throws Exception {
		Map<String, String> options20 = new HashMap<>();
		JavaModelUtil.setComplianceOptions(options20, JavaCore.VERSION_20);
		options20.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
		options20.put(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
		fJProject.setOptions(options20);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test", false, null);
		assertNoErrors(fJProject.getResource());

		StringBuilder buf = new StringBuilder();
		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public sealed class Shape {}\n");
		buf.append("\n");
		pack1.createCompilationUnit("Shape.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("final class Square extends Shape {}\n");
		buf.append("\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Square.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public sealed class Shape permits Square {}\n");
		buf.append("\n");
		Expected e1 = new Expected("Declare 'Square' as permitted subtype of 'Shape'", buf.toString());
		assertCodeActions(cu, e1);
	}

}
