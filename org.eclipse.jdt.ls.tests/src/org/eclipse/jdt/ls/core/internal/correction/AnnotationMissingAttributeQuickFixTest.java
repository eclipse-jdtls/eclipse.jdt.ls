/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
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
import org.eclipse.jdt.ls.core.internal.corrections.proposals.LocalCorrectionsSubProcessor;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for
 * {@link LocalCorrectionsSubProcessor#addValueForAnnotationProposals}.
 */
public class AnnotationMissingAttributeQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testOneAttribute() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public @interface C {\n");
		buf.append("  String value();\n");
		buf.append("}\n");
		pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("  @C\n");
		buf.append("  int myInt = 0;\n");
		buf.append("}\n");
		ICompilationUnit classCU = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		// FIXME: https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/334
		buf.append("  @C(value = \"\")\n");
		buf.append("  int myInt = 0;\n");
		buf.append("}\n");

		Expected expected = new Expected("Add missing attributes", buf.toString());

		assertCodeActions(classCU, expected);
	}

	@Test
	public void testOneRequiredAttribute() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public @interface C {\n");
		buf.append("  String value();\n");
		buf.append("  String otherValue() default \"prefix\";\n");
		buf.append("}\n");
		pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("  @C\n");
		buf.append("  int myInt = 0;\n");
		buf.append("}\n");
		ICompilationUnit classCU = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("  @C(value = \"\")\n");
		buf.append("  int myInt = 0;\n");
		buf.append("}\n");

		Expected expected = new Expected("Add missing attributes", buf.toString());

		assertCodeActions(classCU, expected);
	}

	@Test
	public void testMultipleRequiredAttributes() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public @interface C {\n");
		buf.append("  String value1();\n");
		buf.append("  String value2();\n");
		buf.append("  String otherValue() default \"prefix\";\n");
		buf.append("}\n");
		pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("  @C\n");
		buf.append("  int myInt = 0;\n");
		buf.append("}\n");
		ICompilationUnit classCU = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("  @C(value1 = \"\", value2 = \"\")\n");
		buf.append("  int myInt = 0;\n");
		buf.append("}\n");

		Expected expected = new Expected("Add missing attributes", buf.toString());

		assertCodeActions(classCU, expected);
	}

}
