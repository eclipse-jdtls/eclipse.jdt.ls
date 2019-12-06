/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Before;
import org.junit.Test;

/**
 * ReturnTypeQuickFixTest
 */
public class ReturnTypeQuickFixTest extends AbstractQuickFixTest{
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
	public void testVoidMethodReturnsValue() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        return new Object();\n");
		buf.append("    }\n");
		buf.append("}\n");
    ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

    buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Object foo(Object o) {\n");
		buf.append("        return new Object();\n");
		buf.append("    }\n");
    buf.append("}\n");
    Expected e1 = new Expected("Change method return type to 'Object'", buf.toString());

    buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
    buf.append("}\n");
    Expected e2 = new Expected("Change to 'return;'", buf.toString());

    assertCodeActions(cu, e1,e2);
  }

  @Test
	public void testMethodReturnsVoid() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Object foo(Object o) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("}\n");
    ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

    buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Object foo(Object o) {\n");
		buf.append("        return o;\n");
		buf.append("    }\n");
    buf.append("}\n");
    Expected e1 = new Expected("Change return statement", buf.toString());

    buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
    buf.append("}\n");
    Expected e2 = new Expected("Change return type to 'void'", buf.toString());

    assertCodeActions(cu, e1,e2);
  }

  @Test
	public void testMissingReturnType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public foo(Object o) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("}\n");
    ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

    buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
    buf.append("}\n");
    Expected e1 = new Expected("Set method return type to 'void'", buf.toString());

    buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E(Object o) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
    buf.append("}\n");
    Expected e2 = new Expected("Change to constructor", buf.toString());

    assertCodeActions(cu, e1,e2);
  }

  @Test
	public void testShouldReturn() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Object foo(Object o) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("}\n");
    ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

    buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Object foo(Object o) {\n");
		buf.append("        return o;\n");
		buf.append("    }\n");
    buf.append("}\n");
    Expected e1 = new Expected("Change return statement", buf.toString());

    buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
    buf.append("}\n");
    Expected e2 = new Expected("Change return type to 'void'", buf.toString());

    assertCodeActions(cu, e1,e2);
  }
}
