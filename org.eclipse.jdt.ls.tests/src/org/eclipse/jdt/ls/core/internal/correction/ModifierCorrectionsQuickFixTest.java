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
public class ModifierCorrectionsQuickFixTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;
	private static final String proposal = "Remove invalid modifiers";

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testInvalidInterfaceModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public static interface E {\n");
		buf.append("    public void foo();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public void foo();\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidMemberInterfaceModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private interface Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    interface Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidInterfaceFieldModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public native int i;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public int i;\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidInterfaceMethodModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private strictfp void foo();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    void foo();\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidClassModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public volatile class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidMemberClassModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private class Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    class Inner {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidLocalClassModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo() {\n");
		buf.append("        static class Local {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo() {\n");
		buf.append("        class Local {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidClassFieldModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    strictfp public native int i;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public int i;\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidClassMethodModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public abstract class E {\n");
		buf.append("    volatile abstract void foo();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public abstract class E {\n");
		buf.append("    abstract void foo();\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidConstructorModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class Bug {\n");
		buf.append("    public static Bug() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Bug.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public class Bug {\n");
		buf.append("    public Bug() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidParamModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo(private int x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo(int x) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidVariableModifiers() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo() {\n");
		buf.append("        native int x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private void foo() {\n");
		buf.append("        int x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidEnumModifier() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("private final strictfp enum E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("strictfp enum E {\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidEnumModifier2() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public abstract enum E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidEnumConstructorModifier() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("enum E {\n");
		buf.append("	WHITE(1);\n");
		buf.append("\n");
		buf.append("	public final E(int foo) {\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("enum E {\n");
		buf.append("	WHITE(1);\n");
		buf.append("\n");
		buf.append("	E(int foo) {\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidMemberEnumModifier() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("class E {\n");
		buf.append("	final enum A {\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("class E {\n");
		buf.append("	enum A {\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidArgumentModifier() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public void foo(static String a);\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public void foo(String a);\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidModifierCombinationFinalVolatileForField() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public final volatile String A;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    public final String A;\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidStaticModifierForField() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private static String A = \"Test\";\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    static String A = \"Test\";\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidStaticModifierForMethod() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    private static void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public interface E {\n");
		buf.append("    static void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected(proposal, buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testOverrideStaticMethod() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    public static void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class C {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("public class E extends C {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove 'static' modifier of 'C.foo'(..)", buf.toString());

		assertCodeActions(cu, e1);
	}

}
