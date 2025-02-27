/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.refactoring;

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.lsp4j.CodeActionKind;
import org.junit.Before;
import org.junit.Test;

public class MakeStaticTest extends AbstractSelectionTest {

	private static final String MAKE_STATIC = RefactoringCoreMessages.MakeStaticRefactoring_name;

	private IJavaProject testProject;

	private IPackageFragmentRoot testSourceFolder;

	@Before
	public void setup() throws Exception {
		testProject = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		testProject.setOptions(options);
		testSourceFolder = testProject.getPackageFragmentRoot(testProject.getProject().getFolder("src"));
	}

	@Test
	public void testArrayParameterAndReturnType() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("", false, null);

		String input = "public class Foo {\n"
				+ "\n"
				+ "	public String[] bar(String[] ending) {\n"
				+ "		String[] j = new String[] {ending[0], ending[1]};\n"
				+ "		return j;\n"
				+ "	}\n"
				+ "\n"
				+ "	public static void foo() {\n"
				+ "		Foo instance = new Foo();\n"
				+ "		String[] stringArray = new String[] {\"bar\", \"bar\"};\n"
				+ "		String[] j = instance.bar(stringArray);\n"
				+ "	}\n"
				+ "}";

		ICompilationUnit cu = pack1.createCompilationUnit("Foo.java", input, false, null);

		String output = "public class Foo {\n"
				+ "\n"
				+ "	public static String[] bar(String[] ending) {\n"
				+ "		String[] j = new String[] {ending[0], ending[1]};\n"
				+ "		return j;\n"
				+ "	}\n"
				+ "\n"
				+ "	public static void foo() {\n"
				+ "		Foo instance = new Foo();\n"
				+ "		String[] stringArray = new String[] {\"bar\", \"bar\"};\n"
				+ "		String[] j = Foo.bar(stringArray);\n"
				+ "	}\n"
				+ "}";

		Expected expected = new Expected(MAKE_STATIC, output, CodeActionKind.RefactorInline);
		assertCodeActions(cu, CodeActionUtil.getRange(cu, "bar(String[] ending)", 0), expected);
	}

	@Test
	public void testConcatenatedFieldAccessAndQualifiedNames() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("", false, null);

		String input = "public class Foo {\n"
				+ "	Foo foo;\n"
				+ "\n"
				+ "	int i= 0;\n"
				+ "\n"
				+ "	public void bar() {\n"
				+ "		//Field Access\n"
				+ "		this.foo.foo.foo.method();\n"
				+ "		this.getInstance().getInstance().method();\n"
				+ "		foo.getInstance().foo.getInstance().foo.method();\n"
				+ "		getInstance().foo.getInstance().foo.getInstance().method();\n"
				+ "\n"
				+ "		this.foo.foo.foo.i++;\n"
				+ "		this.getInstance().getInstance().i++;\n"
				+ "		foo.getInstance().foo.getInstance().i++;\n"
				+ "		getInstance().foo.getInstance().foo.getInstance().i++;\n"
				+ "\n"
				+ "		//Qualified Name\n"
				+ "		foo.foo.foo = foo.foo;\n"
				+ "	}\n"
				+ "\n"
				+ "	public Foo getInstance() {\n"
				+ "		return this;\n"
				+ "	}\n"
				+ "\n"
				+ "	public void method() {\n"
				+ "	}\n"
				+ "}";

		ICompilationUnit cu = pack1.createCompilationUnit("Foo.java", input, false, null);

		String output = "public class Foo {\n"
				+ "	Foo foo;\n"
				+ "\n"
				+ "	int i= 0;\n"
				+ "\n"
				+ "	public static void bar(Foo foo) {\n"
				+ "		//Field Access\n"
				+ "		foo.foo.foo.foo.method();\n"
				+ "		foo.getInstance().getInstance().method();\n"
				+ "		foo.foo.getInstance().foo.getInstance().foo.method();\n"
				+ "		foo.getInstance().foo.getInstance().foo.getInstance().method();\n"
				+ "\n"
				+ "		foo.foo.foo.foo.i++;\n"
				+ "		foo.getInstance().getInstance().i++;\n"
				+ "		foo.foo.getInstance().foo.getInstance().i++;\n"
				+ "		foo.getInstance().foo.getInstance().foo.getInstance().i++;\n"
				+ "\n"
				+ "		//Qualified Name\n"
				+ "		foo.foo.foo.foo = foo.foo.foo;\n"
				+ "	}\n"
				+ "\n"
				+ "	public Foo getInstance() {\n"
				+ "		return this;\n"
				+ "	}\n"
				+ "\n"
				+ "	public void method() {\n"
				+ "	}\n"
				+ "}";

		Expected expected = new Expected(MAKE_STATIC, output, CodeActionKind.RefactorInline);
		assertCodeActions(cu, CodeActionUtil.getRange(cu, "bar()", 0), expected);
	}

	@Test
	public void testDuplicateParamName() throws Exception {
		IPackageFragment pack1 = testSourceFolder.createPackageFragment("", false, null);

		String input = "public class Foo {\n"
				+ "\n"
				+ "	String j= \"\";\n"
				+ "\n"
				+ "	public String bar(String foo) {\n"
				+ "		String i= this.j;\n"
				+ "		return i;\n"
				+ "	}\n"
				+ "\n"
				+ "	public void method() {\n"
				+ "		String j= this.bar(\"bar\");\n"
				+ "	}\n"
				+ "}";

		ICompilationUnit cu = pack1.createCompilationUnit("Foo.java", input, false, null);

		String output = "public class Foo {\n"
				+ "\n"
				+ "	String j= \"\";\n"
				+ "\n"
				+ "	public static String bar(Foo foo2, String foo) {\n"
				+ "		String i= foo2.j;\n"
				+ "		return i;\n"
				+ "	}\n"
				+ "\n"
				+ "	public void method() {\n"
				+ "		String j= Foo.bar(this, \"bar\");\n"
				+ "	}\n"
				+ "}";

		Expected expected = new Expected(MAKE_STATIC, output, CodeActionKind.RefactorInline);
		assertCodeActions(cu, CodeActionUtil.getRange(cu, "bar(String foo)", 0), expected);

	}
}
