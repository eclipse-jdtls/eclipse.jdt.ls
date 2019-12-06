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
package org.eclipse.jdt.ls.core.internal.correction;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

/**
 * @author nikolas
 *
 */
public class StaticReferenceQuickFixTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testStaticMethodRequested() throws Exception {
		// Problem we are testing
		int problem = IProblem.StaticMethodRequested;

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		b();\n"); // referencing in static context
		buf.append("	}\n");
		buf.append("	");
		buf.append("	public void b() {\n"); // non static
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		b();\n");
		buf.append("	}\n");
		buf.append("	");
		buf.append("	public static void b() {\n");
		buf.append("	}\n");
		buf.append("}\n");

		Expected e1 = new Expected("Change 'b()' to 'static'", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "FOOBAR");
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testNonStaticFieldFromStaticInvocation() throws Exception {
		// Problem we are testing
		int problem = IProblem.NonStaticFieldFromStaticInvocation;

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	int x = 0;"); // is not static
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		System.out.println(x);\n"); // referencing in static context
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	static int x = 0;"); // added static
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		System.out.println(x);\n");
		buf.append("	}\n");
		buf.append("}\n");

		Expected e1 = new Expected("Change 'x' to 'static'", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "FOOBAR");
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testInstanceMethodDuringConstructorInvocation() throws Exception {
		// Problem we are testing
		int problem = IProblem.NonStaticFieldFromStaticInvocation;

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	A (long x) {\n");
		buf.append("		this(test());\n"); // referencing in static context
		buf.append("	}\n");
		buf.append("	\n");
		buf.append("	int test() {\n");
		buf.append("		return 0;\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	A (long x) {\n");
		buf.append("		this(test());\n"); // referencing in static context
		buf.append("	}\n");
		buf.append("	\n");
		buf.append("	static int test() {\n");
		buf.append("		return 0;\n");
		buf.append("	}\n");
		buf.append("}\n");

		Expected e1 = new Expected("Change 'test()' to 'static'", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "FOOBAR");
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testInstanceFieldDuringConstructorInvocation() throws Exception {
		// Problem we are testing
		int problem = IProblem.NonStaticFieldFromStaticInvocation;

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	int i;");
		buf.append("	A () {\n");
		buf.append("		this(i);\n"); // referencing in static context
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	static int i;");
		buf.append("	A () {\n");
		buf.append("		this(i);\n"); // referencing in static context
		buf.append("	}\n");
		buf.append("}\n");

		Expected e1 = new Expected("Change 'i' to 'static'", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "FOOBAR");
		assertCodeActions(cu, selection, e1);
	}
}
