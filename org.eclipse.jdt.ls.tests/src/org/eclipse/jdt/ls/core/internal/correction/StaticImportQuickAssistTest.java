/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class StaticImportQuickAssistTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertToStaticFieldImport() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	public static final String FOO = \"BAR\";\n");
		buf.append("}\n");
		pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class B {\n");
		buf.append("	public String bar = A.FOO;\n");
		buf.append("	public String bar1 = A.FOO;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("B.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("import static test.A.FOO;\n");
		buf.append("\n");
		buf.append("public class B {\n");
		buf.append("	public String bar = A.FOO;\n");
		buf.append("	public String bar1 = FOO;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Convert to static import", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("import static test.A.FOO;\n");
		buf.append("\n");
		buf.append("public class B {\n");
		buf.append("	public String bar = FOO;\n");
		buf.append("	public String bar1 = FOO;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Convert to static import (replace all occurrences)", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "FOO");
		assertCodeActions(cu, selection, e1, e2);
	}

	@Test
	public void testConvertToStaticMethodImport() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	public static void foo() {\n");
		buf.append("	}\n");
		buf.append("}\n");
		pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class B {\n");
		buf.append("	public void bar() {\n");
		buf.append("		A.foo();\n");
		buf.append("	}\n");
		buf.append("	public void bar1() {\n");
		buf.append("		A.foo();\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("B.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("import static test.A.foo;\n");
		buf.append("\n");
		buf.append("public class B {\n");
		buf.append("	public void bar() {\n");
		buf.append("		A.foo();\n");
		buf.append("	}\n");
		buf.append("	public void bar1() {\n");
		buf.append("		foo();\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Convert to static import", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("import static test.A.foo;\n");
		buf.append("\n");
		buf.append("public class B {\n");
		buf.append("	public void bar() {\n");
		buf.append("		foo();\n");
		buf.append("	}\n");
		buf.append("	public void bar1() {\n");
		buf.append("		foo();\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e2 = new Expected("Convert to static import (replace all occurrences)", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "foo");
		assertCodeActions(cu, selection, e1, e2);
	}

	@Test
	// https://github.com/eclipse/eclipse.jdt.ls/issues/1203
	public void testConvertToStaticImportPreservesExisting() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class T {\n");
		buf.append("    public static void foo() { };\n");
		buf.append("}\n");
		pack1.createCompilationUnit("T.java", buf.toString(), false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("test2", false, null);
		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("import test1.T;\n");
		buf.append("\n");
		buf.append("public class S {\n");
		buf.append("    public S() {\n");
		buf.append("        T.foo();\n");
		buf.append("        System.out.println(T.class);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack2.createCompilationUnit("S.java", buf.toString(), false, null);

		Range selection = CodeActionUtil.getRange(cu, "foo");

		StringBuffer expectation = new StringBuffer();
		expectation.append("package test2;\n");
		expectation.append("\n");
		expectation.append("import static test1.T.foo;\n");
		expectation.append("\n");
		expectation.append("import test1.T;\n");
		expectation.append("\n");
		expectation.append("public class S {\n");
		expectation.append("    public S() {\n");
		expectation.append("        foo();\n");
		expectation.append("        System.out.println(T.class);\n");
		expectation.append("    }\n");
		expectation.append("}\n");

		Expected e1 = new Expected("Convert to static import", expectation.toString());
		Expected e2 = new Expected("Convert to static import (replace all occurrences)", expectation.toString());
		assertCodeActions(cu, selection, e1, e2);
	}
}
