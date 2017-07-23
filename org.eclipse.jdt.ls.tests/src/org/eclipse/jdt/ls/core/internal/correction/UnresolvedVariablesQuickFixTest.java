/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/UnresolvedVariablesQuickFixTest.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Rabea Gransberger <rgransberger@gmx.de> - [quick fix] Fix several visibility issues - https://bugs.eclipse.org/394692
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class UnresolvedVariablesQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		options.put(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.IGNORE);
		options.put(JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION, JavaCore.IGNORE);

		fJProject1.setOptions(options);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testVarInAssignment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private Iterator iter;\n");
		buf.append("\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec, Iterator iter) {\n");
		buf.append("        iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create parameter 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        Iterator iter = vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create local variable 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Remove assignment", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarAssingmentInIfBody() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        if (vec != null)\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private Iterator iter;\n");
		buf.append("\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        if (vec != null)\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec, Iterator iter) {\n");
		buf.append("        if (vec != null)\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create parameter 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        Iterator iter;\n");
		buf.append("        if (vec != null)\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create local variable 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        if (vec != null) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Remove assignment", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarAssingmentInThenBody() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private Iterator iter;\n");
		buf.append("\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec, Iterator iter) {\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create parameter 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        Iterator iter;\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create local variable 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector vec) {\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Remove assignment", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarInAssignmentWithGenerics() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private Iterator<String> iter;\n");
		buf.append("\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec, Iterator<String> iter) {\n");
		buf.append("        iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create parameter 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        Iterator<String> iter = vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create local variable 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Remove assignment", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarAssignedByWildcard1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<?> vec) {\n");
		buf.append("        elem = vec.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<?> vec) {\n");
		buf.append("        Object elem = vec.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create local variable 'elem'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarAssignedByWildcard2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? super Number> vec) {\n");
		buf.append("        elem = vec.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? super Number> vec) {\n");
		buf.append("        Object elem = vec.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create local variable 'elem'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarAssignedByWildcard3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? extends Number> vec) {\n");
		buf.append("        elem = vec.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? extends Number> vec) {\n");
		buf.append("        Number elem = vec.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create local variable 'elem'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarAssignedToWildcard1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? super Number> vec) {\n");
		buf.append("        vec.add(elem);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? super Number> vec, Number elem) {\n");
		buf.append("        vec.add(elem);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create parameter 'elem'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarAssignedToWildcard2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? extends Number> vec) {\n");
		buf.append("        vec.add(elem);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<? extends Number> vec, Object elem) {\n");
		buf.append("        vec.add(elem);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create parameter 'elem'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarAssignedToWildcard3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<?> vec) {\n");
		buf.append("        vec.add(elem);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<?> vec, Object elem) {\n");
		buf.append("        vec.add(elem);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create parameter 'elem'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarAssingmentInIfBodyWithGenerics() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        if (vec != null)\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private Iterator<String> iter;\n");
		buf.append("\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        if (vec != null)\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec, Iterator<String> iter) {\n");
		buf.append("        if (vec != null)\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create parameter 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        Iterator<String> iter;\n");
		buf.append("        if (vec != null)\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create local variable 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        if (vec != null) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Remove assignment", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarAssingmentInThenBodyWithGenerics() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    private Iterator<String> iter;\n");
		buf.append("\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec, Iterator<String> iter) {\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create parameter 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        Iterator<String> iter;\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else\n");
		buf.append("            iter= vec.iterator();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create local variable 'iter'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Vector<String> vec) {\n");
		buf.append("        if (vec == null) {\n");
		buf.append("        } else {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Remove assignment", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarInVarArgs1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("import java.util.Arrays;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Arrays.<Number>asList(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("import java.util.Arrays;\n");
		buf.append("public class E {\n");
		buf.append("    private Number x;\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        Arrays.<Number>asList(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("import java.util.Arrays;\n");
		buf.append("public class E {\n");
		buf.append("    private static final Number x = null;\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("        Arrays.<Number>asList(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constant 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("import java.util.Arrays;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Number x) {\n");
		buf.append("        Arrays.<Number>asList(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create parameter 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("import java.util.Arrays;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Number x;\n");
		buf.append("        Arrays.<Number>asList(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create local variable 'x'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarInVarArgs2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("import java.io.File;\n");
		buf.append("import java.util.Arrays;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String name) {\n");
		buf.append("        Arrays.<File>asList( new File(name), new XXX(name) );\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("import java.io.File;\n");
		buf.append("import java.util.Arrays;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String name) {\n");
		buf.append("        Arrays.<File>asList( new File(name), new File(name) );\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'File' (java.io)", buf.toString());

		// buf = new StringBuilder();
		// buf.append("package pack;\n");
		// buf.append("\n");
		// buf.append("import java.io.File;\n");
		// buf.append("\n");
		// buf.append("public class XXX extends File {\n");
		// buf.append("\n");
		// buf.append("}\n");
		// Expected e2 = new Expected("Add Javadoc comment", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testVarInForInitializer() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (i= 0;;) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int i;\n");
		buf.append("\n");
		buf.append("    void foo() {\n");
		buf.append("        for (i= 0;;) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'i'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(int i) {\n");
		buf.append("        for (i= 0;;) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create parameter 'i'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (int i = 0;;) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create local variable 'i'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testVarInForInitializer2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @return Returns a number\n");
		buf.append("     */\n");
		buf.append("    int foo() {\n");
		buf.append("        for (i= new int[] { 1 };;) {\n");
		buf.append("        }\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int[] i;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return Returns a number\n");
		buf.append("     */\n");
		buf.append("    int foo() {\n");
		buf.append("        for (i= new int[] { 1 };;) {\n");
		buf.append("        }\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'i'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param i \n");
		buf.append("     * @return Returns a number\n");
		buf.append("     */\n");
		buf.append("    int foo(int[] i) {\n");
		buf.append("        for (i= new int[] { 1 };;) {\n");
		buf.append("        }\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create parameter 'i'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @return Returns a number\n");
		buf.append("     */\n");
		buf.append("    int foo() {\n");
		buf.append("        for (int[] i = new int[] { 1 };;) {\n");
		buf.append("        }\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create local variable 'i'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testVarInInitializer() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int i= k;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();

		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int k;\n");
		buf.append("    private int i= k;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'k'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static final int k = 0;\n");
		buf.append("    private int i= k;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constant 'k'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testVarInOtherType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var2= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    protected int var1;\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var1= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'var1'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    protected int var1;\n");
		buf.append("    public int var2;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create field 'var2' in type 'E'", buf.toString());


		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testVarInSuperFieldAccess() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F extends E {\n");
		buf.append("    void foo() {\n");
		buf.append("         super.var2= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    protected int var1;\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F extends E {\n");
		buf.append("    void foo() {\n");
		buf.append("         super.var1= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'var1'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    protected int var1;\n");
		buf.append("    public int var2;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create field 'var2' in type 'E'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testVarInSuper() throws Exception {
		StringBuilder buf = new StringBuilder();

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test3.E;\n");
		buf.append("public class F extends E {\n");
		buf.append("    void foo() {\n");
		buf.append("         this.color= baz();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("test2", false, null);
		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		pack2.createCompilationUnit("E.java", buf.toString(), false, null);

		IPackageFragment pack3 = fSourceFolder.createPackageFragment("test3", false, null);
		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    protected Object olor;\n");
		buf.append("    public test2.E baz() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack3.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test3.E;\n");
		buf.append("public class F extends E {\n");
		buf.append("    void foo() {\n");
		buf.append("         this.olor= baz();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'olor'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test3.E;\n");
		buf.append("public class F extends E {\n");
		buf.append("    private test2.E color;\n");
		buf.append("\n");
		buf.append("    void foo() {\n");
		buf.append("         this.color= baz();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create field 'color' in type 'F'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testVarInAnonymous() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                fCount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                fcount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'fcount'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            private int fCount;\n");
		buf.append("\n");
		buf.append("            public void run() {\n");
		buf.append("                fCount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create field 'fCount'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    protected int fCount;\n");
		buf.append("\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                fCount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create field 'fCount' in type 'E'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run(int fCount) {\n");
		buf.append("                fCount= 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create parameter 'fCount'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                int fCount = 7;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e5 = new Expected("Create local variable 'fCount'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int fcount) {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e6 = new Expected("Remove assignment", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4, e5, e6);
	}

	@Test
	public void testVarInAnnotation1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        String value();\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    @Annot(x)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        String value();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private static final String x = null;\n");
		buf.append("    \n");
		buf.append("    @Annot(x)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create constant 'x'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testVarInAnnotation2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        float value();\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    @Annot(value=x)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        float value();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private static final float x = 0;\n");
		buf.append("    \n");
		buf.append("    @Annot(value=x)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create constant 'x'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarInAnnotation3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        float[] value();\n");
		buf.append("    }\n");
		buf.append("    \n");
		buf.append("    @Annot(value={x})\n");
		buf.append("    class Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        float[] value();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    private static final float x = 0;\n");
		buf.append("    \n");
		buf.append("    @Annot(value={x})\n");
		buf.append("    class Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create constant 'x'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testStaticImportFavorite1() throws Exception {
		PreferenceManager.getPrefs(null).setFavoriteStaticMembers("java.lang.Math.*");
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
			StringBuilder buf = new StringBuilder();
			buf.append("package pack;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("    private float foo() {\n");
			buf.append("        return PI;\n");
			buf.append("    }\n");
			buf.append("}\n");
			ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

			buf = new StringBuilder();
			buf.append("package pack;\n");
			buf.append("\n");
			buf.append("import static java.lang.Math.PI;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("    private float foo() {\n");
			buf.append("        return PI;\n");
			buf.append("    }\n");
			buf.append("}\n");
			Expected e1 = new Expected("Add static import for 'Math.PI'", buf.toString());

			assertCodeActionExists(cu, e1);
		} finally {
			PreferenceManager.getPrefs(null).setFavoriteStaticMembers("");
		}
	}

	@Test
	public void testLongVarRef() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    public int mash;\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var.hash= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public F var;\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    public int mash;\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var.mash= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'mash'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    public int mash;\n");
		buf.append("    private int hash;\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var.hash= 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create field 'hash' in type 'F'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testVarAndTypeRef() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.File;\n");
		buf.append("public class F {\n");
		buf.append("    void foo() {\n");
		buf.append("        char ch= Fixe.pathSeparatorChar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.File;\n");
		buf.append("public class F {\n");
		buf.append("    void foo() {\n");
		buf.append("        char ch= File.pathSeparatorChar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'File' (java.io)", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.File;\n");
		buf.append("public class F {\n");
		buf.append("    private Object Fixe;\n");
		buf.append("\n");
		buf.append("    void foo() {\n");
		buf.append("        char ch= Fixe.pathSeparatorChar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create field 'Fixe'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.File;\n");
		buf.append("public class F {\n");
		buf.append("    private static final String Fixe = null;\n");
		buf.append("\n");
		buf.append("    void foo() {\n");
		buf.append("        char ch= Fixe.pathSeparatorChar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create constant 'Fixe'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.File;\n");
		buf.append("public class F {\n");
		buf.append("    void foo(Object Fixe) {\n");
		buf.append("        char ch= Fixe.pathSeparatorChar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create parameter 'Fixe'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.File;\n");
		buf.append("public class F {\n");
		buf.append("    void foo() {\n");
		buf.append("        Object Fixe;\n");
		buf.append("        char ch= Fixe.pathSeparatorChar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e5 = new Expected("Create local variable 'Fixe'", buf.toString());

		// buf = new StringBuilder();
		// buf.append("package test1;\n");
		// buf.append("\n");
		// buf.append("public class Fixe {\n");
		// buf.append("\n");
		// buf.append("}\n");
		// Expected e5 = new Expected("Add all missing tags", buf.toString());
		//
		// buf = new StringBuilder();
		// buf.append("package test1;\n");
		// buf.append("\n");
		// buf.append("public interface Fixe {\n");
		// buf.append("\n");
		// buf.append("}\n");
		// Expected e6 = new Expected("Add all missing tags", buf.toString());
		//
		// buf = new StringBuilder();
		// buf.append("package test1;\n");
		// buf.append("\n");
		// buf.append("public enum Fixe {\n");
		// buf.append("\n");
		// buf.append("}\n");
		// Expected e7 = new Expected("Add all missing tags", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4, e5);
	}

	@Test
	public void testVarWithGenericType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class F {\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var2= new ArrayList<String>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    protected int var1;\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class F {\n");
		buf.append("    void foo(E e) {\n");
		buf.append("         e.var1= new ArrayList<String>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'var1'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    protected int var1;\n");
		buf.append("    public ArrayList<String> var2;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create field 'var2' in type 'E'", buf.toString());


		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testSimilarVariableNames1() throws Exception {
		StringBuilder buf = new StringBuilder();

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test3", false, null);
		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public int foo() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public int foo() {\n");
		buf.append("        return CON1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'CON1'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public int foo() {\n");
		buf.append("        return cout;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change to 'cout'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    private int count;\n");
		buf.append("    public int foo() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create field 'count'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private static final int count = 0;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public int foo() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create constant 'count'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public int foo(int count) {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e5 = new Expected("Create parameter 'count'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public int foo() {\n");
		buf.append("        int count;\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e6 = new Expected("Create local variable 'count'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4, e5, e6);
	}

	@Test
	public void testSimilarVariableNames2() throws Exception {
		StringBuilder buf = new StringBuilder();

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test3", false, null);
		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private static short var1= 1;\n");
		buf.append("    private static float var2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public void foo(int x) {\n");
		buf.append("        count= x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private static short var1= 1;\n");
		buf.append("    private static float var2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public void foo(int x) {\n");
		buf.append("        cout= x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'cout'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private static short var1= 1;\n");
		buf.append("    private static float var2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public void foo(int x) {\n");
		buf.append("        var2= x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change to 'var2'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private static short var1= 1;\n");
		buf.append("    private static float var2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    private int count;\n");
		buf.append("    public void foo(int x) {\n");
		buf.append("        count= x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create field 'count'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private static short var1= 1;\n");
		buf.append("    private static float var2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public void foo(int x, int count) {\n");
		buf.append("        count= x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create parameter 'count'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private static short var1= 1;\n");
		buf.append("    private static float var2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public void foo(int x) {\n");
		buf.append("        int count = x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e5 = new Expected("Create local variable 'count'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private static final short CON1= 1;\n");
		buf.append("    private static final float CON2= 1.0f;\n");
		buf.append("    private static short var1= 1;\n");
		buf.append("    private static float var2= 1.0f;\n");
		buf.append("    private String bla;\n");
		buf.append("    private String cout;\n");
		buf.append("    public void foo(int x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e6 = new Expected("Remove assignment", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4, e5, e6);
	}

	@Test
	public void testSimilarVariableNamesMultipleOcc() throws Exception {
		StringBuilder buf = new StringBuilder();

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test3", false, null);
		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private int cout;\n");
		buf.append("    public void setCount(int x) {\n");
		buf.append("        count= x;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("    public int getCount() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private int cout;\n");
		buf.append("    public void setCount(int x) {\n");
		buf.append("        cout= x;\n");
		buf.append("        cout++;\n");
		buf.append("    }\n");
		buf.append("    public int getCount() {\n");
		buf.append("        return cout;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'cout'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private int cout;\n");
		buf.append("    private int count;\n");
		buf.append("    public void setCount(int x) {\n");
		buf.append("        count= x;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("    public int getCount() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create field 'count'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private int cout;\n");
		buf.append("    public void setCount(int x, int count) {\n");
		buf.append("        count= x;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("    public int getCount() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create parameter 'count'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private int cout;\n");
		buf.append("    public void setCount(int x) {\n");
		buf.append("        int count = x;\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("    public int getCount() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create local variable 'count'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test3;\n");
		buf.append("public class E {\n");
		buf.append("    private int cout;\n");
		buf.append("    public void setCount(int x) {\n");
		buf.append("        count++;\n");
		buf.append("    }\n");
		buf.append("    public int getCount() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e5 = new Expected("Remove assignment", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4, e5);
	}

	@Test
	public void testVarMultipleOccurances1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (i= 0; i > 9; i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (int i = 0; i > 9; i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create local variable 'i'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarMultipleOccurances2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (i= 0; i > 9;) {\n");
		buf.append("            i++;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (int i = 0; i > 9;) {\n");
		buf.append("            i++;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create local variable 'i'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarMultipleOccurances3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        for (i = 0; i > 9;) {\n");
		buf.append("        }\n");
		buf.append("        i= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        for (i = 0; i > 9;) {\n");
		buf.append("        }\n");
		buf.append("        i= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create local variable 'i'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarInArray() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Object[] arr) {\n");
		buf.append("        for (int i = 0; i > arr.lenght; i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Object[] arr) {\n");
		buf.append("        for (int i = 0; i > arr.length; i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'length'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testVarInEnumSwitch() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public enum Colors {\n");
		buf.append("    RED\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Colors.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Colors c) {\n");
		buf.append("        switch (c) {\n");
		buf.append("            case BLUE:\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void foo(Colors c) {\n");
		buf.append("        switch (c) {\n");
		buf.append("            case RED:\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'RED'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public enum Colors {\n");
		buf.append("    RED, BLUE\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create enum constant 'BLUE' in 'Colors'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testVarInMethodInvocation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void goo(String s) {\n");
		buf.append("    }\n");
		buf.append("    void foo() {\n");
		buf.append("        goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private String x;\n");
		buf.append("    void goo(String s) {\n");
		buf.append("    }\n");
		buf.append("    void foo() {\n");
		buf.append("        goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static final String x = null;\n");
		buf.append("    void goo(String s) {\n");
		buf.append("    }\n");
		buf.append("    void foo() {\n");
		buf.append("        goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constant 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void goo(String s) {\n");
		buf.append("    }\n");
		buf.append("    void foo(String x) {\n");
		buf.append("        goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create parameter 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    void goo(String s) {\n");
		buf.append("    }\n");
		buf.append("    void foo() {\n");
		buf.append("        String x;\n");
		buf.append("        goo(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create local variable 'x'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarInConstructurInvocation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E(String s) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static String x;\n");
		buf.append("    public E(String s) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static final String x = null;\n");
		buf.append("    public E(String s) {\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constant 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E(String s) {\n");
		buf.append("    }\n");
		buf.append("    public E(String x) {\n");
		buf.append("        this(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create parameter 'x'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testVarInSuperConstructurInvocation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    public F(String s) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("    public E() {\n");
		buf.append("        super(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("    private static String x;\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        super(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("    private static final String x = null;\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        super(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constant 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("    public E(String x) {\n");
		buf.append("        super(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create parameter 'x'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testVarInClassInstanceCreation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    public F(String s) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("        new F(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private String x;\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        new F(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static final String x = null;\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        new F(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constant 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E(String x) {\n");
		buf.append("        new F(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create parameter 'x'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("        String x;\n");
		buf.append("        new F(x);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create local variable 'x'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarInArrayAccess() throws Exception {
		// bug 194913
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("p", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    void foo(int i) {\n");
		buf.append("        bar[0][i] = \"bar\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    private String[][] bar;\n");
		buf.append("\n");
		buf.append("    void foo(int i) {\n");
		buf.append("        bar[0][i] = \"bar\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'bar'", buf.toString());

		buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    private static final String[][] bar = null;\n");
		buf.append("\n");
		buf.append("    void foo(int i) {\n");
		buf.append("        bar[0][i] = \"bar\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constant 'bar'", buf.toString());

		buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    void foo(int i, String[][] bar) {\n");
		buf.append("        bar[0][i] = \"bar\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create parameter 'bar'", buf.toString());

		buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    void foo(int i) {\n");
		buf.append("        String[][] bar;\n");
		buf.append("        bar[0][i] = \"bar\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create local variable 'bar'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testVarWithMethodName1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int foo(String str) {\n");
		buf.append("        for (int i = 0; i > str.length; i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int foo(String str) {\n");
		buf.append("        for (int i = 0; i > str.length(); i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'length()'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testVarWithMethodName2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int foo(String str) {\n");
		buf.append("        return length;\n");
		buf.append("    }\n");
		buf.append("    int getLength() {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int foo(String str) {\n");
		buf.append("        return getLength();\n");
		buf.append("    }\n");
		buf.append("    int getLength() {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'getLength()'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testSimilarVarsAndVisibility() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    int var1;\n");
		buf.append("    static int var2;\n");
		buf.append("    public static void main(String[] var3) {\n");
		buf.append("        println(var);\n");
		buf.append("    }\n");
		buf.append("    public static void println(String[] s) {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    int var1;\n");
		buf.append("    static int var2;\n");
		buf.append("    public static void main(String[] var3) {\n");
		buf.append("        println(var3);\n");
		buf.append("    }\n");
		buf.append("    public static void println(String[] s) {}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change to 'var3'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    int var1;\n");
		buf.append("    static int var2;\n");
		buf.append("    public static void main(String[] var3) {\n");
		buf.append("        println(var2);\n");
		buf.append("    }\n");
		buf.append("    public static void println(String[] s) {}\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change to 'var2'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    int var1;\n");
		buf.append("    static int var2;\n");
		buf.append("    private static String[] var;\n");
		buf.append("    public static void main(String[] var3) {\n");
		buf.append("        println(var);\n");
		buf.append("    }\n");
		buf.append("    public static void println(String[] s) {}\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create field 'var'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    private static final String[] var = null;\n");
		buf.append("    int var1;\n");
		buf.append("    static int var2;\n");
		buf.append("    public static void main(String[] var3) {\n");
		buf.append("        println(var);\n");
		buf.append("    }\n");
		buf.append("    public static void println(String[] s) {}\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create constant 'var'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    int var1;\n");
		buf.append("    static int var2;\n");
		buf.append("    public static void main(String[] var3, String[] var) {\n");
		buf.append("        println(var);\n");
		buf.append("    }\n");
		buf.append("    public static void println(String[] s) {}\n");
		buf.append("}\n");
		Expected e5 = new Expected("Create parameter 'var'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    int var1;\n");
		buf.append("    static int var2;\n");
		buf.append("    public static void main(String[] var3) {\n");
		buf.append("        String[] var;\n");
		buf.append("        println(var);\n");
		buf.append("    }\n");
		buf.append("    public static void println(String[] s) {}\n");
		buf.append("}\n");
		Expected e6 = new Expected("Create local variable 'var'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4, e5, e6);
	}

	@Test
	public void testVarOfShadowedType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    class Runnable { }\n");
		buf.append("    public void test() {\n");
		buf.append("        new Thread(myRunnable);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    class Runnable { }\n");
		buf.append("    private java.lang.Runnable myRunnable;\n");
		buf.append("    public void test() {\n");
		buf.append("        new Thread(myRunnable);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'myRunnable'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    class Runnable { }\n");
		buf.append("    private static final java.lang.Runnable myRunnable = null;\n");
		buf.append("    public void test() {\n");
		buf.append("        new Thread(myRunnable);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Create constant 'myRunnable'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    class Runnable { }\n");
		buf.append("    public void test(java.lang.Runnable myRunnable) {\n");
		buf.append("        new Thread(myRunnable);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Create parameter 'myRunnable'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    class Runnable { }\n");
		buf.append("    public void test() {\n");
		buf.append("        java.lang.Runnable myRunnable;\n");
		buf.append("        new Thread(myRunnable);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Create local variable 'myRunnable'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	@Ignore("Modifier proposals")
	public void testVarParameterAccess() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		IPackageFragment pack2 = fSourceFolder.createPackageFragment("test2", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Base {\n");
		buf.append("    protected int myField;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Base.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("import test1.Base;\n");
		buf.append("public class Child extends Base {\n");
		buf.append("    public void aMethod(Base parent) {\n");
		buf.append("        System.out.println(parent.myField);\n");
		buf.append("    }\n");
		buf.append("}\n");
		cu = pack2.createCompilationUnit("Child.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Base {\n");
		buf.append("    public int myField;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());

		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testBug300() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Message {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Message.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class DevoxxApplication {\n");
		buf.append("    public static void main(String[] args) {\n");
		buf.append("        new Message().z\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("DevoxxApplication.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Message {\n");
		buf.append("    public Object z;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create field 'z' in type 'Message'", buf.toString());

		assertCodeActionExists(cu, e1);
	}

}