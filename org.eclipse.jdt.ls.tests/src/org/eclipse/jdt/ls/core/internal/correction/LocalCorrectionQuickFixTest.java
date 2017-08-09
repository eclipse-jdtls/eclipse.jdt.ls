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

package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.junit.Before;
import org.junit.Test;

public class LocalCorrectionQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		fJProject1.setOptions(options);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testUnimplementedMethods() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface E {\n");
		buf.append("    void foo();\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F implements E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F implements E {\n");
		buf.append("\n");
		buf.append("    @Override\n");
		buf.append("    public void foo() {\n");
		buf.append("        \n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add unimplemented methods", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testUnimplementedMethodsForEnum() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface E {\n");
		buf.append("    void foo();\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public enum F implements E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public enum F implements E {\n");
		buf.append("    ;\n");
		buf.append("\n");
		buf.append("    @Override\n");
		buf.append("    public void foo() {\n");
		buf.append("        \n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add unimplemented methods", buf.toString());
		assertCodeActions(cu, e1);
	}
}
