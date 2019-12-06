/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.ui.tests.quickfix.SerialVersionQuickFixTest.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class SerialVersionQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		options.put(JavaCore.COMPILER_PB_NO_EFFECT_ASSIGNMENT, JavaCore.IGNORE);
		options.put(JavaCore.COMPILER_PB_INDIRECT_STATIC_ACCESS, JavaCore.ERROR);
		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testLocalClass() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test3", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test5 {\n");
		buf.append("    public void test() {\n");
		buf.append("        class X implements Serializable, Cloneable, Runnable {\n");
		buf.append("            private static final int x= 1;\n");
		buf.append("            private Object y;\n");
		buf.append("            public X() {\n");
		buf.append("            }\n");
		buf.append("            public void run() {}\n");
		buf.append("            public synchronized strictfp void bar() {}\n");
		buf.append("            public String bar(int x, int y) { return null; };\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("Test5.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test5 {\n");
		buf.append("    public void test() {\n");
		buf.append("        class X implements Serializable, Cloneable, Runnable {\n");
		buf.append("            /**\n");
		buf.append("             *\n");
		buf.append("             */\n");
		buf.append("            private static final long serialVersionUID = 1L;\n");
		buf.append("            private static final int x= 1;\n");
		buf.append("            private Object y;\n");
		buf.append("            public X() {\n");
		buf.append("            }\n");
		buf.append("            public void run() {}\n");
		buf.append("            public synchronized strictfp void bar() {}\n");
		buf.append("            public String bar(int x, int y) { return null; };\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add default serial version ID", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test5 {\n");
		buf.append("    public void test() {\n");
		buf.append("        class X implements Serializable, Cloneable, Runnable {\n");
		buf.append("            /**\n");
		buf.append("             *\n");
		buf.append("             */\n");
		buf.append("            private static final long serialVersionUID = -4564939359985118485L;\n");
		buf.append("            private static final int x= 1;\n");
		buf.append("            private Object y;\n");
		buf.append("            public X() {\n");
		buf.append("            }\n");
		buf.append("            public void run() {}\n");
		buf.append("            public synchronized strictfp void bar() {}\n");
		buf.append("            public String bar(int x, int y) { return null; };\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add generated serial version ID", buf.toString());

		assertCodeActions(cu, e1, e2);
	}


	@Test
	public void testAnonymousClass() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test3", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test3 {\n");
		buf.append("    protected int var1;\n");
		buf.append("    protected int var2;\n");
		buf.append("    public void test() {\n");
		buf.append("        Serializable var3= new Serializable() {\n");
		buf.append("            int var4; \n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("Test3.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test3 {\n");
		buf.append("    protected int var1;\n");
		buf.append("    protected int var2;\n");
		buf.append("    public void test() {\n");
		buf.append("        Serializable var3= new Serializable() {\n");
		buf.append("            /**\n");
		buf.append("             *\n");
		buf.append("             */\n");
		buf.append("            private static final long serialVersionUID = 1L;\n");
		buf.append("            int var4; \n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add default serial version ID", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test3 {\n");
		buf.append("    protected int var1;\n");
		buf.append("    protected int var2;\n");
		buf.append("    public void test() {\n");
		buf.append("        Serializable var3= new Serializable() {\n");
		buf.append("            /**\n");
		buf.append("             *\n");
		buf.append("             */\n");
		buf.append("            private static final long serialVersionUID = -868523843598659436L;\n");
		buf.append("            int var4; \n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add generated serial version ID", buf.toString());

		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testInnerClass() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test2", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("\n");
		buf.append("public class Test2 {\n");
		buf.append("    protected int var1;\n");
		buf.append("    protected int var2;\n");
		buf.append("    protected class Test1 implements Serializable {\n");
		buf.append("        public long var3;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("Test2.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("\n");
		buf.append("public class Test2 {\n");
		buf.append("    protected int var1;\n");
		buf.append("    protected int var2;\n");
		buf.append("    protected class Test1 implements Serializable {\n");
		buf.append("        /**\n");
		buf.append("         *\n");
		buf.append("         */\n");
		buf.append("        private static final long serialVersionUID = 1L;\n");
		buf.append("        public long var3;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add default serial version ID", buf.toString());

		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("\n");
		buf.append("public class Test2 {\n");
		buf.append("    protected int var1;\n");
		buf.append("    protected int var2;\n");
		buf.append("    protected class Test1 implements Serializable {\n");
		buf.append("        /**\n");
		buf.append("         *\n");
		buf.append("         */\n");
		buf.append("        private static final long serialVersionUID = -4023230086280104302L;\n");
		buf.append("        public long var3;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add generated serial version ID", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testOuterClass() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test1 implements Serializable {\n");
		buf.append("    protected int var1;\n");
		buf.append("    protected int var2;\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("Test1.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test1 implements Serializable {\n");
		buf.append("    /**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    private static final long serialVersionUID = 1L;\n");
		buf.append("    protected int var1;\n");
		buf.append("    protected int var2;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add default serial version ID", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test1 implements Serializable {\n");
		buf.append("    /**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    private static final long serialVersionUID = -2242798150684569765L;\n");
		buf.append("    protected int var1;\n");
		buf.append("    protected int var2;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add generated serial version ID", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testOuterClass2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test3", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("import java.util.EventObject;\n");
		buf.append("public class Test4 extends EventObject {\n");
		buf.append("    private static final int x;\n");
		buf.append("    private static Class[] a2;\n");
		buf.append("    private volatile Class a1;\n");
		buf.append("    static {\n");
		buf.append("        x= 1;\n");
		buf.append("    }\n");
		buf.append("    {\n");
		buf.append("        a1= null;\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    public Test4(Object source) {\n");
		buf.append("        super(source);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("Test4.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("import java.util.EventObject;\n");
		buf.append("public class Test4 extends EventObject {\n");
		buf.append("    /**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    private static final long serialVersionUID = 1L;\n");
		buf.append("    private static final int x;\n");
		buf.append("    private static Class[] a2;\n");
		buf.append("    private volatile Class a1;\n");
		buf.append("    static {\n");
		buf.append("        x= 1;\n");
		buf.append("    }\n");
		buf.append("    {\n");
		buf.append("        a1= null;\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    public Test4(Object source) {\n");
		buf.append("        super(source);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add default serial version ID", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("import java.util.EventObject;\n");
		buf.append("public class Test4 extends EventObject {\n");
		buf.append("    /**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    private static final long serialVersionUID = -7476608308201363525L;\n");
		buf.append("    private static final int x;\n");
		buf.append("    private static Class[] a2;\n");
		buf.append("    private volatile Class a1;\n");
		buf.append("    static {\n");
		buf.append("        x= 1;\n");
		buf.append("    }\n");
		buf.append("    {\n");
		buf.append("        a1= null;\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    public Test4(Object source) {\n");
		buf.append("        super(source);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add generated serial version ID", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testOuterClass3() throws Exception {
		// longer package

		IPackageFragment pack1= fSourceFolder.createPackageFragment("a.b.c", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package a.b.c;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test1 implements Serializable {\n");
		buf.append("    protected int var1;\n");
		buf.append("    class Test1Inner {}\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("Test1.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package a.b.c;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test1 implements Serializable {\n");
		buf.append("    /**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    private static final long serialVersionUID = 1L;\n");
		buf.append("    protected int var1;\n");
		buf.append("    class Test1Inner {}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add default serial version ID", buf.toString());

		buf = new StringBuilder();
		buf.append("package a.b.c;\n");
		buf.append("import java.io.Serializable;\n");
		buf.append("public class Test1 implements Serializable {\n");
		buf.append("    /**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    private static final long serialVersionUID = -3715240305486851194L;\n");
		buf.append("    protected int var1;\n");
		buf.append("    class Test1Inner {}\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add generated serial version ID", buf.toString());

		assertCodeActions(cu, e1, e2);
	}
}
