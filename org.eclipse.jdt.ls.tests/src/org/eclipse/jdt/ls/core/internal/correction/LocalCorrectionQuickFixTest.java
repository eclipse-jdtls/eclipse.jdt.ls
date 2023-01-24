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

import static org.mockito.Mockito.when;

import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class LocalCorrectionQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		options.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_DEAD_CODE, JavaCore.WARNING);

		fJProject1.setOptions(options);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		this.setIgnoredCommands("Extract.*");
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
		buf.append("        // TODO Auto-generated method stub\n");
		buf.append("        throw new UnsupportedOperationException(\"Unimplemented method \'foo\'\");\n");		buf.append("    }\n");
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
		buf.append("        // TODO Auto-generated method stub\n");
		buf.append("        throw new UnsupportedOperationException(\"Unimplemented method \'foo\'\");\n");		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add unimplemented methods", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testUnusedPrivateField() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int count;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'count', keep assignments with side effects", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int count;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return the count\n");
		buf.append("     */\n");
		buf.append("    public int getCount() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("\n");
	    buf.append("    /**\n");
	    buf.append("     * @param count the count to set\n");
	    buf.append("     */\n");
		buf.append("    public void setCount(int count) {\n");
		buf.append("        this.count = count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Generate Getter and Setter for 'count'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnusedPrivateFieldWithResourceOperationSupport() throws Exception {
		ClientPreferences clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);
		testUnusedPrivateField();
	}

	@Test
	public void testUnusedPrivateField1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int count, color= count;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int count;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'color', keep assignments with side effects", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int count, color= count;\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @return the color\n");
		buf.append("     */\n");
		buf.append("    public int getColor() {\n");
		buf.append("        return color;\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * @param color the color to set\n");
		buf.append("     */\n");
		buf.append("    public void setColor(int color) {\n");
		buf.append("        this.color = color;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Generate Getter and Setter for 'color'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnusedPrivateField2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int count= 0;\n");
		buf.append("    public void foo() {\n");
		buf.append("        count= 1 + 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'count', keep assignments with side effects", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int count= 0;\n");
		buf.append("    /**\n");
		buf.append("     * @return the count\n");
		buf.append("     */\n");
		buf.append("    public int getCount() {\n");
		buf.append("        return count;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * @param count the count to set\n");
		buf.append("     */\n");
		buf.append("    public void setCount(int count) {\n");
		buf.append("        this.count = count;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        count= 1 + 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Generate Getter and Setter for 'count'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	// requires TP >= 4.24 I20220314-1800 - https://github.com/eclipse/eclipse.jdt.ls/issues/2026
	@Test
	@Ignore
	public void testUnusedParameter() throws Exception {
		Map<String, String> options = fJProject1.getOptions(true);
		options.put(JavaCore.COMPILER_PB_UNUSED_PARAMETER, JavaCore.ERROR);
		fJProject1.setOptions(options);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo(int i, int j) {\n");
		buf.append("       System.out.println(j);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo(int j) {\n");
		buf.append("       System.out.println(j);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused parameter 'i'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param i  \n");
		buf.append("     */\n");
		buf.append("    private void foo(int i, int j) {\n");
		buf.append("       System.out.println(j);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Document parameter to avoid 'unused' warning", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnusedMethod() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo() {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove method 'foo'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUnusedPrivateConstructor() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int i;\n");
		buf.append("    private E() {}\n");
		buf.append("    public E(int i) {\n");
		buf.append("        this.i = i;");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int i;\n");
		buf.append("    public E(int i) {\n");
		buf.append("        this.i = i;");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove constructor 'E'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUnusedLocalVariable() throws Exception {
		Map<String, String> options = fJProject1.getOptions(true);
		options.put(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.ERROR);
		fJProject1.setOptions(options);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i = 0;\n");
		buf.append("        i++;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'i' and all assignments", buf.toString());
		Expected e2 = new Expected("Remove 'i', keep assignments with side effects", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnusedLocalVariableWithKeepingAssignments() throws Exception {
		Map<String, String> options = fJProject1.getOptions(true);
		options.put(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.ERROR);
		fJProject1.setOptions(options);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class B {\n");
		buf.append("    void test(){\n");
		buf.append("        String c=\"Test\",d=String.valueOf(true),e=c;\n");
		buf.append("        e+=\"\";\n");
		buf.append("        d=\"blubb\";\n");
		buf.append("        d=String.valueOf(12);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class B {\n");
		buf.append("    void test(){\n");
		buf.append("        String c=\"Test\",e=c;\n");
		buf.append("        e+=\"\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'd' and all assignments", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class B {\n");
		buf.append("    void test(){\n");
		buf.append("        String c=\"Test\";\n");
		buf.append("        String.valueOf(true);\n");
		buf.append("        String e=c;\n");
		buf.append("        e+=\"\";\n");
		buf.append("        String.valueOf(12);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Remove 'd', keep assignments with side effects", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnusedTypeParameter() throws Exception {
		Map<String, String> options = fJProject1.getOptions(true);
		options.put(JavaCore.COMPILER_PB_UNUSED_TYPE_PARAMETER, JavaCore.ERROR);
		fJProject1.setOptions(options);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private static class Foo {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove type 'Foo'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUncaughtException() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() throws IOException {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUncaughtException2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public String goo() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     */\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo().substring(2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public String goo() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     * @throws IOException\n");
		buf.append("     */\n");
		buf.append("    public void foo() throws IOException {\n");
		buf.append("        goo().substring(2);\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public String goo() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     */\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo().substring(2);\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUncaughtException3() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public String goo() throws IOException, ParseException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     * @throws ParseException Parsing failed\n");
		buf.append("     */\n");
		buf.append("    public void foo() throws ParseException {\n");
		buf.append("        goo().substring(2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public String goo() throws IOException, ParseException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     * @throws ParseException Parsing failed\n");
		buf.append("     * @throws IOException\n");
		buf.append("     */\n");
		buf.append("    public void foo() throws ParseException, IOException {\n");
		buf.append("        goo().substring(2);\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public String goo() throws IOException, ParseException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     * @throws ParseException Parsing failed\n");
		buf.append("     */\n");
		buf.append("    public void foo() throws ParseException {\n");
		buf.append("        try {\n");
		buf.append("            goo().substring(2);\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUncaughtException4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("import java.io.InterruptedIOException;\n");
		buf.append("public class E {\n");
		buf.append("    public E goo(int i) throws InterruptedIOException {\n");
		buf.append("        return new E();\n");
		buf.append("    }\n");
		buf.append("    public E bar() throws FileNotFoundException {\n");
		buf.append("        return new E();\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     */\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo(1).bar();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("import java.io.InterruptedIOException;\n");
		buf.append("public class E {\n");
		buf.append("    public E goo(int i) throws InterruptedIOException {\n");
		buf.append("        return new E();\n");
		buf.append("    }\n");
		buf.append("    public E bar() throws FileNotFoundException {\n");
		buf.append("        return new E();\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     * @throws InterruptedIOException\n");
		buf.append("     * @throws FileNotFoundException\n");
		buf.append("     */\n");
		buf.append("    public void foo() throws FileNotFoundException, InterruptedIOException {\n");
		buf.append("        goo(1).bar();\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("import java.io.InterruptedIOException;\n");
		buf.append("public class E {\n");
		buf.append("    public E goo(int i) throws InterruptedIOException {\n");
		buf.append("        return new E();\n");
		buf.append("    }\n");
		buf.append("    public E bar() throws FileNotFoundException {\n");
		buf.append("        return new E();\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     */\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo(1).bar();\n");
		buf.append("        } catch (FileNotFoundException | InterruptedIOException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Surround with try/multi-catch", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("import java.io.InterruptedIOException;\n");
		buf.append("public class E {\n");
		buf.append("    public E goo(int i) throws InterruptedIOException {\n");
		buf.append("        return new E();\n");
		buf.append("    }\n");
		buf.append("    public E bar() throws FileNotFoundException {\n");
		buf.append("        return new E();\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * Not much to say here.\n");
		buf.append("     */\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo(1).bar();\n");
		buf.append("        } catch (FileNotFoundException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        } catch (InterruptedIOException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e3 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testUncaughtException5() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=31554
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        try {\n");
		buf.append("            throw new IOException();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("            throw new IOException();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() throws IOException {\n");
		buf.append("        try {\n");
		buf.append("            throw new IOException();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("            throw new IOException();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    void foo() {\n");
		buf.append("        try {\n");
		buf.append("            throw new IOException();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("            try {\n");
		buf.append("                throw new IOException();\n");
		buf.append("            } catch (IOException e1) {\n");
		buf.append("                // TODO Auto-generated catch block\n");
		buf.append("                e1.printStackTrace();\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Surround with try/catch", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUncaughtExceptionImportConflict() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Test {\n");
		buf.append("    public void test1() {\n");
		buf.append("        test2();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test2() throws de.muenchen.test.Exception {\n");
		buf.append("        throw new de.muenchen.test.Exception();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test3() {\n");
		buf.append("        try {\n");
		buf.append("            java.io.File.createTempFile(\"\", \".tmp\");\n");
		buf.append("        } catch (Exception ex) {\n");
		buf.append("\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("de.muenchen.test", false, null);
		buf = new StringBuilder();
		buf.append("package de.muenchen.test;\n");
		buf.append("\n");
		buf.append("public class Exception extends java.lang.Throwable {\n");
		buf.append("}\n");
		pack2.createCompilationUnit("Exception.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Test {\n");
		buf.append("    public void test1() throws de.muenchen.test.Exception {\n");
		buf.append("        test2();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test2() throws de.muenchen.test.Exception {\n");
		buf.append("        throw new de.muenchen.test.Exception();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test3() {\n");
		buf.append("        try {\n");
		buf.append("            java.io.File.createTempFile(\"\", \".tmp\");\n");
		buf.append("        } catch (Exception ex) {\n");
		buf.append("\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Test {\n");
		buf.append("    public void test1() {\n");
		buf.append("        try {\n");
		buf.append("            test2();\n");
		buf.append("        } catch (de.muenchen.test.Exception e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test2() throws de.muenchen.test.Exception {\n");
		buf.append("        throw new de.muenchen.test.Exception();\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public void test3() {\n");
		buf.append("        try {\n");
		buf.append("            java.io.File.createTempFile(\"\", \".tmp\");\n");
		buf.append("        } catch (Exception ex) {\n");
		buf.append("\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUncaughtExceptionRemoveMoreSpecific() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.net.SocketException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * @throws SocketException Sockets are dangerous\n");
		buf.append("     * @since 3.0\n");
		buf.append("     */\n");
		buf.append("    public void foo() throws SocketException {\n");
		buf.append("        this.goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.net.SocketException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * @throws IOException\n");
		buf.append("     * @since 3.0\n");
		buf.append("     */\n");
		buf.append("    public void foo() throws IOException {\n");
		buf.append("        this.goo();\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.net.SocketException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("    /**\n");
		buf.append("     * @throws SocketException Sockets are dangerous\n");
		buf.append("     * @since 3.0\n");
		buf.append("     */\n");
		buf.append("    public void foo() throws SocketException {\n");
		buf.append("        try {\n");
		buf.append("            this.goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUncaughtExceptionToSurroundingTry() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() throws IOException, ParseException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            E.goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() throws IOException, ParseException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("    public void foo() throws ParseException {\n");
		buf.append("        try {\n");
		buf.append("            E.goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() throws IOException, ParseException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            E.goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } catch (ParseException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Add catch clause to surrounding try", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() throws IOException, ParseException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            E.goo();\n");
		buf.append("        } catch (IOException | ParseException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Add exception to existing catch clause", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public static void goo() throws IOException, ParseException {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            try {\n");
		buf.append("                E.goo();\n");
		buf.append("            } catch (ParseException e) {\n");
		buf.append("                // TODO Auto-generated catch block\n");
		buf.append("                e.printStackTrace();\n");
		buf.append("            }\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e4 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	// https://github.com/redhat-developer/vscode-java/issues/2711
	@Test
	public void testBug2711() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n" //
				+ "public class E {\n" //
				+ "    public void test () {\n" //
				+ "        throw new Exception();\n" //
				+ "        try {\n" //
				+ "        } catch (Exception e) {\n" //
				+ "            // TODO: handle exception\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n" //
				+ "public class E {\n" //
				+ "    public void test () {\n" //
				+ "        try {\n" //
				+ "            throw new Exception();\n" //
				+ "        } catch (Exception e) {\n" //
				+ "            // TODO Auto-generated catch block\n" //
				+ "            e.printStackTrace();\n" //
				+ "        }\n" //
				+ "        try {\n" //
				+ "        } catch (Exception e) {\n" //
				+ "            // TODO: handle exception\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}");
		Expected e1 = new Expected("Surround with try/catch", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMultiCatchUncaughtExceptions() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.EOFException;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() throws EOFException {}\n");
		buf.append("    public void bar() throws FileNotFoundException {}\n");
		buf.append("    public void test() {\n");
		buf.append("        System.out.println(1);\n");
		buf.append("        foo();\n");
		buf.append("        System.out.println(2);\n");
		buf.append("        bar();\n");
		buf.append("        System.out.println(3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.EOFException;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() throws EOFException {}\n");
		buf.append("    public void bar() throws FileNotFoundException {}\n");
		buf.append("    public void test() {\n");
		buf.append("        try {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("            foo();\n");
		buf.append("            System.out.println(2);\n");
		buf.append("            bar();\n");
		buf.append("            System.out.println(3);\n");
		buf.append("        } catch (EOFException | FileNotFoundException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Surround with try/multi-catch", buf.toString());

		String beginningExpr = "System.out.println(1);";
		String endingExpr = "System.out.println(3);";
		String sourceCode = cu.getSource();
		int offset = sourceCode.indexOf(beginningExpr);
		int length = sourceCode.indexOf(endingExpr) - offset + endingExpr.length();
		Range selection = JDTUtils.toRange(cu, offset, length);
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testMultiCatchUncaughtExceptions2() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.EOFException;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() throws EOFException {}\n");
		buf.append("    public void bar() throws FileNotFoundException {}\n");
		buf.append("    public void test() {\n");
		buf.append("        System.out.println(1);\n");
		buf.append("        foo();\n");
		buf.append("        System.out.println(2);\n");
		buf.append("        bar();\n");
		buf.append("        System.out.println(3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.EOFException;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() throws EOFException {}\n");
		buf.append("    public void bar() throws FileNotFoundException {}\n");
		buf.append("    public void test() {\n");
		buf.append("        System.out.println(1);\n");
		buf.append("        try {\n");
		buf.append("            foo();\n");
		buf.append("            System.out.println(2);\n");
		buf.append("            bar();\n");
		buf.append("        } catch (EOFException | FileNotFoundException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("        System.out.println(3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Surround with try/multi-catch", buf.toString());

		String beginningExpr = "foo();";
		String endingExpr = "bar();";
		String sourceCode = cu.getSource();
		int offset = sourceCode.indexOf(beginningExpr);
		int length = sourceCode.indexOf(endingExpr) - offset + endingExpr.length();
		Range selection = JDTUtils.toRange(cu, offset, length);
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testUncaughtExceptionOnSuper1() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.FileInputStream;\n");
		buf.append("public class E extends FileInputStream {\n");
		buf.append("    public E() {\n");
		buf.append("        super(\"x\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.FileInputStream;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("public class E extends FileInputStream {\n");
		buf.append("    public E() throws FileNotFoundException {\n");
		buf.append("        super(\"x\");\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUncaughtExceptionOnSuper2() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public A() throws Exception {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    /**\n");
		buf.append("     * @throws Exception sometimes...\n");
		buf.append("     */\n");
		buf.append("    public E() {\n");
		buf.append("        super();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    /**\n");
		buf.append("     * @throws Exception sometimes...\n");
		buf.append("     */\n");
		buf.append("    public E() throws Exception {\n");
		buf.append("        super();\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUncaughtExceptionOnSuper3() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A implements Runnable {\n");
		buf.append("    public void run() {\n");
		buf.append("        Class.forName(null);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A implements Runnable {\n");
		buf.append("    public void run() {\n");
		buf.append("        try {\n");
		buf.append("            Class.forName(null);\n");
		buf.append("        } catch (ClassNotFoundException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUncaughtExceptionOnSuper4() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    public void foo() {\n");
		buf.append("        throw new Exception();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    public void foo() throws Exception {\n");
		buf.append("        throw new Exception();\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends A {\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            throw new Exception();\n");
		buf.append("        } catch (Exception e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUncaughtExceptionOnSuper5() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=349051
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.Closeable;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("public class A implements Closeable {\n");
		buf.append("    public void close() {\n");
		buf.append("        throw new FileNotFoundException();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.Closeable;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("public class A implements Closeable {\n");
		buf.append("    public void close() throws FileNotFoundException {\n");
		buf.append("        throw new FileNotFoundException();\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.Closeable;\n");
		buf.append("import java.io.FileNotFoundException;\n");
		buf.append("public class A implements Closeable {\n");
		buf.append("    public void close() {\n");
		buf.append("        try {\n");
		buf.append("            throw new FileNotFoundException();\n");
		buf.append("        } catch (FileNotFoundException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e2 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUncaughtExceptionOnSuper6() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=349051
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.Closeable;\n");
		buf.append("public class A implements Closeable {\n");
		buf.append("    public void close() {\n");
		buf.append("        throw new Throwable();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.Closeable;\n");
		buf.append("public class A implements Closeable {\n");
		buf.append("    public void close() {\n");
		buf.append("        try {\n");
		buf.append("            throw new Throwable();\n");
		buf.append("        } catch (Throwable e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1);
	}

	public void testUncaughtExceptionOnThis() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public E() {\n");
		buf.append("        this(null);\n");
		buf.append("    }\n");
		buf.append("    public E(Object x) throws IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public E() throws IOException {\n");
		buf.append("        this(null);\n");
		buf.append("    }\n");
		buf.append("    public E(Object x) throws IOException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add throws declaration", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUncaughtExceptionDuplicate() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class MyException extends Exception {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("MyException.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void m1() throws IOException {\n");
		buf.append("        m2();\n");
		buf.append("    }\n");
		buf.append("    public void m2() throws IOException, ParseException, MyException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void m1() throws IOException, ParseException, MyException {\n");
		buf.append("        m2();\n");
		buf.append("    }\n");
		buf.append("    public void m2() throws IOException, ParseException, MyException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void m1() throws IOException {\n");
		buf.append("        try {\n");
		buf.append("            m2();\n");
		buf.append("        } catch (ParseException | MyException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    public void m2() throws IOException, ParseException, MyException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Surround with try/multi-catch", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void m1() throws IOException {\n");
		buf.append("        try {\n");
		buf.append("            m2();\n");
		buf.append("        } catch (ParseException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        } catch (MyException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    public void m2() throws IOException, ParseException, MyException {\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e3 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testMultipleUncaughtExceptions() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException, ParseException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException, ParseException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() throws IOException, ParseException {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add throws declaration", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException, ParseException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } catch (IOException | ParseException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Surround with try/multi-catch", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException, ParseException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        } catch (ParseException e) {\n");
		buf.append("            // TODO Auto-generated catch block\n");
		buf.append("            e.printStackTrace();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e3 = new Expected("Surround with try/catch", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testUncaughtExceptionForCloseable() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n"
				+ "\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.InputStream;\n"
				+ "import java.nio.file.Path;\n"
				+ "\n"
				+ "public class E {\n"
				+ "    public void test () {\n"
				+ "        InputStream inp = new FileInputStream(Path.of(\"test\").toFile());\n"
				+ "    }\n"
				+ "}");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n"
				+ "\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.IOException;\n"
				+ "import java.io.InputStream;\n"
				+ "import java.nio.file.Path;\n"
				+ "\n"
				+ "public class E {\n"
				+ "    public void test () {\n"
				+ "        try (InputStream inp = new FileInputStream(Path.of(\"test\").toFile())) {\n"
				+ "        } catch (IOException e) {\n"
				+ "            // TODO Auto-generated catch block\n"
				+ "            e.printStackTrace();\n"
				+ "        }\n"
				+ "    }\n"
				+ "}");

		Expected e1 = new Expected("Surround with try-with-resources", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUnneededCatchBlock() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } catch (ParseException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove catch clause", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() throws IOException {\n");
		buf.append("    }\n");
		buf.append("    public void foo() throws ParseException {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Replace catch clause with throws", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnneededCatchBlockInInitializer() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    static {\n");
		buf.append("        try {\n");
		buf.append("            int x= 1;\n");
		buf.append("        } catch (ParseException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E {\n");
		buf.append("    static {\n");
		buf.append("        int x= 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove catch clause", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testUnneededCatchBlockSingle() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove catch clause", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");
		buf.append("    public void foo() throws IOException {\n");
		buf.append("        goo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Replace catch clause with throws", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnneededCatchBlockWithFinally() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove catch clause", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E {\n");
		buf.append("    public void goo() {\n");
		buf.append("    }\n");
		buf.append("    public void foo() throws IOException {\n");
		buf.append("        try {\n");
		buf.append("            goo();\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Replace catch clause with throws", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testRemoveUnreachableCodeStmt() throws Exception {
		Hashtable<String, String> hashtable = JavaCore.getOptions();
		hashtable.put(JavaCore.COMPILER_PB_UNNECESSARY_ELSE, JavaCore.IGNORE);
		fJProject1.setOptions(hashtable);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int x) {\n");
		buf.append("        if (x == 9) {\n");
		buf.append("            return true;\n");
		buf.append("        } else\n");
		buf.append("            return false;\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int x) {\n");
		buf.append("        if (x == 9) {\n");
		buf.append("            return true;\n");
		buf.append("        } else\n");
		buf.append("            return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveUnreachableCodeStmt2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public String getName() {\n");
		buf.append("        try{\n");
		buf.append("            return \"fred\";\n");
		buf.append("        }\n");
		buf.append("        catch (Exception e){\n");
		buf.append("            return e.getLocalizedMessage();\n");
		buf.append("        }\n");
		buf.append("        System.err.print(\"wow\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		String[] expected = new String[1];
		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public String getName() {\n");
		buf.append("        try{\n");
		buf.append("            return \"fred\";\n");
		buf.append("        }\n");
		buf.append("        catch (Exception e){\n");
		buf.append("            return e.getLocalizedMessage();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveUnreachableCodeWhile() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo() {\n");
		buf.append("        while (false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo() {\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeIfThen() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (false) {\n");
		buf.append("            System.out.println(\"a\");\n");
		buf.append("        } else {\n");
		buf.append("            System.out.println(\"b\");\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(\"b\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeIfThen2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = new Object();\n");
		buf.append("        if (o != null) {\n");
		buf.append("            if (o == null) {\n");
		buf.append("            	System.out.println(\"hello\");\n");
		buf.append("        	} else {\n");
		buf.append("            	System.out.println(\"bye\");\n");
		buf.append("        	}\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = new Object();\n");
		buf.append("        if (o != null) {\n");
		buf.append("            System.out.println(\"bye\");\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeIfThen3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = new Object();\n");
		buf.append("        if (o != null) \n");
		buf.append("            if (o == null) {\n");
		buf.append("            	System.out.println(\"hello\");\n");
		buf.append("        	} else {\n");
		buf.append("            	System.out.println(\"bye\");\n");
		buf.append("            	System.out.println(\"bye-bye\");\n");
		buf.append("        	}\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = new Object();\n");
		buf.append("        if (o != null) {\n");
		buf.append("        	System.out.println(\"bye\");\n");
		buf.append("        	System.out.println(\"bye-bye\");\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeIfThen4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = new Object();\n");
		buf.append("        if (o != null) \n");
		buf.append("            if (true) \n");
		buf.append("            	if (o == null) \n");
		buf.append("            		System.out.println(\"hello\");\n");
		buf.append("		System.out.println(\"bye\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = new Object();\n");
		buf.append("        if (o != null) \n");
		buf.append("            if (true) {\n");
		buf.append("            }\n");
		buf.append("		System.out.println(\"bye\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeIfThen5() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = new Object();\n");
		buf.append("        if (o != null) \n");
		buf.append("            if (false) \n");
		buf.append("            	if (o == null) \n");
		buf.append("            		System.out.println(\"hello\");\n");
		buf.append("		System.out.println(\"bye\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = new Object();\n");
		buf.append("        if (o != null) {\n");
		buf.append("        }\n");
		buf.append("		System.out.println(\"bye\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeIfThenSwitch() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        switch (1) {\n");
		buf.append("            case 1:\n");
		buf.append("                if (false) {\n");
		buf.append("                	foo();\n");
		buf.append("					System.out.println(\"hi\");\n");
		buf.append("				} else {\n");
		buf.append("                	System.out.println(\"bye\");\n");
		buf.append("				}\n");
		buf.append("                break;\n");
		buf.append("            case 2:\n");
		buf.append("                foo();\n");
		buf.append("                break;\n");
		buf.append("            default:\n");
		buf.append("                break;\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        switch (1) {\n");
		buf.append("            case 1:\n");
		buf.append("            System.out.println(\"bye\");\n");
		buf.append("                break;\n");
		buf.append("            case 2:\n");
		buf.append("                foo();\n");
		buf.append("                break;\n");
		buf.append("            default:\n");
		buf.append("                break;\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeIfElse() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (Math.random() == -1 || true) {\n");
		buf.append("            System.out.println(\"a\");\n");
		buf.append("        } else {\n");
		buf.append("            System.out.println(\"b\");\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(\"a\");\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo() {\n");
		buf.append("        if (true) return false;\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo() {\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if ((false && b1) && b2) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (false && b2) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if ((b1 && false) && b2) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (b1 && false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (b1 && false) {\n");
		buf.append("            if (b2) {\n");
		buf.append("                return true;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Split && condition", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testRemoveDeadCodeAfterIf4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if ((((b1 && false))) && b2) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (b1 && false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (b1 && false) {\n");
		buf.append("            if (b2) {\n");
		buf.append("                return true;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Split && condition", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testRemoveDeadCodeAfterIf5() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if ((((b1 && false) && b2))) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (b1 && false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf6() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if ((((false && b1) && b2))) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (((false && b2))) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf7() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if ((((false && b1))) && b2) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (false && b2) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf8() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1) {\n");
		buf.append("        if ((((false && b1)))) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1) {\n");
		buf.append("        if (false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf9() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (false && b1 && b2) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (false) {\n");
		buf.append("            if (b1 && b2) {\n");
		buf.append("                return true;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Split && condition", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testRemoveDeadCodeAfterIf10() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (((false && b1 && b2))) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if (false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf11() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1) {\n");
		buf.append("        if ((true || b1) && false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1) {\n");
		buf.append("        if (true && false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf12() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2, boolean b3) {\n");
		buf.append("        if (((b1 && false) && b2) | b3) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2, boolean b3) {\n");
		buf.append("        if ((b1 && false) | b3) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeAfterIf13() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if ((false | false && b1) & b2) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1, boolean b2) {\n");
		buf.append("        if ((false | false) & b2) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeConditional() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return true ? 1 : 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeConditional2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = true ? new Integer(1) + 2 : new Double(0.0) + 3;\n");
		buf.append("        System.out.println(o);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = (double) (new Integer(1) + 2);\n");
		buf.append("        System.out.println(o);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeConditional3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = true ? new Integer(1) : new Double(0.0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);


		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o = (double) new Integer(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveDeadCodeMultiStatements() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true)\n");
		buf.append("            return;\n");
		buf.append("        foo();\n");
		buf.append("        foo();\n");
		buf.append("        foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        return;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove (including condition)", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveUnreachableCodeMultiStatementsSwitch() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        switch (1) {\n");
		buf.append("            case 1:\n");
		buf.append("                foo();\n");
		buf.append("                break;\n");
		buf.append("                foo();\n");
		buf.append("                new Object();\n");
		buf.append("            case 2:\n");
		buf.append("                foo();\n");
		buf.append("                break;\n");
		buf.append("            default:\n");
		buf.append("                break;\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        switch (1) {\n");
		buf.append("            case 1:\n");
		buf.append("                foo();\n");
		buf.append("                break;\n");
		buf.append("            case 2:\n");
		buf.append("                foo();\n");
		buf.append("                break;\n");
		buf.append("            default:\n");
		buf.append("                break;\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove", buf.toString());
	}

}
