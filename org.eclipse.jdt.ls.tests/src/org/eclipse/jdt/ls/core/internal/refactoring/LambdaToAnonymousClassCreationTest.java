/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.refactoring;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.junit.Before;
import org.junit.Test;

public class LambdaToAnonymousClassCreationTest extends AbstractSelectionTest {

	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertToAnonymousClassCreation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf = new StringBuffer();
		buf.append("package test1;\n");
		buf.append("interface I {\n");
		buf.append("    void method();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    void bar(I i) {\n");
		buf.append("    }\n");
		buf.append("    void foo() {\n");
		buf.append("        bar(() /*[*//*]*/-> {\n");
		buf.append("            System.out.println();\n");
		buf.append("            System.out.println();\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuffer();
		buf.append("package test1;\n");
		buf.append("interface I {\n");
		buf.append("    void method();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    void bar(I i) {\n");
		buf.append("    }\n");
		buf.append("    void foo() {\n");
		buf.append("        bar(new I() {\n");
		buf.append("            @Override\n");
		buf.append("            public void method() {\n");
		buf.append("                System.out.println();\n");
		buf.append("                System.out.println();\n");
		buf.append("            }\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e = new Expected("Convert to anonymous class creation", buf.toString());

		assertCodeActions(cu, e);
	}
}
