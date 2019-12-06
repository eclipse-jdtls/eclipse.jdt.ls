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

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Before;
import org.junit.Test;

public class ConstructorQuickFixTest extends AbstractQuickFixTest {
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		options.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_DEAD_CODE, JavaCore.WARNING);

		fJProject1.setOptions(options);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		this.setIgnoredCommands("Extract.*");
	}

	@Test
	public void testUndefinedConstructorFromSuperClass() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    public F(Runnable runnable) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("\n");
		buf.append("    public E(Runnable runnable) {\n");
		buf.append("        super(runnable);\n");
		buf.append("        //TODO Auto-generated constructor stub\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add constructor 'E(Runnable)'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMultipleUndefinedConstructorFromSuperClass() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class F {\n");
		buf.append("    public F(Runnable runnable) throws IOException {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public F(int i, Runnable runnable) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");
		buf.append("public class E extends F {\n");
		buf.append("\n");
		buf.append("    public E(Runnable runnable) throws IOException {\n");
		buf.append("        super(runnable);\n");
		buf.append("        //TODO Auto-generated constructor stub\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add constructor 'E(Runnable)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("\n");
		buf.append("    public E(int i, Runnable runnable) {\n");
		buf.append("        super(i, runnable);\n");
		buf.append("        //TODO Auto-generated constructor stub\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add constructor 'E(int,Runnable)'", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testNotVisibleConstructorFromSuperClass() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F {\n");
		buf.append("    private F() {\n");
		buf.append("    }\n");
		buf.append("    public F(Runnable runnable) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("\n");
		buf.append("    public E(Runnable runnable) {\n");
		buf.append("        super(runnable);\n");
		buf.append("        //TODO Auto-generated constructor stub\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add constructor 'E(Runnable)'", buf.toString());
		assertCodeActions(cu, e1);
	}
}
