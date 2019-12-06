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
package org.eclipse.jdt.ls.core.internal.refactoring;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.junit.Before;
import org.junit.Test;

public class AnonymousClassCreationToLambdaTest extends AbstractSelectionTest {

	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertToLambda1() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface I {\n");
		buf.append("    void method();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    public void bar(I i) {}\n");
		buf.append("    public void foo() {\n");
		buf.append("        bar(new I() {\n");
		buf.append("            @Override\n");
		buf.append("            /*[*/public void method() {\n");
		buf.append("                System.out.println();\n");
		buf.append("            }/*]*/\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface I {\n");
		buf.append("    void method();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    public void bar(I i) {}\n");
		buf.append("    public void foo() {\n");
		buf.append("        bar(() -> System.out.println());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}

	@Test
	public void testConvertToLambda2() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface I {\n");
		buf.append("    void method(int a, int b);\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    void bar(I i) {}\n");
		buf.append("    void foo() {\n");
		buf.append("        bar(new I() {\n");
		buf.append("            /*[*/public void method(int a, int b) {\n");
		buf.append("                System.out.println(a+b);\n");
		buf.append("            }/*]*/\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface I {\n");
		buf.append("    void method(int a, int b);\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    void bar(I i) {}\n");
		buf.append("    void foo() {\n");
		buf.append("        bar((a, b) -> System.out.println(a+b));\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}

	@Test
	public void testConvertToLambda3() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface I {\n");
		buf.append("    void count(int test);\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        I i = new I() {\n");
		buf.append("            /*[*/public void count(int test) {\n");
		buf.append("                System.out.println(test);\n");
		buf.append("            }/*]*/\n");
		buf.append("        };\n");
		buf.append("        i.count(10);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface I {\n");
		buf.append("    void count(int test);\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        I i = test -> System.out.println(test);\n");
		buf.append("        i.count(10);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}

	@Test
	public void testConvertToLambda4() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface I {\n");
		buf.append("    int method();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    void bar(I i) {}\n");
		buf.append("    void foo() {\n");
		buf.append("        bar(new I() {\n");
		buf.append("            /*[*/public int method() {\n");
		buf.append("                return 1;\n");
		buf.append("            }/*]*/\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface I {\n");
		buf.append("    int method();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    void bar(I i) {}\n");
		buf.append("    void foo() {\n");
		buf.append("        bar(() -> 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}

	public void testConvertToLambda5() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface A {\n");
		buf.append("    public int sayHello();\n");
		buf.append("}\n");
		buf.append("interface J {\n");
		buf.append("    public int method();\n");
		buf.append("}\n");
		buf.append("public class X {\n");
		buf.append("    static void foo(A a) { }\n");
		buf.append("    static void foo(B b) { }\n");
		buf.append("    public static void main(String[] args) {\n");
		buf.append("        foo(new A() {\n");
		buf.append("            @Override\n");
		buf.append("            /*[*/public int sayHello() {\n");
		buf.append("                return 0;\n");
		buf.append("            }/*]*/\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface A {\n");
		buf.append("    public int sayHello();\n");
		buf.append("}\n");
		buf.append("interface J {\n");
		buf.append("    public int method();\n");
		buf.append("}\n");
		buf.append("public class X {\n");
		buf.append("    static void foo(A a) { }\n");
		buf.append("    static void foo(B b) { }\n");
		buf.append("    public static void main(String[] args) {\n");
		buf.append("        foo((A) () -> 0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}

	@Test
	public void testConvertToLambda6() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("@FunctionalInterface\n");
		buf.append("interface A {\n");
		buf.append("    int test(int x, int y, int z);\n");
		buf.append("}\n");
		buf.append("public class B {\n");
		buf.append("    int i;\n");
		buf.append("    private void foo() {\n");
		buf.append("        A a = new A() {\n");
		buf.append("           @Override\n");
		buf.append("           /*[*/public int test(int x/*km*/, int i /*inches*/, int y/*yards*/) {\n");
		buf.append("                return x + i + y;\n");
		buf.append("           }/*]*/\n");
		buf.append("        };\n");
		buf.append("        a.test(1, 2, 3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("B.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("@FunctionalInterface\n");
		buf.append("interface A {\n");
		buf.append("    int test(int x, int y, int z);\n");
		buf.append("}\n");
		buf.append("public class B {\n");
		buf.append("    int i;\n");
		buf.append("    private void foo() {\n");
		buf.append("        A a = (x/*km*/, i /*inches*/, y/*yards*/) -> x + i + y;\n");
		buf.append("        a.test(1, 2, 3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}

	@Test
	public void testConvertToLambda7() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C1 {\n");
		buf.append("    Runnable r1 = new Runnable() {\n");
		buf.append("        @Override @Deprecated\n");
		buf.append("        /*[*/public void run() {\n");
		buf.append("            System.out.println();\n");
		buf.append("        }/*]*/\n");
		buf.append("    };\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C1.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C1 {\n");
		buf.append("    Runnable r1 = () -> System.out.println();\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}

	@Test
	public void testConvertToLambda8() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C1 {\n");
		buf.append("    Runnable run = new Runnable() {\n");
		buf.append("        @Override\n");
		buf.append("        /*[*/public strictfp void run() {}/*]*/\n");
		buf.append("    };\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C1.java", buf.toString(), false, null);

		assertCodeActionNotExists(cu, "Convert to lambda expression");
	}

	@Test
	public void testConvertToLambda9() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C1 {\n");
		buf.append("    Runnable run = new Runnable() {\n");
		buf.append("        @Override\n");
		buf.append("        /*[*/public synchronized void run() {}/*]*/\n");
		buf.append("    };\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C1.java", buf.toString(), false, null);

		assertCodeActionNotExists(cu, "Convert to lambda expression");
	}

	@Test
	public void testConvertToLambda10() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface FI {\n");
		buf.append("    void foo(String... strs);\n");
		buf.append("}\n");
		buf.append("public class C1 {\n");
		buf.append("    FI fi = new  FI() {\n");
		buf.append("        @Override\n");
		buf.append("        /*[*/public void foo(String... strs) {\n");
		buf.append("                 System.out.println();\n");
		buf.append("        /*]*/}\n");
		buf.append("    };\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C1.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("interface FI {\n");
		buf.append("    void foo(String... strs);\n");
		buf.append("}\n");
		buf.append("public class C1 {\n");
		buf.append("    FI fi = strs -> System.out.println();\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}

	@Test
	public void testConvertToLambda11() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.lang.annotation.ElementType;\n");
		buf.append("import java.lang.annotation.Target;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class C1 {\n");
		buf.append("    FI fi1 = new FI() {\n");
		buf.append("        @Override\n");
		buf.append("        /*[*/public void foo(java.util.@T ArrayList<IOException> x) {\n");
		buf.append("                 System.out.println();\n");
		buf.append("        }/*]*/\n");
		buf.append("    };\n");
		buf.append("}\n");
		buf.append("interface FI {\n");
		buf.append("    void foo(ArrayList<IOException> x);\n");
		buf.append("}\n");
		buf.append("@Target(ElementType.TYPE_USE)\n");
		buf.append("@interface T {\n");
		buf.append("    int val1() default 1;\n");
		buf.append("    int val2() default -1;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C1.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.lang.annotation.ElementType;\n");
		buf.append("import java.lang.annotation.Target;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class C1 {\n");
		buf.append("    FI fi1 = (java.util.@T ArrayList<IOException> x) -> System.out.println();\n");
		buf.append("}\n");
		buf.append("interface FI {\n");
		buf.append("    void foo(ArrayList<IOException> x);\n");
		buf.append("}\n");
		buf.append("@Target(ElementType.TYPE_USE)\n");
		buf.append("@interface T {\n");
		buf.append("    int val1() default 1;\n");
		buf.append("    int val2() default -1;\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}

	@Test
	public void testConvertToLambda12() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.function.Predicate;\n");
		buf.append("\n");
		buf.append("public class Test {\n");
		buf.append("    void foo(ArrayList<String> list) {\n");
		buf.append("        list.removeIf(new Predicate<String>() {\n");
		buf.append("            @Override\n");
		buf.append("            /*[*/public boolean test(String t) {\n");
		buf.append("                return t.isEmpty();\n");
		buf.append("            }/*]*/\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class Test {\n");
		buf.append("    void foo(ArrayList<String> list) {\n");
		buf.append("        list.removeIf(t -> t.isEmpty());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to lambda expression", buf.toString());

		assertCodeActions(cu, e);
	}
}
