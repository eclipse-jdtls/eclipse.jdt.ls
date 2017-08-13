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
}
