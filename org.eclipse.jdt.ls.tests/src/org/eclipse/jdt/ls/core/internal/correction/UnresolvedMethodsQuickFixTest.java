/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/UnresolvedTypesQuickFixTest.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Rabea Gransberger <rgransberger@gmx.de> - [quick fix] Fix several visibility issues - https://bugs.eclipse.org/394692
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class UnresolvedMethodsQuickFixTest extends AbstractQuickFixTest {
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		IJavaProject fJProject1 = newEmptyProject();
		Map<String, String> options = TestOptions.getDefaultOptions();
		options.put(JavaCore.COMPILER_PB_NO_EFFECT_ASSIGNMENT, JavaCore.IGNORE);
		options.put(JavaCore.COMPILER_PB_INDIRECT_STATIC_ACCESS, JavaCore.ERROR);

		fJProject1.setOptions(options);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testMethodInSameType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int goo(Vector vec, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Vector, boolean)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodInForInit() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (int i= 0, j= goo(3); i < 0; i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (int i= 0, j= goo(3); i < 0; i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int goo(int i) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(int)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodInInfixExpression1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private boolean foo() {\n");
		buf.append("        return f(1) || f(2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private boolean foo() {\n");
		buf.append("        return f(1) || f(2);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private boolean f(int i) {\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'f(int)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodInInfixExpression2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private boolean foo() {\n");
		buf.append("        return f(1) == f(2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private boolean foo() {\n");
		buf.append("        return f(1) == f(2);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private Object f(int i) {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'f(int)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodSpacing0EmptyLines() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("    private int goo(Vector vec, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Vector, boolean)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodSpacing1EmptyLine() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int goo(Vector vec, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Vector, boolean)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodSpacing2EmptyLines() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    \n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    \n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("\n");
		buf.append("    private int goo(Vector vec, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Vector, boolean)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodSpacingComment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("//comment\n");
		buf.append("\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("//comment\n");
		buf.append("\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int goo(Vector vec, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Vector, boolean)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodSpacingJavadoc() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * javadoc\n");
		buf.append("     */\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * javadoc\n");
		buf.append("     */\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int goo(Vector vec, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Vector, boolean)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodSpacingNonJavadoc() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /*\n");
		buf.append("     * non javadoc\n");
		buf.append("     */\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    void fred() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /*\n");
		buf.append("     * non javadoc\n");
		buf.append("     */\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int goo(Vector vec, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Vector, boolean)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodInSameTypeUsingThis() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= this.goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        int i= this.goo(vec, true);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int goo(Vector vec, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Vector, boolean)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodInDifferentClass() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(X x) {\n");
		buf.append("        if (x instanceof Y) {\n");
		buf.append("            boolean i= x.goo(1, 2.1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface Y {\n");
		buf.append("    public boolean goo(int i, double d);\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Y.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("\n");
		buf.append("    public boolean goo(int i, double d) {\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(int, double)' in type 'X'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(X x) {\n");
		buf.append("        if (x instanceof Y) {\n");
		buf.append("            boolean i= ((Y) x).goo(1, 2.1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add cast to 'x'", buf.toString());
		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testParameterWithTypeVariable() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Bork<T> {\n");
		buf.append("    private Help help = new Help();\n");
		buf.append("    public void method() {\n");
		buf.append("        help.help(this);\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("\n");
		buf.append("class Help {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Bork.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Bork<T> {\n");
		buf.append("    private Help help = new Help();\n");
		buf.append("    public void method() {\n");
		buf.append("        help.help(this);\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("\n");
		buf.append("class Help {\n");
		buf.append("\n");
		buf.append("    public void help(Bork<T> bork) {\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Create method 'help(Bork<T>)' in type 'Help'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testParameterAnonymous() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("        foo(new Runnable() {\n");
		buf.append("            public void run() {}\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("        foo(new Runnable() {\n");
		buf.append("            public void run() {}\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void foo(Runnable runnable) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'foo(Runnable)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodInGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X<A> {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface Y<A> {\n");
		buf.append("    public boolean goo(X<A> a);\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Y.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Y<Object> y;\n");
		buf.append("    void foo(X<String> x) {\n");
		buf.append("        boolean i= x.goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X<A> {\n");
		buf.append("\n");
		buf.append("    public boolean goo(X<String> x) {\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(X<String>)' in type 'X'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Y<Object> y;\n");
		buf.append("    void foo(X<String> x) {\n");
		buf.append("        boolean i= ((Y<Object>) x).goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add cast to 'x'", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMethodAssignedToWildcard() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? extends Number> vec) {\n");
		buf.append("        vec.add(goo());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? extends Number> vec) {\n");
		buf.append("        vec.add(goo());\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private Object goo() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo()'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodAssignedToWildcard2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? super Number> vec) {\n");
		buf.append("        vec.add(goo());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? super Number> vec) {\n");
		buf.append("        vec.add(goo());\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private Number goo() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo()'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodAssignedFromWildcard1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? super Number> vec) {\n");
		buf.append("        goo(vec.get(0));\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? super Number> vec) {\n");
		buf.append("        goo(vec.get(0));\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void goo(Object object) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Object)'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodAssignedFromWildcard2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void testMethod(Vector<? extends Number> vec) {\n");
		buf.append("        goo(vec.get(0));\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void goo(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void testMethod(Vector<? extends Number> vec) {\n");
		buf.append("        goo(vec.get(0));\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void goo(Number number) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'goo(int)' to 'goo(Number)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void testMethod(Vector<? extends Number> vec) {\n");
		buf.append("        goo((int) vec.get(0));\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void goo(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Cast argument 'vec.get(0)' to 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void testMethod(Vector<? extends Number> vec) {\n");
		buf.append("        goo(vec.get(0));\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void goo(Number number) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void goo(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'goo(Number)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testMethodInGenericTypeSameCU() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public class X<A> {\n");
		buf.append("    }\n");
		buf.append("    int foo(X<String> x) {\n");
		buf.append("        return x.goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public class X<A> {\n");
		buf.append("\n");
		buf.append("        public int goo(X<String> x) {\n");
		buf.append("            return 0;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    int foo(X<String> x) {\n");
		buf.append("        return x.goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(X<String>)' in type 'X'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public class X<A> {\n");
		buf.append("    }\n");
		buf.append("    int foo(X<String> x) {\n");
		buf.append("        return ((Object) x).goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add cast to 'x'", buf.toString());

		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testMethodInRawType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X<A> {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface Y<A> {\n");
		buf.append("    public boolean goo(X<A> a);\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Y.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Y<Object> y;\n");
		buf.append("    void foo(X x) {\n");
		buf.append("        boolean i= x.goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X<A> {\n");
		buf.append("\n");
		buf.append("    public boolean goo(X x) {\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(X)' in type 'X'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Y<Object> y;\n");
		buf.append("    void foo(X x) {\n");
		buf.append("        boolean i= ((Y<Object>) x).goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add cast to 'x'", buf.toString());

		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testMethodInAnonymous1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                xoo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                foo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'foo(..)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                xoo();\n");
		buf.append("            }\n");
		buf.append("\n");
		buf.append("            private void xoo() {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create method 'xoo()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                xoo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    protected void xoo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'xoo()' in type 'E'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testMethodInAnonymous2() throws Exception {
		IPackageFragment pack0 = fSourceFolder.createPackageFragment("other", false, null);

		StringBuilder buf = new StringBuilder();
		buf = new StringBuilder();
		buf.append("package other;\n");
		buf.append("public class A {\n");
		buf.append("}\n");
		pack0.createCompilationUnit("A.java", buf.toString(), false, null);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import other.A;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                A.xoo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package other;\n");
		buf.append("public class A {\n");
		buf.append("\n");
		buf.append("    public static void xoo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'xoo()' in type 'A'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMethodInAnonymous3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                xoo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                foo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'foo(..)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                xoo();\n");
		buf.append("            }\n");
		buf.append("\n");
		buf.append("            private void xoo() {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create method 'xoo()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                xoo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    protected static void xoo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'xoo()' in type 'E'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testMethodInAnonymous4() throws Exception {
		// bug 266032
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void foo(final E e) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                e.foobar();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void foo(final E e) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                e.foobar();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    protected void foobar() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'foobar()' in type 'E'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void foo(final E e) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                ((Object) e).foobar();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add cast to 'e'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMethodInAnonymousGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Comparable<String>() {\n");
		buf.append("            public int compareTo(String s) {\n");
		buf.append("                xoo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Comparable<String>() {\n");
		buf.append("            public int compareTo(String s) {\n");
		buf.append("                foo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'foo(..)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Comparable<String>() {\n");
		buf.append("            public int compareTo(String s) {\n");
		buf.append("                xoo();\n");
		buf.append("            }\n");
		buf.append("\n");
		buf.append("            private void xoo() {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create method 'xoo()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Comparable<String>() {\n");
		buf.append("            public int compareTo(String s) {\n");
		buf.append("                xoo();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    protected void xoo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'xoo()' in type 'E<T>'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);

	}

	@Test
	public void testMethodInAnonymousCovering1() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                run(1);\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                E.this.run(1);\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Qualify with enclosing type 'E'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                run();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Remove argument to match 'run()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run(int i) {\n");
		buf.append("                run(1);\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change method 'run()': Add parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                run(1);\n");
		buf.append("            }\n");
		buf.append("\n");
		buf.append("            private void run(int i) {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create method 'run(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testMethodInAnonymousCovering2() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                run(1);\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                E.run(1);\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Qualify with enclosing type 'E'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                run();\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Remove argument to match 'run()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run(int i) {\n");
		buf.append("                run(1);\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change method 'run()': Add parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                run(1);\n");
		buf.append("            }\n");
		buf.append("\n");
		buf.append("            private void run(int i) {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create method 'run(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testMethodInAnonymousCovering3() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public class Inner {\n");
		buf.append("        public void run() {\n");
		buf.append("            run(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public class Inner {\n");
		buf.append("        public void run() {\n");
		buf.append("            E.this.run(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Qualify with enclosing type 'E'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public class Inner {\n");
		buf.append("        public void run() {\n");
		buf.append("            run();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Remove argument to match 'run()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public class Inner {\n");
		buf.append("        public void run(int i) {\n");
		buf.append("            run(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change method 'run()': Add parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public class Inner {\n");
		buf.append("        public void run() {\n");
		buf.append("            run(1);\n");
		buf.append("        }\n");
		buf.append("\n");
		buf.append("        private void run(int i) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create method 'run(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testMethodInAnonymousCovering4() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public static class Inner {\n");
		buf.append("        public void run() {\n");
		buf.append("            run(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public static class Inner {\n");
		buf.append("        public void run() {\n");
		buf.append("            run();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'run()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public static class Inner {\n");
		buf.append("        public void run(int i) {\n");
		buf.append("            run(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'run()': Add parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void run(int i) {\n");
		buf.append("    }\n");
		buf.append("    public static class Inner {\n");
		buf.append("        public void run() {\n");
		buf.append("            run(1);\n");
		buf.append("        }\n");
		buf.append("\n");
		buf.append("        private void run(int i) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'run(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testMethodInDifferentInterface() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(X x) {\n");
		buf.append("        boolean i= x.goo(getClass());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface X {\n");
		buf.append("\n");
		buf.append("    boolean goo(Class<? extends E> class1);\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'goo(Class<? extends E>)' in type 'X'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(X x) {\n");
		buf.append("        boolean i= ((Object) x).goo(getClass());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add cast to 'x'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMethodInArrayAccess() throws Exception {
		// bug 148011
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("p", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        int i = bar()[0];\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        int i = bar()[0];\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private int[] bar() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'bar()'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testParameterMismatchCast() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        long x= 0;\n");
		buf.append("        foo(x + 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(long l) {\n");
		buf.append("        long x= 0;\n");
		buf.append("        foo(x + 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'foo(int)' to 'foo(long)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        long x= 0;\n");
		buf.append("        foo((int) (x + 1));\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Cast argument 'x + 1' to 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        long x= 0;\n");
		buf.append("        foo(x + 1);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void foo(long l) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(long)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchCast2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        double x= 0.0;\n");
		buf.append("        X.xoo((float) x, this);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public static void xoo(int i, Object o) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public static void xoo(float x, Object o) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'xoo(int, Object)' to 'xoo(float, Object)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        double x= 0.0;\n");
		buf.append("        X.xoo((int) x, this);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Cast argument '(float)x' to 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public static void xoo(int i, Object o) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public static void xoo(float x, E o) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'xoo(float, E)' in type 'X'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchCastBoxing() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Integer i) {\n");
		buf.append("        foo(1.0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(double d) {\n");
		buf.append("        foo(1.0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'foo(Integer)' to 'foo(double)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Integer i) {\n");
		buf.append("        foo((int) 1.0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Cast argument '1.0' to 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Integer i) {\n");
		buf.append("        foo(1.0);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void foo(double d) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(double)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchChangeVarType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(Vector v) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        long x= 0;\n");
		buf.append("        goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(long x) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        long x= 0;\n");
		buf.append("        goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'goo(Vector)' to 'goo(long)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(Vector v) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        Vector x= 0;\n");
		buf.append("        goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'x' to 'Vector'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(Vector v) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        long x= 0;\n");
		buf.append("        goo(x);\n");
		buf.append("    }\n");
		buf.append("    private void goo(long x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'goo(long)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchChangeVarTypeInGeneric() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public void goo(Vector<T> v) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void foo(A<Number> a, long x) {\n");
		buf.append("        a.goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public void goo(long x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'goo(Vector<T>)' to 'goo(long)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void foo(A<Number> a, Vector<Number> x) {\n");
		buf.append("        a.goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'x' to 'Vector<Number>'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public void goo(Vector<T> v) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void goo(long x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'goo(long)' in type 'A'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchKeepModifiers() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Collections;\n");
		buf.append("\n");
		buf.append("class E {\n");
		buf.append("    void foo(@Deprecated final String map){}\n");
		buf.append("    {foo(Collections.EMPTY_MAP);}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Collections;\n");
		buf.append("import java.util.Map;\n");
		buf.append("\n");
		buf.append("class E {\n");
		buf.append("    void foo(@Deprecated final Map emptyMap){}\n");
		buf.append("    {foo(Collections.EMPTY_MAP);}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'foo(String)' to 'foo(Map)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Collections;\n");
		buf.append("import java.util.Map;\n");
		buf.append("\n");
		buf.append("class E {\n");
		buf.append("    void foo(@Deprecated final String map){}\n");
		buf.append("    {foo(Collections.EMPTY_MAP);}\n");
		buf.append("    private void foo(Map emptyMap) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create method 'foo(Map)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testParameterMismatchChangeFieldType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    int fCount= 0;\n");
		buf.append("    public void goo(Vector v) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo(fCount);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    int fCount= 0;\n");
		buf.append("    public void goo(int fCount2) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo(fCount);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'goo(Vector)' to 'goo(int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    Vector fCount= 0;\n");
		buf.append("    public void goo(Vector v) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo(fCount);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'fCount' to 'Vector'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    int fCount= 0;\n");
		buf.append("    public void goo(Vector v) {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo(fCount);\n");
		buf.append("    }\n");
		buf.append("    private void goo(int fCount2) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'goo(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchChangeFieldTypeInGeneric() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X<A> {\n");
		buf.append("    String count= 0;\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(Vector<String> v) {\n");
		buf.append("    }\n");
		buf.append("    public void foo(X<String> x, int y) {\n");
		buf.append("        goo(x.count);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(String count) {\n");
		buf.append("    }\n");
		buf.append("    public void foo(X<String> x, int y) {\n");
		buf.append("        goo(x.count);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'goo(Vector<String>)' to 'goo(String)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class X<A> {\n");
		buf.append("    Vector<String> count= 0;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'count' to 'Vector<String>'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(Vector<String> v) {\n");
		buf.append("    }\n");
		buf.append("    public void foo(X<String> x, int y) {\n");
		buf.append("        goo(x.count);\n");
		buf.append("    }\n");
		buf.append("    private void goo(String count) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'goo(String)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchChangeMethodType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(Vector v) {\n");
		buf.append("    }\n");
		buf.append("    public int foo() {\n");
		buf.append("        goo(this.foo());\n");
		buf.append("        return 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(int i) {\n");
		buf.append("    }\n");
		buf.append("    public int foo() {\n");
		buf.append("        goo(this.foo());\n");
		buf.append("        return 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'goo(Vector)' to 'goo(int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(Vector v) {\n");
		buf.append("    }\n");
		buf.append("    public Vector foo() {\n");
		buf.append("        goo(this.foo());\n");
		buf.append("        return 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change return type of 'foo(..)' to 'Vector'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo(Vector v) {\n");
		buf.append("    }\n");
		buf.append("    public int foo() {\n");
		buf.append("        goo(this.foo());\n");
		buf.append("        return 9;\n");
		buf.append("    }\n");
		buf.append("    private void goo(int foo) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'goo(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchChangeMethodTypeBug102142() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class Foo {\n");
		buf.append("    Foo(String string) {\n");
		buf.append("        System.out.println(string);\n");
		buf.append("    }  \n");
		buf.append("    private void bar() {\n");
		buf.append("        new Foo(3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Foo.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Foo {\n");
		buf.append("    Foo(int i) {\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }  \n");
		buf.append("    private void bar() {\n");
		buf.append("        new Foo(3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change constructor 'Foo(String)' to 'Foo(int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Foo {\n");
		buf.append("    Foo(String string) {\n");
		buf.append("        System.out.println(string);\n");
		buf.append("    }  \n");
		buf.append("    public Foo(int i) {\n");
		buf.append("    }\n");
		buf.append("    private void bar() {\n");
		buf.append("        new Foo(3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constructor 'Foo(int)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testParameterMismatchChangeMethodTypeInGeneric() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void goo(Vector<String> v) {\n");
		buf.append("    }\n");
		buf.append("    public int foo() {\n");
		buf.append("        goo(this.foo());\n");
		buf.append("        return 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void goo(int i) {\n");
		buf.append("    }\n");
		buf.append("    public int foo() {\n");
		buf.append("        goo(this.foo());\n");
		buf.append("        return 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'goo(Vector<String>)' to 'goo(int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void goo(Vector<String> v) {\n");
		buf.append("    }\n");
		buf.append("    public Vector<String> foo() {\n");
		buf.append("        goo(this.foo());\n");
		buf.append("        return 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change return type of 'foo(..)' to 'Vector<String>'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void goo(Vector<String> v) {\n");
		buf.append("    }\n");
		buf.append("    public int foo() {\n");
		buf.append("        goo(this.foo());\n");
		buf.append("        return 9;\n");
		buf.append("    }\n");
		buf.append("    private void goo(int foo) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'goo(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchLessArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String s, int i, Object o) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        foo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String s, int i, Object o) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        foo(s, x, o);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add arguments to match 'foo(String, int, Object)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        foo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'foo(String, int, Object)': Remove parameters 'String, Object'",
				buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String s, int i, Object o) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        foo(x);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void foo(int x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchLessArguments2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        X.xoo(null);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public static void xoo(int i, Object o) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        X.xoo(0, null);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add argument to match 'xoo(int, Object)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public static void xoo(Object o) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'xoo(int, Object)': Remove parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public static void xoo(int i, Object o) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public static void xoo(Object object) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'xoo(Object)' in type 'X'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchLessArguments3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        X.xoo(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     *                  More about the int value\n");
		buf.append("     * @param o The Object value\n");
		buf.append("     */\n");
		buf.append("    public static void xoo(int i, Object o) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        X.xoo(1, null);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add argument to match 'xoo(int, Object)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     *                  More about the int value\n");
		buf.append("     */\n");
		buf.append("    public static void xoo(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'xoo(int, Object)': Remove parameter 'Object'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     *                  More about the int value\n");
		buf.append("     * @param o The Object value\n");
		buf.append("     */\n");
		buf.append("    public static void xoo(int i, Object o) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public static void xoo(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'xoo(int)' in type 'X'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchLessArgumentsInGeneric() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface X<S, T extends Number> {\n");
		buf.append("    public void foo(S s, int i, T t);\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public abstract class E implements X<String, Integer> {\n");
		buf.append("    public void meth(E e, String s) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        e.foo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public abstract class E implements X<String, Integer> {\n");
		buf.append("    public void meth(E e, String s) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        e.foo(s, x, x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add arguments to match 'foo(String, int, Integer)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface X<S, T extends Number> {\n");
		buf.append("    public void foo(int i);\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'foo(S, int, T)': Remove parameters 'S, T'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public abstract class E implements X<String, Integer> {\n");
		buf.append("    public void meth(E e, String s) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        e.foo(x);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void foo(int x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testSuperConstructorLessArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public X(Object o, int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E extends X {\n");
		buf.append("    public E() {\n");
		buf.append("        super(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E extends X {\n");
		buf.append("    public E() {\n");
		buf.append("        super(new Vector(), 0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add argument to match 'X(Object, int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public X(Object o) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'X(Object, int)': Remove parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("    public X(Object o, int i) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public X(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'X(Vector)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testConstructorInvocationLessArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(new Vector(), 0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add argument to match 'E(Object, int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public E(Object o) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'E(Object, int)': Remove parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("    public E(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'E(Vector)'", buf.toString());
		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testConstructorInvocationLessArgumentsInGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(new Vector(), 0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add argument to match 'E(Object, int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public E(Object o) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'E(Object, int)': Remove parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("    public E(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'E<T>(Vector)'", buf.toString());
		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchMoreArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(X x) {\n");
		buf.append("        x.xoo(1, 1, x.toString());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public void xoo(int i, String o) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(X x) {\n");
		buf.append("        x.xoo(1, x.toString());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'xoo(int, String)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public void xoo(int i, int j, String o) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'xoo(int, String)': Add parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public void xoo(int i, String o) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void xoo(int i, int j, String string) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'xoo(int, int, String)' in type 'X'", buf.toString());
		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchMoreArguments2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String s) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        foo(s, x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String s) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        foo(s);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'foo(String)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String s, int x2) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        foo(s, x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'foo(String)': Add parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String s) {\n");
		buf.append("        int x= 0;\n");
		buf.append("        foo(s, x);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void foo(String s, int x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(String, int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchMoreArguments3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("import java.util.Collections;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(X x) {\n");
		buf.append("        x.xoo(Collections.EMPTY_SET, 1, 2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     */\n");
		buf.append("    public void xoo(int i) {\n");
		buf.append("       int j= 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Collections;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(X x) {\n");
		buf.append("        x.xoo(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove arguments to match 'xoo(int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Set;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("    /**\n");
		buf.append("     * @param emptySet\n");
		buf.append("     * @param i The int value\n");
		buf.append("     * @param k\n");
		buf.append("     */\n");
		buf.append("    public void xoo(Set emptySet, int i, int k) {\n");
		buf.append("       int j= 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'xoo(int)': Add parameters 'Set, int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Set;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     */\n");
		buf.append("    public void xoo(int i) {\n");
		buf.append("       int j= 0;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void xoo(Set emptySet, int i, int j) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'xoo(Set, int, int)' in type 'X'", buf.toString());
		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchMoreArguments4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object[] o= null;\n");
		buf.append("        foo(o.length);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object[] o= null;\n");
		buf.append("        foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'foo()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int length) {\n");
		buf.append("        Object[] o= null;\n");
		buf.append("        foo(o.length);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'foo()': Add parameter 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object[] o= null;\n");
		buf.append("        foo(o.length);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void foo(int length) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchMoreArgumentsInGeneric() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void foo(X<T> x) {\n");
		buf.append("        x.xoo(x.toString(), x, 2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X<T> {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     */\n");
		buf.append("    public void xoo(String s) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> {\n");
		buf.append("    public void foo(X<T> x) {\n");
		buf.append("        x.xoo(x.toString());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove arguments to match 'xoo(String)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X<T> {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     */\n");
		buf.append("    public void xoo(String s) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void xoo(String string, X<T> x, int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create method 'xoo(String, X<T>, int)' in type 'X'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testSuperConstructorMoreArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public X() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E extends X {\n");
		buf.append("    public E() {\n");
		buf.append("        super(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E extends X {\n");
		buf.append("    public E() {\n");
		buf.append("        super();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'X()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("    public X(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'X()': Add parameter 'Vector'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("    public X() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public X(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'X(Vector)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testConstructorInvocationMoreArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("        this();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'E()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public E(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'E()': Add parameter 'Vector'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("    public E(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'E(Vector)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testConstructorInvocationMoreArguments2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * My favourite constructor.\n");
		buf.append("     */\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * My favourite constructor.\n");
		buf.append("     */\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("        this();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'E()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * My favourite constructor.\n");
		buf.append("     * @param vector\n");
		buf.append("     */\n");
		buf.append("    public E(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'E()': Add parameter 'Vector'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * My favourite constructor.\n");
		buf.append("     */\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("    public E(Object o, int i) {\n");
		buf.append("        this(new Vector());\n");
		buf.append("    }\n");
		buf.append("    public E(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'E(Vector)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchSwap() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, String[] o) {\n");
		buf.append("        foo(new String[] { }, i - 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, String[] o) {\n");
		buf.append("        foo(i - 1, new String[] { });\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Swap arguments 'new String[]{}' and 'i - 1'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String[] o, int i) {\n");
		buf.append("        foo(new String[] { }, i - 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'foo(int, String[])': Swap parameters 'int, String[]'",
				buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, String[] o) {\n");
		buf.append("        foo(new String[] { }, i - 1);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void foo(String[] strings, int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(String[], int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchSwapInGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public void b(int i, T[] t) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public enum E {\n");
		buf.append("    CONST1, CONST2;\n");
		buf.append("    public void foo(A<String> a) {\n");
		buf.append("        a.b(new String[1], 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public enum E {\n");
		buf.append("    CONST1, CONST2;\n");
		buf.append("    public void foo(A<String> a) {\n");
		buf.append("        a.b(1, new String[1]);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Swap arguments 'new String[1]' and '1'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public void b(T[] t, int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'b(int, T[])': Swap parameters 'int, T[]'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public void b(int i, T[] t) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void b(String[] strings, int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'b(String[], int)' in type 'A'", buf.toString());


		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchWithExtraDimensions() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class ArrayTest {\n");
		buf.append("        public void test(String[] a){\n");
		buf.append("                foo(a);\n");
		buf.append("        }\n");
		buf.append("        private void foo(int a[]) {\n");
		buf.append("        }\n");

		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("ArrayTest.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class ArrayTest {\n");
		buf.append("        public void test(String[] a){\n");
		buf.append("                foo(a);\n");
		buf.append("        }\n");
		buf.append("        private void foo(String[] a) {\n");
		buf.append("        }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'foo(int[])' to 'foo(String[])'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class ArrayTest {\n");
		buf.append("        public void test(int[] a){\n");
		buf.append("                foo(a);\n");
		buf.append("        }\n");
		buf.append("        private void foo(int a[]) {\n");
		buf.append("        }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'a' to 'int[]'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class ArrayTest {\n");
		buf.append("        public void test(String[] a){\n");
		buf.append("                foo(a);\n");
		buf.append("        }\n");
		buf.append("        private void foo(String[] a) {\n");
		buf.append("        }\n");
		buf.append("        private void foo(int a[]) {\n");
		buf.append("        }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(String[])'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testParameterMismatchWithVarArgs() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class ArrayTest {\n");
		buf.append("        public void test(String[] a){\n");
		buf.append("                foo(a, a);\n");
		buf.append("        }\n");
		buf.append("        private void foo(int[] a, int... i) {\n");
		buf.append("        }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("ArrayTest.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class ArrayTest {\n");
		buf.append("        public void test(String[] a){\n");
		buf.append("                foo(a, a);\n");
		buf.append("        }\n");
		buf.append("        private void foo(String[] a, String... a2) {\n");
		buf.append("        }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method 'foo(int[], int...)' to 'foo(String[], String...)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class ArrayTest {\n");
		buf.append("        public void test(String[] a){\n");
		buf.append("                foo(a, a);\n");
		buf.append("        }\n");
		buf.append("        private void foo(String[] a, String[] a2) {\n");
		buf.append("        }\n");
		buf.append("        private void foo(int[] a, int... i) {\n");
		buf.append("        }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create method 'foo(String[], String[])'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testParameterMismatchSwap2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     * @param o The Object value\n");
		buf.append("     * @param b The boolean value\n");
		buf.append("     *                  More about the boolean value\n");
		buf.append("     */\n");
		buf.append("    public void foo(int i, Object o, boolean b) {\n");
		buf.append("        foo(false, o, i - 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     * @param o The Object value\n");
		buf.append("     * @param b The boolean value\n");
		buf.append("     *                  More about the boolean value\n");
		buf.append("     */\n");
		buf.append("    public void foo(int i, Object o, boolean b) {\n");
		buf.append("        foo(i - 1, o, false);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Swap arguments 'false' and 'i - 1'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param b The boolean value\n");
		buf.append("     *                  More about the boolean value\n");
		buf.append("     * @param o The Object value\n");
		buf.append("     * @param i The int value\n");
		buf.append("     */\n");
		buf.append("    public void foo(boolean b, Object o, int i) {\n");
		buf.append("        foo(false, o, i - 1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'foo(int, Object, boolean)': Swap parameters 'int, boolean'",
				buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The int value\n");
		buf.append("     * @param o The Object value\n");
		buf.append("     * @param b The boolean value\n");
		buf.append("     *                  More about the boolean value\n");
		buf.append("     */\n");
		buf.append("    public void foo(int i, Object o, boolean b) {\n");
		buf.append("        foo(false, o, i - 1);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private void foo(boolean b, Object o, int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(boolean, Object, int)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testSuperConstructor() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    public E(int i) {\n");
		buf.append("        super(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    public E(int i) {\n");
		buf.append("        super();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'A()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("\n");
		buf.append("    public A(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constructor 'A(int)'", buf.toString());

		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testClassInstanceCreation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A a= new A(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A a= new A();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'A()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("\n");
		buf.append("    public A(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constructor 'A(int)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testClassInstanceCreation2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A a= new A(\"test\");\n");
		buf.append("    }\n");
		buf.append("    class A {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A a= new A();\n");
		buf.append("    }\n");
		buf.append("    class A {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'A()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A a= new A(\"test\");\n");
		buf.append("    }\n");
		buf.append("    class A {\n");
		buf.append("\n");
		buf.append("        public A(String string) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constructor 'A(String)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testClassInstanceCreationInGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A<String> a= new A<String>(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A<String> a= new A<String>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'A<String>()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("\n");
		buf.append("    public A(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constructor 'A<T>(int)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testClassInstanceCreationMoreArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A a= new A(i, String.valueOf(i), true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public A(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A a= new A(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove arguments to match 'A(int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public A(int i, String string, boolean b) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'A(int)': Add parameters 'String, boolean'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public A(int i) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public A(int i, String valueOf, boolean b) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'A(int, String, boolean)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testClassInstanceCreationMoreArgumentsInGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A<List<? extends E>> a= new A<List<? extends E>>(i, String.valueOf(i), true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public A(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A<List<? extends E>> a= new A<List<? extends E>>(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove arguments to match 'A<List<? extends E>>(int)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public A(int i, String string, boolean b) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'A(int)': Add parameters 'String, boolean'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public A(int i) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public A(int i, String valueOf, boolean b) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'A<T>(int, String, boolean)'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testClassInstanceCreationLessArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A a= new A();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public A(int i, String s) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A a= new A(i, null);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add arguments to match 'A(int, String)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public A() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'A(int, String)': Remove parameters 'int, String'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public A(int i, String s) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public A() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'A()'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testClassInstanceCreationLessArgumentsInGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A<List<String>> a= new A<List<String>>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public A(int i, String s) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        A<List<String>> a= new A<List<String>>(i, null);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add arguments to match 'A<List<String>>(int, String)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public A() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change constructor 'A(int, String)': Remove parameters 'int, String'",
				buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A<T> {\n");
		buf.append("    public A(int i, String s) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public A() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constructor 'A<T>()'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testConstructorInvocation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E(int i) {\n");
		buf.append("        this(i, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E(int i) {\n");
		buf.append("        this(i, true);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public E(int i, boolean b) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create constructor 'E(int, boolean)'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testConstructorInvocationInGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<S, T> {\n");
		buf.append("    public E(int i) {\n");
		buf.append("        this(i, true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<S, T> {\n");
		buf.append("    public E(int i) {\n");
		buf.append("        this(i, true);\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public E(int i, boolean b) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create constructor 'E<S, T>(int, boolean)'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testSuperMethodInvocation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        super.foo(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'foo(int)' in type 'A'", buf.toString());

		assertCodeActionExists(cu, e1);

	}

	@Test
	public void testSuperMethodMoreArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E extends X {\n");
		buf.append("    public void xoo() {\n");
		buf.append("        super.foo(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E extends X {\n");
		buf.append("    public void xoo() {\n");
		buf.append("        super.foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove argument to match 'foo()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("    public int foo(Vector vector) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'foo()': Add parameter 'Vector'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void foo(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(Vector)' in type 'X'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testSuperMethodLessArguments() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public int foo(Object o, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E extends X {\n");
		buf.append("    public void xoo() {\n");
		buf.append("        super.foo(new Vector());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E extends X {\n");
		buf.append("    public void xoo() {\n");
		buf.append("        super.foo(new Vector(), false);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add argument to match 'foo(Object, boolean)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class X {\n");
		buf.append("    public int foo(Object o) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method 'foo(Object, boolean)': Remove parameter 'boolean'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("    public int foo(Object o, boolean b) {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void foo(Vector vector) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create method 'foo(Vector)' in type 'X'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testMissingCastParents1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        String x= (String) o.substring(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        String x= ((String) o).substring(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add parentheses around cast", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMissingCastParents2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        String x= (String) o.substring(1).toLowerCase();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        String x= ((String) o).substring(1).toLowerCase();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add parentheses around cast", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testMissingCastParents3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static Object obj;\n");
		buf.append("    public void foo() {\n");
		buf.append("        String x= (String) E.obj.substring(1).toLowerCase();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static Object obj;\n");
		buf.append("    public void foo() {\n");
		buf.append("        String x= ((String) E.obj).substring(1).toLowerCase();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add parentheses around cast", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testArrayAccess() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static Object obj;\n");
		buf.append("    public String foo(Object[] array) {\n");
		buf.append("        return array.tostring();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static Object obj;\n");
		buf.append("    public String foo(Object[] array) {\n");
		buf.append("        return array.length;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'length'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static Object obj;\n");
		buf.append("    public String foo(Object[] array) {\n");
		buf.append("        return array.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change to 'toString(..)'", buf.toString());

		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testIncompleteThrowsStatement() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object[] array) {\n");
		buf.append("        throw RuntimeException();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object[] array) {\n");
		buf.append("        throw new RuntimeException();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Insert 'new' keyword", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object[] array) {\n");
		buf.append("        throw RuntimeException();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private Exception RuntimeException() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create method 'RuntimeException()'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingAnnotationAttribute1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    @Annot(count= 1)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("\n");
		buf.append("        int count();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    @Annot(count= 1)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create attribute 'count()'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testMissingAnnotationAttribute2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    @Annot(1)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("\n");
		buf.append("        int value();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    @Annot(1)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create attribute 'value()'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testStaticImportFavorite1() throws Exception {
		List<String> favorites = new ArrayList<>();
		favorites.add("java.lang.Math.*");
		PreferenceManager.getPrefs(null).setJavaCompletionFavoriteMembers(favorites);
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
			StringBuilder buf = new StringBuilder();
			buf.append("package pack;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("    private int foo() {\n");
			buf.append("        return max(1, 2);\n");
			buf.append("    }\n");
			buf.append("}\n");
			ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

			buf = new StringBuilder();
			buf.append("package pack;\n");
			buf.append("\n");
			buf.append("import static java.lang.Math.max;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("    private int foo() {\n");
			buf.append("        return max(1, 2);\n");
			buf.append("    }\n");
			buf.append("}\n");
			Expected e1 = new Expected("Add static import for 'Math.max'", buf.toString());

			assertCodeActionExists(cu, e1);
		} finally {
			PreferenceManager.getPrefs(null).setJavaCompletionFavoriteMembers(Collections.emptyList());
		}
	}

	@Test
	public void testStaticImportFavorite2() throws Exception {
		List<String> favorites = new ArrayList<>();
		favorites.add("java.lang.Math.max");
		PreferenceManager.getPrefs(null).setJavaCompletionFavoriteMembers(favorites);
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
			StringBuilder buf = new StringBuilder();
			buf.append("package pack;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("    private int max() {\n");
			buf.append("        return max(1, 2);\n");
			buf.append("    }\n");
			buf.append("}\n");
			ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

			buf = new StringBuilder();
			buf.append("package pack;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("    private int max() {\n");
			buf.append("        return Math.max(1, 2);\n"); // naming conflict
			buf.append("    }\n");
			buf.append("}\n");
			Expected e1 = new Expected("Change to 'Math.max'", buf.toString());

			assertCodeActionExists(cu, e1);
		} finally {
			PreferenceManager.getPrefs(null).setJavaCompletionFavoriteMembers(Collections.emptyList());
		}
	}

	/**
	 * Visibility: fix for public or protected not appropriate.
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=65876#c5
	 *
	 * @throws Exception
	 *             if anything goes wrong
	 * @since 3.9
	 */
	@Test
	@Ignore("Requires ModifierCorrectionSubProcessor")
	public void testIndirectProtectedMethod() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		IPackageFragment pack2 = fSourceFolder.createPackageFragment("test2", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    protected void method() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("import test1.A;\n");
		buf.append("public class B extends A {\n");
		buf.append("    private void bMethod() {\n");
		buf.append("        A a = new A();\n");
		buf.append("        a.method();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack2.createCompilationUnit("B.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public void method() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add Javadoc comment", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testStaticMethodInInterface1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface Snippet {\n");
		buf.append("    public abstract String name();\n");
		buf.append("}\n");
		buf.append("class Ref {\n");
		buf.append("    void foo(Snippet c) {\n");
		buf.append("        int[] v= Snippet.values();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Snippet.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface Snippet {\n");
		buf.append("    public abstract String name();\n");
		buf.append("\n");
		buf.append("    public static int[] values() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class Ref {\n");
		buf.append("    void foo(Snippet c) {\n");
		buf.append("        int[] v= Snippet.values();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'values()' in type 'Snippet'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testStaticMethodInInterface2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface Snippet {\n");
		buf.append("    public abstract String name();\n");
		buf.append("}\n");
		buf.append("interface Ref {\n");
		buf.append("   int[] v= Snippet.values();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Snippet.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface Snippet {\n");
		buf.append("    public abstract String name();\n");
		buf.append("\n");
		buf.append("    public static int[] values() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("interface Ref {\n");
		buf.append("   int[] v= Snippet.values();\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'values()' in type 'Snippet'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testStaticMethodInInterface3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class XX {\n");
		buf.append("    interface I {\n");
		buf.append("        int i= n();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("XX.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class XX {\n");
		buf.append("    interface I {\n");
		buf.append("        int i= n();\n");
		buf.append("\n");
		buf.append("        static int n() {\n");
		buf.append("            return 0;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'n()'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class XX {\n");
		buf.append("    interface I {\n");
		buf.append("        int i= n();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    protected static int n() {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create method 'n()' in type 'XX'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testStaticMethodInInterface4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface I {\n");
		buf.append("    int i= n();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("I.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface I {\n");
		buf.append("    int i= n();\n");
		buf.append("\n");
		buf.append("    static int n() {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'n()'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testAbstractMethodInInterface() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface Snippet {\n");
		buf.append("    abstract String name();\n");
		buf.append("}\n");
		buf.append("class Ref {\n");
		buf.append("    void foo(Snippet c) {\n");
		buf.append("        int[] v= c.values();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Snippet.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface Snippet {\n");
		buf.append("    abstract String name();\n");
		buf.append("\n");
		buf.append("    abstract int[] values();\n");
		buf.append("}\n");
		buf.append("class Ref {\n");
		buf.append("    void foo(Snippet c) {\n");
		buf.append("        int[] v= c.values();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create method 'values()' in type 'Snippet'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface Snippet {\n");
		buf.append("    abstract String name();\n");
		buf.append("}\n");
		buf.append("class Ref {\n");
		buf.append("    void foo(Snippet c) {\n");
		buf.append("        int[] v= ((Object) c).values();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add cast to 'c'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

}