/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.refactoring;

import java.util.Arrays;
import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.junit.Before;
import org.junit.Test;

public class ExtractVariableTest extends AbstractSelectionTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		this.setIgnoredCommands(Arrays.asList("Extract to method"));
	}

	@Test
	public void testExtractVariable() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	void m(int i){\n");
		buf.append("		int x= /*]*/0/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	void m(int i){\n");
		buf.append("		int j = 0;\n");
		buf.append("        int x= /*]*/j/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to local variable (replace all occurrences)", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	void m(int i){\n");
		buf.append("		int j = 0;\n");
		buf.append("        int x= /*]*/j/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e2 = new Expected("Extract to local variable", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	/**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    \n");
		buf.append("    private static final int _0 = /*]*/0/*[*/;\n");
		buf.append("\n");
		buf.append("    void m(int i){\n");
		buf.append("		int x= _0;\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e3 = new Expected("Extract to constant", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testExtractVariable1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("	public void foo() {\n");
		buf.append("		ArrayList<? extends Number> nl= new ArrayList<Integer>();\n");
		buf.append("		Number n= nl.get(/*]*/0/*[*/);\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("	public void foo() {\n");
		buf.append("		ArrayList<? extends Number> nl= new ArrayList<Integer>();\n");
		buf.append("		int i = 0;\n");
		buf.append("        Number n= nl.get(/*]*/i/*[*/);\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to local variable (replace all occurrences)", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("	public void foo() {\n");
		buf.append("		ArrayList<? extends Number> nl= new ArrayList<Integer>();\n");
		buf.append("		int i = 0;\n");
		buf.append("        Number n= nl.get(/*]*/i/*[*/);\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e2 = new Expected("Extract to local variable", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("	/**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    \n");
		buf.append("    private static final int _0 = /*]*/0/*[*/;\n");
		buf.append("\n");
		buf.append("    public void foo() {\n");
		buf.append("		ArrayList<? extends Number> nl= new ArrayList<Integer>();\n");
		buf.append("		Number n= nl.get(_0);\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e3 = new Expected("Extract to constant", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testExtractVariable2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	void f(){\n");
		buf.append("		try{\n");
		buf.append("			int j=0 +0;\n");
		buf.append("		} finally {\n");
		buf.append("			int j=/*]*/0/*[*/ +0;\n");
		buf.append("		}\n");
		buf.append("	}	\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	void f(){\n");
		buf.append("		int i = 0;\n");
		buf.append("        try{\n");
		buf.append("			int j=i +i;\n");
		buf.append("		} finally {\n");
		buf.append("			int j=/*]*/i/*[*/ +i;\n");
		buf.append("		}\n");
		buf.append("	}	\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to local variable (replace all occurrences)", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	void f(){\n");
		buf.append("		try{\n");
		buf.append("			int j=0 +0;\n");
		buf.append("		} finally {\n");
		buf.append("			int i = 0;\n");
		buf.append("            int j=/*]*/i/*[*/ +0;\n");
		buf.append("		}\n");
		buf.append("	}	\n");
		buf.append("}\n");
		Expected e2 = new Expected("Extract to local variable", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	/**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    \n");
		buf.append("    private static final int _0 = 0/*[*/;\n");
		buf.append("\n");
		buf.append("    void f(){\n");
		buf.append("		try{\n");
		buf.append("			int j=0 +0;\n");
		buf.append("		} finally {\n");
		buf.append("			int j=/*]*/_0 +0;\n");
		buf.append("		}\n");
		buf.append("	}	\n");
		buf.append("}\n");
		Expected e3 = new Expected("Extract to constant", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testExtractVariableFailed() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class A{\n");
		buf.append("	int m(int y){\n");
		buf.append("		int y= m(/*]*/y/*[*/);\n");
		buf.append("	};\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		assertCodeActions(cu);
	}


}
