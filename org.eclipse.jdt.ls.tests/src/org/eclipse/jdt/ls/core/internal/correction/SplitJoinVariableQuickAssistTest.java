/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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
import org.junit.Before;
import org.junit.Test;

public class SplitJoinVariableQuickAssistTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testSplitSimpleVariableAssignment() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.util.List;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class Variables {\n");
		buf.append("    public void test() {\n");
		buf.append("        List<String> strings = new ArrayList<>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Variables.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.util.List;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class Variables {\n");
		buf.append("    public void test() {\n");
		buf.append("        List<String> strings;\n");
		buf.append("        strings = new ArrayList<>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Split variable declaration", buf.toString());

		assertCodeActions(cu, CodeActionUtil.getRange(cu, "strings"), e1);
	}

	@Test
	public void testSplitForLoopVariableAssignment() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.util.List;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class Variables {\n");
		buf.append("    public void test(int j) {\n");
		buf.append("        for(int index = 0; j < 10; j++){\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Variables.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("import java.util.List;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class Variables {\n");
		buf.append("    public void test(int j) {\n");
		buf.append("        int index;\n");
		buf.append("        for(index = 0; j < 10; j++){\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Split variable declaration", buf.toString());

		assertCodeActions(cu, CodeActionUtil.getRange(cu, "index"), e1);
	}
}
