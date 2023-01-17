/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/JavadocQuickFixTest.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Before;
import org.junit.Test;

public class JavadocQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		options.put(JavaCore.COMPILER_PB_INVALID_JAVADOC, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_INVALID_JAVADOC_TAGS, JavaCore.ENABLED);
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAGS_METHOD_TYPE_PARAMETERS, JavaCore.ENABLED);
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAGS, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS_OVERRIDING, JavaCore.ENABLED);

		fJProject1.setOptions(options);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testMissingParam1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param b\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a \n");
		buf.append("     * @param b\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		Expected e2 = new Expected("Add '@param' tag", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingParam2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param b \n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		Expected e2 = new Expected("Add '@param' tag", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingParam3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param b\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param b\n");
		buf.append("     * @param c \n");
		buf.append("     */\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		Expected e2 = new Expected("Add '@param' tag", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingParam4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param <A>\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param a\n");
		buf.append("     */\n");
		buf.append("    public <A, B> void foo(int a) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param <A>\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param <B> \n");
		buf.append("     * @param a\n");
		buf.append("     */\n");
		buf.append("    public <A, B> void foo(int a) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		Expected e2 = new Expected("Add '@param' tag", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingParam5() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * @param <B> Hello\n");
		buf.append(" */\n");
		buf.append("public class E<A, B> {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * @param <A> \n");
		buf.append(" * @param <B> Hello\n");
		buf.append(" */\n");
		buf.append("public class E<A, B> {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		Expected e2 = new Expected("Add '@param' tag", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingParam6() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * @author ae\n");
		buf.append(" */\n");
		buf.append("public class E<A> {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * @author ae\n");
		buf.append(" * @param <A> \n");
		buf.append(" */\n");
		buf.append("public class E<A> {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		Expected e2 = new Expected("Add '@param' tag", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingReturn1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param b\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public int foo(int b, int c) {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param b\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param c\n");
		buf.append("     * @return \n");
		buf.append("     */\n");
		buf.append("    public int foo(int b, int c) {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		Expected e2 = new Expected("Add '@return' tag", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingReturn2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     */\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @return \n");
		buf.append("     */\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		Expected e2 = new Expected("Add '@return' tag", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	public void testMissingReturn3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @throws Exception\n");
		buf.append("     */\n");
		buf.append("    public int foo() throws Exception {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @return \n");
		buf.append("     * @throws Exception\n");
		buf.append("     */\n");
		buf.append("    public int foo() throws Exception {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add '@param' tag", buf.toString());
		Expected e2 = new Expected("Add all missing tags", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingThrows() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @return Returns an Int\n");
		buf.append("     */\n");
		buf.append("    public int foo() throws Exception {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @return Returns an Int\n");
		buf.append("     * @throws Exception \n");
		buf.append("     */\n");
		buf.append("    public int foo() throws Exception {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		Expected e2 = new Expected("Add '@throws' tag", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testInsertAllMissing1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @throws Exception\n");
		buf.append("     */\n");
		buf.append("    public int foo(int a, int b) throws NullPointerException, Exception {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a \n");
		buf.append("     * @param b \n");
		buf.append("     * @return \n");
		buf.append("     * @throws NullPointerException \n");
		buf.append("     * @throws Exception\n");
		buf.append("     */\n");
		buf.append("    public int foo(int a, int b) throws NullPointerException, Exception {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testInsertAllMissing2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param b\n");
		buf.append("     * @return a number\n");
		buf.append("     */\n");
		buf.append("    public int foo(int a, int b, int c) throws NullPointerException, Exception {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a \n");
		buf.append("     * @param b\n");
		buf.append("     * @param c \n");
		buf.append("     * @return a number\n");
		buf.append("     * @throws NullPointerException \n");
		buf.append("     * @throws Exception \n");
		buf.append("     */\n");
		buf.append("    public int foo(int a, int b, int c) throws NullPointerException, Exception {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testInsertAllMissing3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E<S, T> {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * @param <S> \n");
		buf.append(" * @param <T> \n");
		buf.append(" */\n");
		buf.append("public class E<S, T> {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testInsertAllMissing4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param <B> test\n");
		buf.append("     * @param b\n");
		buf.append("     * @return a number\n");
		buf.append("     */\n");
		buf.append("    public <A, B> int foo(int a, int b) throws NullPointerException {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param <A> \n");
		buf.append("     * @param <B> test\n");
		buf.append("     * @param a \n");
		buf.append("     * @param b\n");
		buf.append("     * @return a number\n");
		buf.append("     * @throws NullPointerException \n");
		buf.append("     */\n");
		buf.append("    public <A, B> int foo(int a, int b) throws NullPointerException {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add all missing tags", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testRemoveParamTag1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public void foo(int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public void foo(int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove tag", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveParamTag2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param a\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove tag", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveThrowsTag1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param c\n");
		buf.append("     * @throws Exception Thrown by surprise.\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a, int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a, int c) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove tag", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveThrowsTag2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @exception Exception\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove tag", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveThrowsTag3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @exception Exception\n");
		buf.append("     * @exception java.io.IOException\n");
		buf.append("     * @exception NullPointerException\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) throws IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @exception java.io.IOException\n");
		buf.append("     * @exception NullPointerException\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) throws IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove tag", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveReturnTag1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @return Returns the result.\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @exception Exception\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) throws Exception {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @exception Exception\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) throws Exception {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove tag", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveUnknownTag1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @return Returns the result.\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @exception Exception\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) throws Exception {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     *      comment on second line.\n");
		buf.append("     * @exception Exception\n");
		buf.append("     */\n");
		buf.append("    public void foo(int a) throws Exception {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove tag", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMissingMethodComment1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    public <A> void foo(int a) throws IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param <A>\n");
		buf.append("     * @param a\n");
		buf.append("     * @throws IOException\n");
		buf.append("     */\n");
		buf.append("    public <A> void foo(int a) throws IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add Javadoc for 'foo'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMissingOverrideMethodComment1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" *\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    public String toString() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		assertCodeActionNotExists(cu, "Add Javadoc for 'toString'");
	}

	@Test
	public void testMissingMethodComment3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * Some comment\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    public void empty() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * Some comment\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * \n");
		buf.append("     */\n");
		buf.append("    public void empty() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add Javadoc for 'empty'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMissingOverrideMethodComment2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class B extends A<Integer> {\n");
		buf.append("    public void foo(Integer x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("class A<T extends Number> {\n");
		buf.append("    /**\n");
		buf.append("     * @param x\n");
		buf.append("     */\n");
		buf.append("    public void foo(T x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", buf.toString(), false, null);
		assertCodeActionNotExists(cu, "Add Javadoc for 'foo'");
	}

	@Test
	public void testMissingConstructorComment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    public E(int a) throws IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param a\n");
		buf.append("     * @throws IOException\n");
		buf.append("     */\n");
		buf.append("    public E(int a) throws IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add Javadoc for 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMissingTypeComment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<A, B> {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" * @param <A>\n");
		buf.append(" * @param <B>\n");
		buf.append(" */\n");
		buf.append("public class E<A, B> {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add Javadoc for 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMissingFieldComment() throws Exception {
		Map<String, String> original = fJProject1.getOptions(false);
		HashMap<String, String> newOptions = new HashMap<>(original);
		this.setIgnoredCommands("Extract.*");
		// newOptions.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS,
		// JavaCore.ERROR);
		// newOptions.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS_VISIBILITY,
		// JavaCore.PUBLIC);
		fJProject1.setOptions(newOptions);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    public static final int COLOR= 1;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("/**\n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     *\n");
		buf.append("     */\n");
		buf.append("    public static final int COLOR= 1;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add Javadoc for 'COLOR'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidQualification1() throws Exception {
		Map<String, String> original = fJProject1.getOptions(false);
		HashMap<String, String> newOptions = new HashMap<>(original);
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_4, newOptions);
		fJProject1.setOptions(newOptions);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("    public static class B {\n");
		buf.append("        public static class C {\n");
		buf.append("            \n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("pack2", false, null);
		buf = new StringBuilder();
		buf.append("package pack2;\n");
		buf.append("\n");
		buf.append("import pack.A.B.C;\n");
		buf.append("\n");
		buf.append("/**\n");
		buf.append(" * {@link C} \n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack2.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack2;\n");
		buf.append("\n");
		buf.append("import pack.A.B.C;\n");
		buf.append("\n");
		buf.append("/**\n");
		buf.append(" * {@link pack.A.B.C} \n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Qualify inner type name", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidQualification2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("    public static class B {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("pack2", false, null);
		buf = new StringBuilder();
		buf.append("package pack2;\n");
		buf.append("\n");
		buf.append("import pack.A;\n");
		buf.append("\n");
		buf.append("/**\n");
		buf.append(" * {@link A.B} \n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack2.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack2;\n");
		buf.append("\n");
		buf.append("import pack.A;\n");
		buf.append("\n");
		buf.append("/**\n");
		buf.append(" * {@link pack.A.B} \n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Qualify inner type name", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidQualification3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("    public interface B {\n");
		buf.append("        void foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("pack2", false, null);
		buf = new StringBuilder();
		buf.append("package pack2;\n");
		buf.append("\n");
		buf.append("import pack.A;\n");
		buf.append("\n");
		buf.append("/**\n");
		buf.append(" * {@link A.B#foo()} \n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack2.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack2;\n");
		buf.append("\n");
		buf.append("import pack.A;\n");
		buf.append("\n");
		buf.append("/**\n");
		buf.append(" * {@link pack.A.B#foo()} \n");
		buf.append(" */\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Qualify inner type name", buf.toString());
		assertCodeActions(cu, e1);
	}

}