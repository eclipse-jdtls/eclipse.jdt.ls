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
import org.junit.Before;
import org.junit.Test;

/**
 * @author nikolas
 *
 */
public class StaticAccessQuickFixTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testNonStaticAccessToStaticField() throws Exception {
		int problem = IProblem.NonStaticAccessToStaticField;
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder bufA = new StringBuilder();
		bufA.append("package test1;\n");
		bufA.append("public enum EnumA {\n");
		bufA.append("  B1,\n");
		bufA.append("  B2;\n");
		bufA.append("  public void foo(){}\n");
		bufA.append("}");
		pack1.createCompilationUnit("EnumA.java", bufA.toString(), false, null);

		bufA = new StringBuilder();
		bufA.append("package test1;\n");
		bufA.append("public enum EnumA {\n");
		bufA.append("  B1,\n");
		bufA.append("  B2;\n");
		bufA.append("  public void foo(){}\n");
		bufA.append("}");

		StringBuilder bufB = new StringBuilder();
		bufB.append("package test1;\n");
		bufB.append("public class ClassC {\n");
		bufB.append("  void bar() {\n");
		bufB.append("    EnumA.B1.B1.foo();\n");
		bufB.append("    EnumA.B1.B2.foo();\n");
		bufB.append("  }\n");
		bufB.append("}");
		ICompilationUnit cu = pack1.createCompilationUnit("ClassC.java", bufB.toString(), false, null);


		bufB = new StringBuilder();
		bufB.append("package test1;\n");
		bufB.append("public class ClassC {\n");
		bufB.append("  void bar() {\n");
		bufB.append("    EnumA.B1.foo();\n");
		bufB.append("    EnumA.B1.B2.foo();\n");
		bufB.append("  }\n");
		bufB.append("}");

		Expected e1 = new Expected("Remove 'static' modifier of 'B1'", bufA.toString());
		Expected e2 = new Expected("Change access to static using 'EnumA' (declaring type)", bufB.toString());
		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testNonStaticAccessToStaticMethod() throws Exception {
		int problem = IProblem.NonStaticAccessToStaticMethod;
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		// Referenced from: https://www.intertech.com/Blog/a-static-method-should-be-accessed-in-a-static-way/
		StringBuilder bufA = new StringBuilder();
		bufA.append("package test1;\n");
		bufA.append("public class HelloWorld {\n");
		bufA.append("  public static void sayHello() {\n");
		bufA.append("    System.out.println(\"Hello!\");\n");
		bufA.append("  }\n");
		bufA.append("  public static void main(String[] args) {\n");
		bufA.append("    HelloWorld hw = new HelloWorld();\n");
		bufA.append("    hw.sayHello();\n");
		bufA.append("  }\n");
		bufA.append("}");
		ICompilationUnit cu = pack1.createCompilationUnit("HelloWorld.java", bufA.toString(), false, null);

		bufA = new StringBuilder();
		bufA.append("package test1;\n");
		bufA.append("public class HelloWorld {\n");
		bufA.append("  public void sayHello() {\n");
		bufA.append("    System.out.println(\"Hello!\");\n");
		bufA.append("  }\n");
		bufA.append("  public static void main(String[] args) {\n");
		bufA.append("    HelloWorld hw = new HelloWorld();\n");
		bufA.append("    hw.sayHello();\n");
		bufA.append("  }\n");
		bufA.append("}");

		Expected e1 = new Expected("Remove 'static' modifier of 'sayHello()'", bufA.toString());

		bufA = new StringBuilder();
		bufA.append("package test1;\n");
		bufA.append("public class HelloWorld {\n");
		bufA.append("  public static void sayHello() {\n");
		bufA.append("    System.out.println(\"Hello!\");\n");
		bufA.append("  }\n");
		bufA.append("  public static void main(String[] args) {\n");
		bufA.append("    HelloWorld hw = new HelloWorld();\n");
		bufA.append("    HelloWorld.sayHello();\n");
		bufA.append("  }\n");
		bufA.append("}");

		Expected e2 = new Expected("Change access to static using 'HelloWorld' (declaring type)", bufA.toString());
		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testNonStaticOrAlienTypeReceiver() throws Exception {
		int problem = IProblem.NonStaticOrAlienTypeReceiver;
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder bufA = new StringBuilder();
		bufA.append("package test1;\n");
		bufA.append("public class X {\n");
		bufA.append("  public interface I {\n");
		bufA.append("    private static void foo(){};\n");
		bufA.append("    void bar();");
		bufA.append("  }\n");
		bufA.append("  public static void main(String[] args) {\n");
		bufA.append("    I i = () -> {};\n");
		bufA.append("    i.foo();\n");
		bufA.append("  }\n");
		bufA.append("}");
		ICompilationUnit cu = pack1.createCompilationUnit("X.java", bufA.toString(), false, null);

		bufA = new StringBuilder();
		bufA.append("package test1;\n");
		bufA.append("public class X {\n");
		bufA.append("  public interface I {\n");
		bufA.append("    private static void foo(){};\n");
		bufA.append("    void bar();");
		bufA.append("  }\n");
		bufA.append("  public static void main(String[] args) {\n");
		bufA.append("    I i = () -> {};\n");
		bufA.append("    I.foo();\n");
		bufA.append("  }\n");
		bufA.append("}");

		Expected e1 = new Expected("Change access to static using 'I' (declaring type)", bufA.toString());

		assertCodeActions(cu, e1);

	}

	@Test
	public void testIndirectAccessToStaticField() throws Exception {
		// Eclipse Core FieldAccessTest#test002()
		// Cannot get code action to trigger, same in Eclipse
		// Same with IProblem.IndirectAccessToStaticMethod
		// To test, uncomment assert at bottom
		int problem = IProblem.IndirectAccessToStaticField;
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("foo", false, null);

		StringBuilder bufA = new StringBuilder();
		bufA.append("package foo;\n");
		bufA.append("public class BaseFoo {\n");
		bufA.append(" public static final int VAL = 0;\n");
		bufA.append("}");
		pack1.createCompilationUnit("BaseFoo.java", bufA.toString(), false, null);

		StringBuilder bufB = new StringBuilder();
		bufB.append("package foo;\n");
		bufB.append("public class NextFoo extends BaseFoo {\n");
		bufB.append("}");

		pack1.createCompilationUnit("NextFoo.java", bufB.toString(), false, null);

		StringBuilder bufC = new StringBuilder();
		bufC.append("package bar;\n");
		bufC.append("public class Bar {\n");
		bufC.append(" int v = foo.NextFoo.VAL;\n");
		bufC.append("}");

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("bar", false, null);
		ICompilationUnit cu = pack2.createCompilationUnit("Bar.java", bufC.toString(), false, null);

		bufC = new StringBuilder();
		bufC.append("package bar;\n");
		bufC.append("public class Bar {\n");
		bufC.append(" int v = BaseFoo.VAL;\n");
		bufC.append("}");

		Expected e1 = new Expected("Change access to static using 'I' (declaring type)", bufC.toString());

		//assertCodeActions(cu, e1);

	}

}
