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

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class AnonymousClassToNestedTest extends AbstractSelectionTest {

	private static final String CONVERT_ANONYMOUS_TO_NESTED = "Convert anonymous to nested class";

	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertToNestedWithInterface() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void test() {\n");
		buf.append("        Runnable run = new Runnable() {\n");
		buf.append("            @Override\n");
		buf.append("            public void run() {}\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private final class RunnableImplementation implements Runnable {\n");
		buf.append("        @Override\n");
		buf.append("        public void run() {}\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test() {\n");
		buf.append("        Runnable run = new RunnableImplementation();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected expected = new Expected(CONVERT_ANONYMOUS_TO_NESTED, buf.toString(), CodeActionKind.Refactor);

		Range range = CodeActionUtil.getRange(cu, "Runnable() {", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testConvertToNestedWithStaticModifier() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public static void test() {\n");
		buf.append("        Runnable run = new Runnable() {\n");
		buf.append("            @Override\n");
		buf.append("            public void run() {}\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static final class RunnableImplementation implements Runnable {\n");
		buf.append("        @Override\n");
		buf.append("        public void run() {}\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public static void test() {\n");
		buf.append("        Runnable run = new RunnableImplementation();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected expected = new Expected(CONVERT_ANONYMOUS_TO_NESTED, buf.toString(), CodeActionKind.Refactor);

		Range range = CodeActionUtil.getRange(cu, "Runnable() {", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testConvertToNestedWithConstructor() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface Foo {\n");
		buf.append("    int bar();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    public void test() {\n");
		buf.append("        int i = 1;\n");
		buf.append("        Foo foo = new Foo() {\n");
		buf.append("            @Override\n");
		buf.append("            public int bar() {\n");
		buf.append("                return i;\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface Foo {\n");
		buf.append("    int bar();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    private final class FooImplementation implements Foo {\n");
		buf.append("        private final int i;\n");
		buf.append("\n");
		buf.append("        private FooImplementation(int i) {\n");
		buf.append("            this.i = i;\n");
		buf.append("        }\n");
		buf.append("\n");
		buf.append("        @Override\n");
		buf.append("        public int bar() {\n");
		buf.append("            return i;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test() {\n");
		buf.append("        int i = 1;\n");
		buf.append("        Foo foo = new FooImplementation(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected expected = new Expected(CONVERT_ANONYMOUS_TO_NESTED, buf.toString(), CodeActionKind.Refactor);

		Range range = CodeActionUtil.getRange(cu, "Foo() {", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testConvertToNestedWithExtends() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class Foo {}\n");
		buf.append("public class E {\n");
		buf.append("    public void test() {\n");
		buf.append("        Foo foo = new Foo() {};\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class Foo {}\n");
		buf.append("public class E {\n");
		buf.append("    private final class FooExtension extends Foo {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test() {\n");
		buf.append("        Foo foo = new FooExtension();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected expected = new Expected(CONVERT_ANONYMOUS_TO_NESTED, buf.toString(), CodeActionKind.Refactor);

		Range range = CodeActionUtil.getRange(cu, "Foo() {", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testConvertToNestedCursor1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class Foo {}\n");
		buf.append("public class E {\n");
		buf.append("    public void test() {\n");
		buf.append("        Foo foo = new Foo(/*cursor*/) {};\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class Foo {}\n");
		buf.append("public class E {\n");
		buf.append("    private final class FooExtension extends Foo {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test() {\n");
		buf.append("        Foo foo = new FooExtension();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected expected = new Expected(CONVERT_ANONYMOUS_TO_NESTED, buf.toString(), CodeActionKind.Refactor);

		Range range = CodeActionUtil.getRange(cu, "/*cursor*/", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testConvertToNestedCursor2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class Foo {}\n");
		buf.append("public class E {\n");
		buf.append("    public void test() {\n");
		buf.append("        Foo foo = new Foo() /*cursor*/{};\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class Foo {}\n");
		buf.append("public class E {\n");
		buf.append("    private final class FooExtension extends Foo {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test() {\n");
		buf.append("        Foo foo = new FooExtension();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected expected = new Expected(CONVERT_ANONYMOUS_TO_NESTED, buf.toString(), CodeActionKind.Refactor);

		Range range = CodeActionUtil.getRange(cu, "/*cursor*/", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testConvertToNestedCursor3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class Foo {}\n");
		buf.append("public class E {\n");
		buf.append("    public void test() {\n");
		buf.append("        Foo foo = new Foo() {/*cursor*/};\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class Foo {}\n");
		buf.append("public class E {\n");
		buf.append("    private final class FooExtension extends Foo {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test() {\n");
		buf.append("        Foo foo = new FooExtension();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected expected = new Expected(CONVERT_ANONYMOUS_TO_NESTED, buf.toString(), CodeActionKind.Refactor);

		Range range = CodeActionUtil.getRange(cu, "/*cursor*/", 0);
		assertCodeActions(cu, range, expected);
	}
}
