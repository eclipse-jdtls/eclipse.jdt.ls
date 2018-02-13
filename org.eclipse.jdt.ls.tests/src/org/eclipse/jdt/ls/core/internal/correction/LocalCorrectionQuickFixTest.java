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
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Before;
import org.junit.Test;

public class LocalCorrectionQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		options.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);

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
		Expected e2 = new Expected("Create getter and setter for 'count'...", buf.toString());

		assertCodeActions(cu, e1, e2);
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
		Expected e2 = new Expected("Create getter and setter for 'color'...", buf.toString());

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
		buf.append("    public void foo() {\n");
		buf.append("        count= 1 + 2;\n");
		buf.append("    }\n");
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
		buf.append("}\n");
		Expected e2 = new Expected("Create getter and setter for 'count'...", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnusedParameter() throws Exception {
		Map<String, String> options = fJProject1.getOptions(true);
		options.put(JavaCore.COMPILER_PB_UNUSED_PARAMETER, JavaCore.ERROR);
		fJProject1.setOptions(options);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, int j) {\n");
		buf.append("       System.out.println(j);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int j) {\n");
		buf.append("       System.out.println(j);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'i', keep assignments with side effects", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    /**\n");
		buf.append("     * @param i  \n");
		buf.append("     */\n");
		buf.append("    public void foo(int i, int j) {\n");
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
		Expected e1 = new Expected("Remove 'i', keep assignments with side effects", buf.toString());

		assertCodeActions(cu, e1);
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

}
