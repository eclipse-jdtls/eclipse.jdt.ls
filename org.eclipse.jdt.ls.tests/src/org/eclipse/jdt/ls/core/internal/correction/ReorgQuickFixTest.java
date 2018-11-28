/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http:www.eclipse.orglegalepl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/ReorgQuickFixTest.java
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 * Microsoft Corporation - adoptions for jdt.ls
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import static org.mockito.Mockito.when;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ReorgQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		fJProject1.setOptions(TestOptions.getDefaultOptions());

		JavaLanguageServerPlugin.setPreferencesManager(preferenceManager);
		when(preferenceManager.getClientPreferences().isResourceOperationSupported()).thenReturn(false);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testUnusedImports() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e2 = new Expected("Organize imports", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnusedImportsInDefaultPackage() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());

		buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e2 = new Expected("Organize imports", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testUnusedImportOnDemand() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("import java.net.*;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" Vector v;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" Vector v;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());
		Expected e2 = new Expected("Organize imports", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testCollidingImports() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.security.Permission;\n");
		buf.append("import java.security.acl.Permission;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" Permission p;\n");
		buf.append(" Vector v;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.security.Permission;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" Permission p;\n");
		buf.append(" Vector v;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());
		Expected e2 = new Expected("Organize imports", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testWrongPackageStatement() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		Expected e1 = new Expected("Change package declaration to 'test1'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongPackageStatementInEnum() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append("}\n");

		Expected e1 = new Expected("Change package declaration to 'test1'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongPackageStatementFromDefault() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		Expected e1 = new Expected("Remove package declaration 'package test2'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongDefaultPackageStatement() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test2", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		Expected e1 = new Expected("Add package declaration 'test2;'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongPackageStatementButColliding() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test2;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		Expected e1 = new Expected("Change package declaration to 'test1'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongTypeName() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Rename type to 'X'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongTypeName_bug180330() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("p", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("public class \\u0042 {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("C.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("public class C {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Rename type to 'C'", buf.toString());
		assertCodeActions(cu, e1);

	}

	@Test
	public void testWrongTypeNameButColliding() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		Expected e1 = new Expected("Rename type to 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongTypeNameWithConstructor() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append(" public X() {\n");
		buf.append(" X other;\n");
		buf.append(" }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append(" public E() {\n");
		buf.append(" E other;\n");
		buf.append(" }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Rename type to 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongTypeNameInEnum() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public enum X {\n");
		buf.append(" A;\n");
		buf.append(" X() {\n");
		buf.append(" }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public enum E {\n");
		buf.append(" A;\n");
		buf.append(" E() {\n");
		buf.append(" }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Rename type to 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testWrongTypeNameInAnnot() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public @interface X {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public @interface X {\n");
		buf.append("}\n");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public @interface E {\n");
		buf.append("}\n");

		Expected e1 = new Expected("Rename type to 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

}