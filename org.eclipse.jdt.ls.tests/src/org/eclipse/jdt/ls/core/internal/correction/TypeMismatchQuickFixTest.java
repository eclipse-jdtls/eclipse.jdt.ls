/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/ReorgQuickFixTest.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla <bmuskalla@eclipsesource.com> - [quick fix] proposes wrong cast from Object to primitive int - https://bugs.eclipse.org/bugs/show_bug.cgi?id=100593
 *     Benjamin Muskalla <bmuskalla@eclipsesource.com> - [quick fix] "Add exceptions to..." quickfix does nothing - https://bugs.eclipse.org/bugs/show_bug.cgi?id=107924
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
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TypeMismatchQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_NUMBER_OF_EMPTY_LINES_TO_PRESERVE, String.valueOf(99));
		options.put(JavaCore.COMPILER_PB_STATIC_ACCESS_RECEIVER, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION, JavaCore.IGNORE);
		fJProject1.setOptions(options);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testTypeMismatchInVarDecl() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        Thread th= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        Thread th= (Thread) o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'Thread'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object o) {\n");
		buf.append("        Object th= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'th' to 'Object'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Thread o) {\n");
		buf.append("        Thread th= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change type of 'o' to 'Thread'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testTypeMismatchInVarDecl2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class Container {\n");
		buf.append("    public List[] getLists() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Container.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container c) {\n");
		buf.append("         ArrayList[] lists= c.getLists();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container c) {\n");
		buf.append("         ArrayList[] lists= (ArrayList[]) c.getLists();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'ArrayList[]'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container c) {\n");
		buf.append("         List[] lists= c.getLists();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'lists' to 'List[]'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class Container {\n");
		buf.append("    public ArrayList[] getLists() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change return type of 'getLists(..)' to 'ArrayList[]'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testTypeMismatchInVarDecl3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Thread th= foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public Thread foo() {\n");
		buf.append("        Thread th= foo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'foo(..)' to 'Thread'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testTypeMismatchInVarDecl4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class Container {\n");
		buf.append("    public List getLists()[] {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Container.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E extends Container {\n");
		buf.append("    public void foo() {\n");
		buf.append("         ArrayList[] lists= super.getLists();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E extends Container {\n");
		buf.append("    public void foo() {\n");
		buf.append("         ArrayList[] lists= (ArrayList[]) super.getLists();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'ArrayList[]'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E extends Container {\n");
		buf.append("    public void foo() {\n");
		buf.append("         List[] lists= super.getLists();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'lists' to 'List[]'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class Container {\n");
		buf.append("    public ArrayList[] getLists() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change return type of 'getLists(..)' to 'ArrayList[]'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testTypeMismatchForInterface1() throws Exception {

		IPackageFragment pack0 = fSourceFolder.createPackageFragment("test0", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test0;\n");
		buf.append("public interface PrimaryContainer {\n");
		buf.append("}\n");
		pack0.createCompilationUnit("PrimaryContainer.java", buf.toString(), false, null);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Container {\n");
		buf.append("    public static Container getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Container.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("         PrimaryContainer list= Container.getContainer();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("         PrimaryContainer list= (PrimaryContainer) Container.getContainer();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'PrimaryContainer'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("         Container list= Container.getContainer();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'list' to 'Container'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("\n");
		buf.append("public class Container {\n");
		buf.append("    public static PrimaryContainer getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change return type of 'getContainer(..)' to 'PrimaryContainer'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("\n");
		buf.append("public class Container implements PrimaryContainer {\n");
		buf.append("    public static Container getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Let 'Container' implement 'PrimaryContainer'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	@Ignore("Requires LocalCorrectionsSubProcessor")
	public void testTypeMismatchForInterface2() throws Exception {
		IPackageFragment pack0 = fSourceFolder.createPackageFragment("test0", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test0;\n");
		buf.append("public interface PrimaryContainer {\n");
		buf.append("    PrimaryContainer duplicate(PrimaryContainer container);\n");
		buf.append("}\n");
		pack0.createCompilationUnit("PrimaryContainer.java", buf.toString(), false, null);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Container {\n");
		buf.append("    public static Container getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Container.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(PrimaryContainer primary) {\n");
		buf.append("         primary.duplicate(Container.getContainer());\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(PrimaryContainer primary) {\n");
		buf.append("         primary.duplicate((PrimaryContainer) Container.getContainer());\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("\n");
		buf.append("public class Container {\n");
		buf.append("    public static PrimaryContainer getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Remove unused import", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("\n");
		buf.append("public class Container implements PrimaryContainer {\n");
		buf.append("    public static Container getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Remove unused import", buf.toString());

		buf = new StringBuilder();
		buf.append("package test0;\n");
		buf.append("\n");
		buf.append("import test1.Container;\n");
		buf.append("\n");
		buf.append("public interface PrimaryContainer {\n");
		buf.append("    PrimaryContainer duplicate(Container container);\n");
		buf.append("}\n");
		Expected e4 = new Expected("Remove unused import", buf.toString());

		buf = new StringBuilder();
		buf.append("package test0;\n");
		buf.append("\n");
		buf.append("import test1.Container;\n");
		buf.append("\n");
		buf.append("public interface PrimaryContainer {\n");
		buf.append("    PrimaryContainer duplicate(PrimaryContainer container);\n");
		buf.append("\n");
		buf.append("    void duplicate(Container container);\n");
		buf.append("}\n");
		Expected e5 = new Expected("Remove unused import", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4, e5);
	}

	@Test
	public void testTypeMismatchForInterfaceInGeneric() throws Exception {

		IPackageFragment pack0 = fSourceFolder.createPackageFragment("test0", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test0;\n");
		buf.append("public interface PrimaryContainer<A> {\n");
		buf.append("}\n");
		pack0.createCompilationUnit("PrimaryContainer.java", buf.toString(), false, null);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Container<A> {\n");
		buf.append("    public Container<A> getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Container.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container<String> c) {\n");
		buf.append("         PrimaryContainer<String> list= c.getContainer();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container<String> c) {\n");
		buf.append("         PrimaryContainer<String> list= (PrimaryContainer<String>) c.getContainer();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'PrimaryContainer<String>'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container<String> c) {\n");
		buf.append("         Container<String> list= c.getContainer();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'list' to 'Container<String>'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("\n");
		buf.append("public class Container<A> {\n");
		buf.append("    public PrimaryContainer<String> getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change return type of 'getContainer(..)' to 'PrimaryContainer<String>'",
				buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("\n");
		buf.append("public class Container<A> implements PrimaryContainer<String> {\n");
		buf.append("    public Container<A> getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e4 = new Expected("Let 'Container' implement 'PrimaryContainer'", buf.toString());

		assertCodeActions(cu, e1, e2, e3, e4);
	}

	@Test
	public void testTypeMismatchForInterfaceInGeneric2() throws Exception {

		IPackageFragment pack0 = fSourceFolder.createPackageFragment("test0", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test0;\n");
		buf.append("public interface PrimaryContainer<A> {\n");
		buf.append("}\n");
		pack0.createCompilationUnit("PrimaryContainer.java", buf.toString(), false, null);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Container<A> {\n");
		buf.append("    public Container<A> getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Container.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container<List<?>> c) {\n");
		buf.append("         PrimaryContainer<?> list= c.getContainer();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container<List<?>> c) {\n");
		buf.append("         PrimaryContainer<?> list= (PrimaryContainer<?>) c.getContainer();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'PrimaryContainer<?>'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Container<List<?>> c) {\n");
		buf.append("         Container<List<?>> list= c.getContainer();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'list' to 'Container<List<?>>'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import test0.PrimaryContainer;\n");
		buf.append("\n");
		buf.append("public class Container<A> {\n");
		buf.append("    public PrimaryContainer<?> getContainer() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change return type of 'getContainer(..)' to 'PrimaryContainer<?>'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	@Ignore("Requires LocalCorrectionsSubProcessor")
	public void testTypeMismatchForParameterizedType() throws Exception {
		Map<String, String> tempOptions = new HashMap<>(fJProject1.getOptions(false));
		tempOptions.put(JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION, JavaCore.WARNING);
		tempOptions.put(JavaCore.COMPILER_PB_RAW_TYPE_REFERENCE, JavaCore.WARNING);
		fJProject1.setOptions(tempOptions);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.*;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List list= new ArrayList<Integer>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.*;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<Integer> list= new ArrayList<Integer>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove unused import", buf.toString());

		assertCodeActionExists(cu, e1);

	}

	@Test
	public void testTypeMismatchForParameterizedType2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.*;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<Integer> list= new ArrayList<Number>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.*;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<Number> list= new ArrayList<Number>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change type of 'list' to 'List<Number>'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testTypeMismatchInFieldDecl() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int time= System.currentTimeMillis();\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    int time= (int) System.currentTimeMillis();\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    long time= System.currentTimeMillis();\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'time' to 'long'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testTypeMismatchInFieldDeclNoImport() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private class StringBuilder { }\n");
		buf.append("    private final StringBuilder sb;\n");
		buf.append("    public E() {\n");
		buf.append("        sb= new java.lang.StringBuilder();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private class StringBuilder { }\n");
		buf.append("    private final java.lang.StringBuilder sb;\n");
		buf.append("    public E() {\n");
		buf.append("        sb= new java.lang.StringBuilder();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change type of 'sb' to 'StringBuilder'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testTypeMismatchInAssignment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        String str;\n");
		buf.append("        str= iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        String str;\n");
		buf.append("        str= (String) iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'String'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        Object str;\n");
		buf.append("        str= iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'str' to 'Object'", buf.toString());

		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testTypeMismatchInAssignment2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        String str, str2;\n");
		buf.append("        str= iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        String str, str2;\n");
		buf.append("        str= (String) iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'String'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        Object str;\n");
		buf.append("        String str2;\n");
		buf.append("        str= iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'str' to 'Object'", buf.toString());

		assertCodeActions(cu, e1, e2);

	}

	@Test
	public void testTypeMismatchInAssignment3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("public enum E {\n");
		buf.append("    A, B;\n");
		buf.append("    String str, str2;\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        str2= iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("public enum E {\n");
		buf.append("    A, B;\n");
		buf.append("    String str, str2;\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        str2= (String) iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'String'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Iterator;\n");
		buf.append("public enum E {\n");
		buf.append("    A, B;\n");
		buf.append("    String str;\n");
		buf.append("    Object str2;\n");
		buf.append("    public void foo(Iterator iter) {\n");
		buf.append("        str2= iter.next();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'str2' to 'Object'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testTypeMismatchInExpression() throws Exception {

		IPackageFragment pack0 = fSourceFolder.createPackageFragment("test0", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test0;\n");
		buf.append("public class Other {\n");
		buf.append("    public Object[] toArray() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack0.createCompilationUnit("Other.java", buf.toString(), false, null);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.Other;\n");
		buf.append("public class E {\n");
		buf.append("    public String[] foo(Other other) {\n");
		buf.append("        return other.toArray();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.Other;\n");
		buf.append("public class E {\n");
		buf.append("    public String[] foo(Other other) {\n");
		buf.append("        return (String[]) other.toArray();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'String[]'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import test0.Other;\n");
		buf.append("public class E {\n");
		buf.append("    public Object[] foo(Other other) {\n");
		buf.append("        return other.toArray();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method return type to 'Object[]'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test0;\n");
		buf.append("public class Other {\n");
		buf.append("    public String[] toArray() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change return type of 'toArray(..)' to 'String[]'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testCastOnCastExpression() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(List list) {\n");
		buf.append("        ArrayList a= (Cloneable) list;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(List list) {\n");
		buf.append("        ArrayList a= (ArrayList) list;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change cast to 'ArrayList'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(List list) {\n");
		buf.append("        Cloneable a= (Cloneable) list;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'a' to 'Cloneable'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingReturnType1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Base {\n");
		buf.append("    public String getName() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Base.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends Base {\n");
		buf.append("    public char[] getName() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends Base {\n");
		buf.append("    public String getName() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'getName(..)' to 'String'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Base {\n");
		buf.append("    public char[] getName() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change return type of overridden 'getName(..)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingReturnType2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public interface IBase {\n");
		buf.append("    List getCollection();\n");
		buf.append("}\n");
		pack1.createCompilationUnit("IBase.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E implements IBase {\n");
		buf.append("    public String[] getCollection() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E implements IBase {\n");
		buf.append("    public List getCollection() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'getCollection(..)' to 'List'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("public interface IBase {\n");
		buf.append("    String[] getCollection();\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change return type of implemented 'getCollection(..)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingReturnTypeOnGeneric() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Base<T extends Number> {\n");
		buf.append("    public String getName(T... t) {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Base.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends Base<Integer> {\n");
		buf.append("    public char[] getName(Integer... i) {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends Base<Integer> {\n");
		buf.append("    public String getName(Integer... i) {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'getName(..)' to 'String'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Base<T extends Number> {\n");
		buf.append("    public char[] getName(T... t) {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change return type of overridden 'getName(..)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingReturnTypeOnGeneric2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Base {\n");
		buf.append("    public Number getVal() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Base.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> extends Base {\n");
		buf.append("    public T getVal() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> extends Base {\n");
		buf.append("    public Number getVal() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'getVal(..)' to 'Number'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testMismatchingReturnTypeOnGenericMethod() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.lang.annotation.Annotation;\n");
		buf.append("import java.lang.reflect.AccessibleObject;\n");
		buf.append("public class E {\n");
		buf.append("    void m() {\n");
		buf.append("        new AccessibleObject() {\n");
		buf.append("            public <T extends Annotation> void getAnnotation(Class<T> annotationClass) {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.lang.annotation.Annotation;\n");
		buf.append("import java.lang.reflect.AccessibleObject;\n");
		buf.append("public class E {\n");
		buf.append("    void m() {\n");
		buf.append("        new AccessibleObject() {\n");
		buf.append("            public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'getAnnotation(..)' to 'T'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testMismatchingReturnTypeOnGenericMethod14() throws Exception {
		Map<String, String> options14 = new HashMap<>(fJProject1.getOptions(false));
		JavaModelUtil.setComplianceOptions(options14, JavaCore.VERSION_1_4);
		fJProject1.setOptions(options14);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.lang.reflect.AccessibleObject;\n");
		buf.append("public class E {\n");
		buf.append("    void m() {\n");
		buf.append("        new AccessibleObject() {\n");
		buf.append("            public void getAnnotation(Class annotationClass) {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.lang.annotation.Annotation;\n");
		buf.append("import java.lang.reflect.AccessibleObject;\n");
		buf.append("public class E {\n");
		buf.append("    void m() {\n");
		buf.append("        new AccessibleObject() {\n");
		buf.append("            public Annotation getAnnotation(Class annotationClass) {\n");
		buf.append("            }\n");
		buf.append("        };\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'getAnnotation(..)' to 'Annotation'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testMismatchingReturnTypeParameterized() throws Exception {
		// test for https://bugs.eclipse.org/bugs/show_bug.cgi?id=165913
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Base {\n");
		buf.append("    public Number getVal() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Base.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> extends Base {\n");
		buf.append("    public E<T> getVal() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E<T> extends Base {\n");
		buf.append("    public Number getVal() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'getVal(..)' to 'Number'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testMismatchingReturnTypeOnWildcardExtends() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E {\n");
		buf.append("    public Integer getIt(ArrayList<? extends Number> b) {\n");
		buf.append("        return b.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E {\n");
		buf.append("    public Integer getIt(ArrayList<? extends Number> b) {\n");
		buf.append("        return (Integer) b.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'Integer'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E {\n");
		buf.append("    public Number getIt(ArrayList<? extends Number> b) {\n");
		buf.append("        return b.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method return type to 'Number'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingReturnTypeOnWildcardSuper() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E {\n");
		buf.append("    public Integer getIt(ArrayList<? super Number> b) {\n");
		buf.append("        return b.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E {\n");
		buf.append("    public Integer getIt(ArrayList<? super Number> b) {\n");
		buf.append("        return (Integer) b.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'Integer'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("public class E {\n");
		buf.append("    public Object getIt(ArrayList<? super Number> b) {\n");
		buf.append("        return b.get(0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change method return type to 'Object'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingExceptions1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface IBase {\n");
		buf.append("    String[] getValues();\n");
		buf.append("}\n");
		pack1.createCompilationUnit("IBase.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E implements IBase {\n");
		buf.append("    public String[] getValues() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E implements IBase {\n");
		buf.append("    public String[] getValues() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove exceptions from 'getValues(..)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");
		buf.append("public interface IBase {\n");
		buf.append("    String[] getValues() throws IOException;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add exceptions to 'IBase.getValues(..)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingExceptions2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class Base {\n");
		buf.append("    String[] getValues() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Base.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.EOFException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E extends Base {\n");
		buf.append("    public String[] getValues() throws EOFException, ParseException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.EOFException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E extends Base {\n");
		buf.append("    public String[] getValues() throws EOFException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove exceptions from 'getValues(..)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class Base {\n");
		buf.append("    String[] getValues() throws IOException, ParseException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add exceptions to 'Base.getValues(..)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingExceptions3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class Base {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The parameter\n");
		buf.append("     *                  More about the parameter\n");
		buf.append("     * @return The returned argument\n");
		buf.append("     * @throws IOException IO problems\n");
		buf.append("     * @since 3.0\n");
		buf.append("     */\n");
		buf.append("    String[] getValues(int i) throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Base.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.EOFException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E extends Base {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The parameter\n");
		buf.append("     *                  More about the parameter\n");
		buf.append("     * @return The returned argument\n");
		buf.append("     * @throws EOFException EOF problems\n");
		buf.append("     * @throws ParseException Parse problems\n");
		buf.append("     */\n");
		buf.append("    public String[] getValues(int i) throws EOFException, ParseException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.EOFException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class E extends Base {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The parameter\n");
		buf.append("     *                  More about the parameter\n");
		buf.append("     * @return The returned argument\n");
		buf.append("     * @throws EOFException EOF problems\n");
		buf.append("     */\n");
		buf.append("    public String[] getValues(int i) throws EOFException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove exceptions from 'getValues(..)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("import java.text.ParseException;\n");
		buf.append("public class Base {\n");
		buf.append("    /**\n");
		buf.append("     * @param i The parameter\n");
		buf.append("     *                  More about the parameter\n");
		buf.append("     * @return The returned argument\n");
		buf.append("     * @throws IOException IO problems\n");
		buf.append("     * @throws ParseException\n");
		buf.append("     * @since 3.0\n");
		buf.append("     */\n");
		buf.append("    String[] getValues(int i) throws IOException, ParseException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add exceptions to 'Base.getValues(..)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingExceptionsOnGeneric() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface IBase<T> {\n");
		buf.append("    T[] getValues();\n");
		buf.append("}\n");
		pack1.createCompilationUnit("IBase.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E implements IBase<String> {\n");
		buf.append("    public String[] getValues() throws IOException {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.io.IOException;\n");
		buf.append("public class E implements IBase<String> {\n");
		buf.append("    public String[] getValues() {\n");
		buf.append("        return null;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove exceptions from 'getValues(..)'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.io.IOException;\n");
		buf.append("\n");
		buf.append("public interface IBase<T> {\n");
		buf.append("    T[] getValues() throws IOException;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Add exceptions to 'IBase<String>.getValues(..)'", buf.toString());

		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMismatchingExceptionsOnBinaryParent() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E implements Runnable {\n");
		buf.append("    public void run() throws ClassNotFoundException {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E implements Runnable {\n");
		buf.append("    public void run() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Remove exceptions from 'run(..)'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testTypeMismatchInAnnotationValues1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        String newAttrib();\n");
		buf.append("    }\n");
		buf.append("    @Annot(newAttrib= 1)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        int newAttrib();\n");
		buf.append("    }\n");
		buf.append("    @Annot(newAttrib= 1)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'newAttrib(..)' to 'int'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testTypeMismatchInAnnotationValues2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class Other<T> {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        String newAttrib();\n");
		buf.append("    }\n");
		buf.append("}\n");
		pack1.createCompilationUnit("Other.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    @Other.Annot(newAttrib= 1)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class Other<T> {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        int newAttrib();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'newAttrib(..)' to 'int'", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testTypeMismatchInSingleMemberAnnotation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        String value();\n");
		buf.append("    }\n");
		buf.append("    @Annot(1)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class E {\n");
		buf.append("    public @interface Annot {\n");
		buf.append("        int value();\n");
		buf.append("    }\n");
		buf.append("    @Annot(1)\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change return type of 'value(..)' to 'int'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testTypeMismatchWithEnumConstant() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public enum E {\n");
		buf.append("    ONE;\n");
		buf.append("    int m(int i) {\n");
		buf.append("            return ONE;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public enum E {\n");
		buf.append("    ONE;\n");
		buf.append("    E m(int i) {\n");
		buf.append("            return ONE;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change method return type to 'E'", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testTypeMismatchWithArrayLength() throws Exception {
		// test for bug 126488
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class TestShort {\n");
		buf.append("        public static void main(String[] args) {\n");
		buf.append("                short test=args.length;\n");
		buf.append("        }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("TestShort.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class TestShort {\n");
		buf.append("        public static void main(String[] args) {\n");
		buf.append("                short test=(short) args.length;\n");
		buf.append("        }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'short'", buf.toString());

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("public class TestShort {\n");
		buf.append("        public static void main(String[] args) {\n");
		buf.append("                int test=args.length;\n");
		buf.append("        }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'test' to 'int'", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testTypeMismatchWithTypeInSamePackage() throws Exception {
		// test for bug 198586
		IPackageFragment pack2 = fSourceFolder.createPackageFragment("test2", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {}\n");
		pack2.createCompilationUnit("E.java", buf.toString(), false, null);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Test {\n");
		buf.append("    test2.E e2= new Object();\n");
		buf.append("    E e1;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Test {\n");
		buf.append("    test2.E e2= (test2.E) new Object();\n");
		buf.append("    E e1;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'E'", buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class Test {\n");
		buf.append("    Object e2= new Object();\n");
		buf.append("    E e1;\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'e2' to 'Object'", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testTypeMismatchInForEachProposalsList() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<String> l= null;    \n");
		buf.append("        for (Number e : l) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<String> l= null;    \n");
		buf.append("        for (String e : l) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change type of 'e' to 'String'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testTypeMismatchInForEachProposalsListExtends() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<? extends String> l= null;    \n");
		buf.append("        for (Number e : l) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<? extends String> l= null;    \n");
		buf.append("        for (String e : l) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change type of 'e' to 'String'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testTypeMismatchInForEachProposalsListSuper() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<? super String> l= null;    \n");
		buf.append("        for (Number e : l) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        List<? super String> l= null;    \n");
		buf.append("        for (Object e : l) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change type of 'e' to 'Object'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testTypeMismatchInForEachProposalsArrays() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String[] l= null;\n");
		buf.append("        for (Number e : l) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("import java.util.List;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String[] l= null;\n");
		buf.append("        for (String e : l) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Change type of 'e' to 'String'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testTypeMismatchInForEachMissingType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String[] strings) {\n");
		buf.append("        for (s: strings) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(String[] strings) {\n");
		buf.append("        for (String s: strings) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Create loop variable 's'", buf.toString());
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testNullCheck() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public static void main(String arg) {\n");
		buf.append("        while (arg) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public static void main(boolean arg) {\n");
		buf.append("        while (arg) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("\n");
		Expected e1 = new Expected("Change type of 'arg' to 'boolean'", buf.toString());

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public static void main(String arg) {\n");
		buf.append("        while (arg != null) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("\n");
		Expected e2 = new Expected("Insert '!= null' check", buf.toString());
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testTypeMismatchObjectAndPrimitiveType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o= new Object();\n");
		buf.append("        int i= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o= new Object();\n");
		buf.append("        int i= (int) o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Object o= new Object();\n");
		buf.append("        Object i= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'i' to 'Object'", buf.toString());

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int o= new Object();\n");
		buf.append("        int i= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change type of 'o' to 'int'", buf.toString());

		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testTypeMismatchPrimitiveTypes() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(long o) {\n");
		buf.append("        int i= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(long o) {\n");
		buf.append("        int i= (int) o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add cast to 'int'", buf.toString());

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(long o) {\n");
		buf.append("        long i= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e2 = new Expected("Change type of 'i' to 'long'", buf.toString());

		buf = new StringBuilder();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int o) {\n");
		buf.append("        int i= o;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e3 = new Expected("Change type of 'o' to 'int'", buf.toString());
		assertCodeActions(cu, e1, e2, e3);
	}

}