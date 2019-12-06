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
package org.eclipse.jdt.ls.core.internal.refactoring;

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
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
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		options.put(JavaCore.COMPILER_PB_MISSING_OVERRIDE_ANNOTATION, JavaCore.WARNING);
		fJProject.setOptions(options);
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertToAnonymousClassCreation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder builder = new StringBuilder();
		builder.append("package test1;\n");
		builder.append("interface I {\n");
		builder.append("    void method();\n");
		builder.append("}\n");
		builder.append("public class E {\n");
		builder.append("    void bar(I i) {\n");
		builder.append("    }\n");
		builder.append("    void foo() {\n");
		builder.append("        bar(() /*[*//*]*/-> {\n");
		builder.append("            System.out.println();\n");
		builder.append("            System.out.println();\n");
		builder.append("        });\n");
		builder.append("    }\n");
		builder.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		builder = new StringBuilder();
		builder.append("package test1;\n");
		builder.append("interface I {\n");
		builder.append("    void method();\n");
		builder.append("}\n");
		builder.append("public class E {\n");
		builder.append("    void bar(I i) {\n");
		builder.append("    }\n");
		builder.append("    void foo() {\n");
		builder.append("        bar(new I() {\n");
		builder.append("            @Override\n");
		builder.append("            public void method() {\n");
		builder.append("                System.out.println();\n");
		builder.append("                System.out.println();\n");
		builder.append("            }\n");
		builder.append("        });\n");
		builder.append("    }\n");
		builder.append("}\n");
		Expected e = new Expected("Convert to anonymous class creation", builder.toString());

		assertCodeActions(cu, e);
	}
}
