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

package org.eclipse.jdt.ls.core.internal.refactoring;

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.lsp4j.CodeActionKind;
import org.junit.Before;
import org.junit.Test;

public class ExtractMethodTest extends AbstractSelectionTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		setOnly(CodeActionKind.Refactor, CodeActionKind.QuickFix);
	}

	@Test
	public void testExtractMethodBranch() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public volatile boolean flag;\n");
		buf.append("    public void foo() {\n");
		buf.append("        /*[*/for (int i = 0; i < 10; i++) {\n");
		buf.append("            if (flag)\n");
		buf.append("                continue;\n");
		buf.append("        }/*]*/\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public volatile boolean flag;\n");
		buf.append("    public void foo() {\n");
		buf.append("        extracted();\n");
		buf.append("    }\n");
		buf.append("    private void extracted() {\n");
		buf.append("        /*[*/for (int i = 0; i < 10; i++) {\n");
		buf.append("            if (flag)\n");
		buf.append("                continue;\n");
		buf.append("        }/*]*/\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodException() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            /*[*/g();/*]*/\n");
		buf.append("        } catch (java.io.IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    public void g() throws java.io.IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");


		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            extracted();\n");
		buf.append("        } catch (java.io.IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    private void extracted() throws IOException {\n");
		buf.append("        /*[*/g();/*]*/\n");
		buf.append("    }\n");
		buf.append("    public void g() throws java.io.IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodException1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        /*[*/try {\n");
		buf.append("            g();\n");
		buf.append("        } catch (java.io.IOException e) {\n");
		buf.append("        } /*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void g() throws java.io.IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");


		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        extracted();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void extracted() {\n");
		buf.append("        /*[*/try {\n");
		buf.append("            g();\n");
		buf.append("        } catch (java.io.IOException e) {\n");
		buf.append("        } /*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void g() throws java.io.IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodReturn() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public interface I {\n");
		buf.append("        public boolean run();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        /*[*/bar(this, new I() {\n");
		buf.append("            public boolean run() {\n");
		buf.append("                return true;\n");
		buf.append("            }\n");
		buf.append("        });/*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void bar(E a, I i) {\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public interface I {\n");
		buf.append("        public boolean run();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        extracted();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void extracted() {\n");
		buf.append("        /*[*/bar(this, new I() {\n");
		buf.append("            public boolean run() {\n");
		buf.append("                return true;\n");
		buf.append("            }\n");
		buf.append("        });/*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void bar(E a, I i) {\n");
		buf.append("    }\n");
		buf.append("}\n");


		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodParameter() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String x = \"x\";\n");
		buf.append("        /*[*/String y = \"a\" + x;\n");
		buf.append("        System.out.println(x);/*]*/\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String x = \"x\";\n");
		buf.append("        extracted(x);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void extracted(String x) {\n");
		buf.append("        /*[*/String y = \"a\" + x;\n");
		buf.append("        System.out.println(x);/*]*/\n");
		buf.append("    }\n");
		buf.append("}\n");


		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodLocal() throws Exception {
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

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodLambdaExpression() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");
		buf.append("@FunctionalInterface\n");
		buf.append("interface E {\n");
		buf.append("    int foo(int i) throws IOException;\n");
		buf.append("\n");
		buf.append("    default E method(E i1) throws InterruptedException {\n");
		buf.append("        /*[*/if (i1 == null)\n");
		buf.append("            throw new InterruptedException();\n");
		buf.append("        return x -> {\n");
		buf.append("            throw new IOException();\n");
		buf.append("        };/*]*/\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");
		buf.append("@FunctionalInterface\n");
		buf.append("interface E {\n");
		buf.append("    int foo(int i) throws IOException;\n");
		buf.append("\n");
		buf.append("    default E method(E i1) throws InterruptedException {\n");
		buf.append("        return extracted(i1);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    default E extracted(E i1) throws InterruptedException {\n");
		buf.append("        /*[*/if (i1 == null)\n");
		buf.append("            throw new InterruptedException();\n");
		buf.append("        return x -> {\n");
		buf.append("            throw new IOException();\n");
		buf.append("        };/*]*/\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodGeneric() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public <E> void foo(E param) {\n");
		buf.append("        /*[*/List<E> list = new ArrayList<E>();\n");
		buf.append("        foo(param);/*]*/\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public <E> void foo(E param) {\n");
		buf.append("        extracted(param);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private <E> void extracted(E param) {\n");
		buf.append("        /*[*/List<E> list = new ArrayList<E>();\n");
		buf.append("        foo(param);/*]*/\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodGeneric1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    <T extends Comparable<? super T>> void method(List<T> list) {\n");
		buf.append("        /*[*/toExtract(list);/*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    static <T extends Comparable<? super T>> void toExtract(List<T> list) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    <T extends Comparable<? super T>> void method(List<T> list) {\n");
		buf.append("        extracted(list);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private <T extends Comparable<? super T>> void extracted(List<T> list) {\n");
		buf.append("        /*[*/toExtract(list);/*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    static <T extends Comparable<? super T>> void toExtract(List<T> list) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodFieldInitializer() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    String fS = \"foo\";\n");
		buf.append("\n");
		buf.append("    void m() {\n");
		buf.append("        new Thread() {\n");
		buf.append("            String fSub = /*]*/fS.substring(1)/*[*/;\n");
		buf.append("\n");
		buf.append("            public void run() {\n");
		buf.append("                System.out.println(fS.substring(1));\n");
		buf.append("            };\n");
		buf.append("        }.start();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    String fS = \"foo\";\n");
		buf.append("\n");
		buf.append("    void m() {\n");
		buf.append("        new Thread() {\n");
		buf.append("            String fSub = /*]*/extracted()/*[*/;\n");
		buf.append("\n");
		buf.append("            private String extracted() {\n");
		buf.append("                return fS.substring(1);\n");
		buf.append("            }\n");
		buf.append("\n");
		buf.append("            public void run() {\n");
		buf.append("                System.out.println(extracted());\n");
		buf.append("            };\n");
		buf.append("        }.start();\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodExpression() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i = 1 - (/*[*/2 + 3/*]*/);\n");
		buf.append("        int j = 1 - (2 + 3);\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i = 1 - extracted();\n");
		buf.append("        int j = 1 - extracted();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int extracted() {\n");
		buf.append("        return /*[*/2 + 3/*]*/;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodEnum() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append("    A;\n");
		buf.append("\n");
		buf.append("    static {\n");
		buf.append("        /*[*/foo();/*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private static void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append("    A;\n");
		buf.append("\n");
		buf.append("    static {\n");
		buf.append("        extracted();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private static void extracted() {\n");
		buf.append("        /*[*/foo();/*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private static void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodDuplicate() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public volatile boolean flag;\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        /*[*/do {\n");
		buf.append("            if (flag)\n");
		buf.append("                continue;\n");
		buf.append("        } while (flag);/*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void extracted() {\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public volatile boolean flag;\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        extracted2();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void extracted2() {\n");
		buf.append("        /*[*/do {\n");
		buf.append("            if (flag)\n");
		buf.append("                continue;\n");
		buf.append("        } while (flag);/*]*/\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void extracted() {\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testExtractMethodAnonymousClass() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.concurrent.ExecutorService;\n");
		buf.append("import java.util.concurrent.Executors;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public static void main(String[] args) {\n");
		buf.append("        ExecutorService executor = Executors.newSingleThreadExecutor();\n");
		buf.append("        executor.execute(new Runnable() {\n");
		buf.append("            @Override\n");
		buf.append("            public void run() {\n");
		buf.append("                String inLocalThread = \"SecondThreadValue\";\n");
		buf.append("                /*[*/System.out.println(inLocalThread);/*]*/\n");
		buf.append("            }\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public static void extracted() {}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.concurrent.ExecutorService;\n");
		buf.append("import java.util.concurrent.Executors;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public static void main(String[] args) {\n");
		buf.append("        ExecutorService executor = Executors.newSingleThreadExecutor();\n");
		buf.append("        executor.execute(() -> {\n");
		buf.append("            String inLocalThread = \"SecondThreadValue\";\n");
		buf.append("            /*[*/System.out.println(inLocalThread);/*]*/\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public static void extracted() {}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Convert to lambda expression", buf.toString());


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.concurrent.ExecutorService;\n");
		buf.append("import java.util.concurrent.Executors;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public static void main(String[] args) {\n");
		buf.append("        ExecutorService executor = Executors.newSingleThreadExecutor();\n");
		buf.append("        executor.execute(new Runnable() {\n");
		buf.append("            @Override\n");
		buf.append("            public void run() {\n");
		buf.append("                String inLocalThread = \"SecondThreadValue\";\n");
		buf.append("                extracted(inLocalThread);\n");
		buf.append("            }\n");
		buf.append("\n");
		buf.append("            private void extracted(String inLocalThread) {\n");
		buf.append("                /*[*/System.out.println(inLocalThread);/*]*/\n");
		buf.append("            }\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public static void extracted() {}\n");
		buf.append("}\n");
		Expected e2 = new Expected("Extract to method", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_METHOD);

		assertCodeActions(cu, e1, e2);
	}

}
