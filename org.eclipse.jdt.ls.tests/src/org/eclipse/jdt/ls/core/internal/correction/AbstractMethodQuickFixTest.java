/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
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
import org.junit.Before;
import org.junit.Test;

/**
 * @author qisun
 *
 */
public class AbstractMethodQuickFixTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testAbstractMethodInConcreteClass() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class ConcreteClass {\n");
		buf.append("    public abstract void AbstractMethodInConcreteClass() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("ConcreteClass.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class ConcreteClass {\n");
		buf.append("    public void AbstractMethodInConcreteClass() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'abstract' modifier", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public abstract class ConcreteClass {\n");
		buf.append("    public abstract void AbstractMethodInConcreteClass() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Make type 'ConcreteClass' abstract", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class ConcreteClass {\n");
		buf.append("    public abstract void AbstractMethodInConcreteClass();\n");
		buf.append("}\n");
		Expected e3 = new Expected("Remove method body", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testAbstractMethodWithBody() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public abstract class TestClass {\n");
		buf.append("    public abstract void TestMethod() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("TestClass.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public abstract class TestClass {\n");
		buf.append("    public void TestMethod() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'abstract' modifier", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public abstract class TestClass {\n");
		buf.append("    public abstract void TestMethod();\n");
		buf.append("}\n");
		Expected e2 = new Expected("Remove method body", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testAbstractMethodWithBody2() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("abstract class TestClass {\n");
		buf.append("    public abstract void TestMethod() {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("TestClass.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("abstract class TestClass {\n");
		buf.append("    public void TestMethod() {}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'abstract' modifier", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("abstract class TestClass {\n");
		buf.append("    public abstract void TestMethod();\n");
		buf.append("}\n");
		Expected e2 = new Expected("Remove method body", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testAbstractMethodWithBody3() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("enum TestEnum {\n");
		buf.append("    A {\n");
		buf.append("        public void TestMethod() {}\n");
		buf.append("    };\n");
		buf.append("    public abstract void TestMethod() {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("TestEnum.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("enum TestEnum {\n");
		buf.append("    A {\n");
		buf.append("        public void TestMethod() {}\n");
		buf.append("    };\n");
		buf.append("    public abstract void TestMethod();\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove method body", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testAbstractMethodInEnum() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public enum TestEnum {\n");
		buf.append("    public abstract void TestMethod() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("TestEnum.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public enum TestEnum {\n");
		buf.append("    public void TestMethod() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'abstract' modifier", buf.toString());

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public enum TestEnum {\n");
		buf.append("    public abstract void TestMethod();\n");
		buf.append("}\n");
		Expected e2 = new Expected("Remove method body", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testAbstractMethodInEnum2() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public enum TestEnum {\n");
		buf.append("    public abstract void TestMethod();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("TestEnum.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public enum TestEnum {\n");
		buf.append("    public void TestMethod() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'abstract' modifier", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testAbstractMethodInEnumWithoutEnumConstants() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("enum TestEnum {\n");
		buf.append("    public abstract boolean TestMethod();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("TestEnum.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("enum TestEnum {\n");
		buf.append("    public boolean TestMethod() {\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'abstract' modifier", buf.toString());

		assertCodeActions(cu, e1);
	}


	@Test
	public void testEnumAbstractMethodMustBeImplementd() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public enum Animal {\n");
		buf.append("    CAT {\n");
		buf.append("        public abstract void makeNoise();\n");
		buf.append("    };\n");
		buf.append("    public abstract void makeNoise();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Animal.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public enum Animal {\n");
		buf.append("    CAT {\n");
		buf.append("        public void makeNoise() {\n");
		buf.append("        }\n");
		buf.append("    };\n");
		buf.append("    public abstract void makeNoise();\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'abstract' modifier", buf.toString());

		assertCodeActions(cu, e1);
	}
}
