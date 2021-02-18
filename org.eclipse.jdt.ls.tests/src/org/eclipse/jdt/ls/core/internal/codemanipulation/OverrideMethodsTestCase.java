/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.codemanipulation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.ls.core.internal.codemanipulation.OverrideMethodsOperation.OverridableMethod;
import org.eclipse.text.edits.TextEdit;
import org.junit.Test;

/**
 * It references the JDT test case
 * eclipse.jdt.ui/org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/core/source/AddUnimplementedMethodsTest.java
 *
 */
public class OverrideMethodsTestCase extends AbstractSourceTestCase {
	private IType fClassA, fInterfaceB, fClassC, fClassD, fInterfaceE;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		ICompilationUnit cu = fCuA;
		fClassA = cu.createType("public abstract class A {\n}\n", null, true, null);
		fClassA.createMethod("public abstract void a();\n", null, true, null);
		fClassA.createMethod("public abstract void b(java.util.Vector<java.util.Date> v);\n", null, true, null);

		cu = fPackageP.getCompilationUnit("B.java");
		fInterfaceB = cu.createType("public interface B {\n}\n", null, true, null);
		fInterfaceB.createMethod("void c(java.util.Hashtable h);\n", null, true, null);

		cu = fPackageP.getCompilationUnit("C.java");
		fClassC = cu.createType("public abstract class C {\n}\n", null, true, null);
		fClassC.createMethod("public void c(java.util.Hashtable h) {\n}\n", null, true, null);
		fClassC.createMethod("public abstract java.util.Enumeration d(java.util.Hashtable h) {\n}\n", null, true, null);

		cu = fPackageP.getCompilationUnit("D.java");
		fClassD = cu.createType("public abstract class D extends C {\n}\n", null, true, null);
		fClassD.createMethod("public abstract void c(java.util.Hashtable h);\n", null, true, null);

		cu = fPackageP.getCompilationUnit("E.java");
		fInterfaceE = cu.createType("public interface E {\n}\n", null, true, null);
		fInterfaceE.createMethod("void c(java.util.Hashtable h);\n", null, true, null);
		fInterfaceE.createMethod("void e() throws java.util.NoSuchElementException;\n", null, true, null);
	}

	/*
	 * basic test: extend an abstract class and an interface
	 */
	@Test
	public void test1() throws Exception {
		ICompilationUnit cu = fPackageP.getCompilationUnit("Test1.java");
		IType testClass = cu.createType("public class Test1 extends A implements B {\n}\n", null, true, null);

		List<OverridableMethod> overridableMethods = getOverridableMethods(testClass);
		checkUnimplementedMethods(new String[] { "a()", "b(Vector<Date>)", "c(Hashtable)" }, overridableMethods);

		addAndApplyOverridableMethods(testClass, overridableMethods);

		IMethod[] methods = testClass.getMethods();
		checkMethods(new String[] { "a", "b", "c", "equals", "clone", "toString", "finalize", "hashCode" }, methods);

		IImportDeclaration[] imports = cu.getImports();
		checkImports(new String[] { "java.util.Date", "java.util.Hashtable", "java.util.Vector" }, imports);
	}

	/*
	 * method c() of interface B is already implemented by class C
	 */
	@Test
	public void test2() throws Exception {
		ICompilationUnit cu = fPackageP.getCompilationUnit("Test2.java");
		IType testClass = cu.createType("public class Test2 extends C implements B {\n}\n", null, true, null);

		List<OverridableMethod> overridableMethods = getOverridableMethods(testClass);
		checkUnimplementedMethods(new String[] { "d(Hashtable)" }, overridableMethods);
		checkOverridableMethods(new String[] { "c(Hashtable)" }, overridableMethods);

		addAndApplyOverridableMethods(testClass, overridableMethods);

		IMethod[] methods = testClass.getMethods();
		checkMethods(new String[] { "c", "d", "equals", "clone", "toString", "finalize", "hashCode" }, methods);

		IImportDeclaration[] imports = cu.getImports();
		checkImports(new String[] { "java.util.Enumeration", "java.util.Hashtable" }, imports);
	}

	/*
	 * method c() is implemented in C but made abstract again in class D
	 */
	@Test
	public void test3() throws Exception {
		ICompilationUnit cu = fPackageP.getCompilationUnit("Test3.java");
		IType testClass = cu.createType("public class Test3 extends D {\n}\n", null, true, null);

		List<OverridableMethod> overridableMethods = getOverridableMethods(testClass);
		checkUnimplementedMethods(new String[] { "c(Hashtable)", "d(Hashtable)" }, overridableMethods);

		addAndApplyOverridableMethods(testClass, overridableMethods);

		IMethod[] methods = testClass.getMethods();
		checkMethods(new String[] { "c", "d", "equals", "clone", "toString", "finalize", "hashCode" }, methods);

		IImportDeclaration[] imports = cu.getImports();
		checkImports(new String[] { "java.util.Hashtable", "java.util.Enumeration" }, imports);
	}

	/*
	 * method c() defined in both interfaces B and E
	 */
	@Test
	public void test4() throws Exception {
		ICompilationUnit cu = fPackageP.getCompilationUnit("Test4.java");
		IType testClass = cu.createType("public class Test4 implements B, E {\n}\n", null, true, null);

		List<OverridableMethod> overridableMethods = getOverridableMethods(testClass);
		checkUnimplementedMethods(new String[] { "c(Hashtable)", "e()" }, overridableMethods);

		addAndApplyOverridableMethods(testClass, overridableMethods);

		IMethod[] methods = testClass.getMethods();
		checkMethods(new String[] { "c", "e", "equals", "clone", "toString", "finalize", "hashCode" }, methods);

		IImportDeclaration[] imports = cu.getImports();
		checkImports(new String[] { "java.util.Hashtable", "java.util.NoSuchElementException" }, imports);
	}

	@Test
	public void testCloneable() throws Exception {
		ICompilationUnit cu = fPackageP.getCompilationUnit("Test4.java");
		IType testClass = cu.createType("public class Test4 implements Cloneable {\n}\n", null, true, null);

		List<OverridableMethod> overridableMethods = getOverridableMethods(testClass);
		checkUnimplementedMethods(new String[] { "clone()" }, overridableMethods);

		addAndApplyOverridableMethods(testClass, overridableMethods);

		IMethod[] methods = testClass.getMethods();
		checkMethods(new String[] { "equals", "clone", "toString", "finalize", "hashCode" }, methods);
	}

	@Test
	public void testBug119171() throws Exception {
		StringBuilder buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("import java.util.Properties;\n");
		buf.append("public interface F {\n");
		buf.append("    public void b(Properties p);\n");
		buf.append("}\n");
		fPackageP.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("public class Properties {\n");
		buf.append("    public int get() {return 0;}\n");
		buf.append("}\n");
		fPackageP.createCompilationUnit("Properties.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("public class Test5 implements F {\n");
		buf.append("    public void foo() {\n");
		buf.append("        Properties p= new Properties();\n");
		buf.append("        p.get();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = fPackageP.getCompilationUnit("Test5.java");
		IType testClass = cu.createType(buf.toString(), null, true, null);
		List<OverridableMethod> overridableMethods = getOverridableMethods(testClass);
		addAndApplyOverridableMethods(testClass, overridableMethods);

		IMethod[] methods = testClass.getMethods();
		checkMethods(new String[] { "foo", "b", "clone", "equals", "finalize", "hashCode", "toString" }, methods);

		IImportDeclaration[] imports = cu.getImports();
		checkImports(new String[0], imports);
	}

	@Test
	public void testBug297183() throws Exception {
		StringBuilder buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("interface Shape {\r\n");
		buf.append("  int getX();\r\n");
		buf.append("  int getY();\r\n");
		buf.append("  int getEdges();\r\n");
		buf.append("  int getArea();\r\n");
		buf.append("}\r\n");
		fPackageP.createCompilationUnit("Shape.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package p;\n");
		buf.append("interface Circle extends Shape {\r\n");
		buf.append("  int getR();\r\n");
		buf.append("}\r\n");
		buf.append("\r\n");
		fPackageP.createCompilationUnit("Circle.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package P;\n");
		buf.append("public class DefaultCircle implements Circle {\n");
		buf.append("}\n");
		ICompilationUnit cu = fPackageP.getCompilationUnit("DefaultCircle.java");
		IType testClass = cu.createType(buf.toString(), null, true, null);

		List<OverridableMethod> overridableMethods = getOverridableMethods(testClass);
		checkUnimplementedMethods(new String[] { "getX()", "getY()", "getEdges()", "getArea()", "getR()" }, overridableMethods);
		List<OverridableMethod> unimplementedMethods = overridableMethods.stream().filter((method) -> method.unimplemented).collect(Collectors.toList());
		addAndApplyOverridableMethods(testClass, unimplementedMethods);

		IMethod[] methods = testClass.getMethods();
		checkMethods(new String[] { "getX", "getY", "getEdges", "getArea", "getR" }, methods);

		IImportDeclaration[] imports = cu.getImports();
		checkImports(new String[0], imports);
	}

	@Test
	public void testBug480682() throws Exception {
		StringBuilder buf = new StringBuilder();
		buf.append("public class Test480682 extends Base {\n");
		buf.append("}\n");
		buf.append("abstract class Base implements I {\n");
		buf.append("    @Override\n");
		buf.append("    public final void method1() {}\n");
		buf.append("}\n");
		buf.append("interface I {\n");
		buf.append("    void method1();\n");
		buf.append("    void method2();\n");
		buf.append("}\n");

		ICompilationUnit cu = fPackageP.createCompilationUnit("Test480682.java", buf.toString(), true, null);
		IType testClass = cu.createType(buf.toString(), null, true, null);

		List<OverridableMethod> overridableMethods = getOverridableMethods(testClass);
		checkUnimplementedMethods(new String[] { "method2()" }, overridableMethods);
		checkUnoverridableMethods(new String[] { "method1()" }, overridableMethods);

		List<OverridableMethod> unimplementedMethods = overridableMethods.stream().filter((method) -> method.unimplemented).collect(Collectors.toList());
		addAndApplyOverridableMethods(testClass, unimplementedMethods);

		IMethod[] methods = testClass.getMethods();
		checkMethods(new String[] { "method2" }, methods);
	}

	private List<OverridableMethod> getOverridableMethods(IType type) {
		return OverrideMethodsOperation.listOverridableMethods(type);
	}

	private void addAndApplyOverridableMethods(IType type, List<OverridableMethod> overridableMethods) throws ValidateEditException, CoreException {
		TextEdit edit = OverrideMethodsOperation.addOverridableMethods(type, overridableMethods.toArray(new OverridableMethod[overridableMethods.size()]));
		if (edit == null) {
			return;
		}
		ICompilationUnit unit = type.getCompilationUnit();
		JavaModelUtil.applyEdit(unit, edit, true, null);
		//		JavaModelUtil.reconcile(unit);
	}

	private void checkUnimplementedMethods(String[] expected, List<OverridableMethod> methods) {
		Set<String> methodSet = methods.stream().map((method) -> method.unimplemented ? method.name + "(" + String.join(",", method.parameters) + ")" : "").collect(Collectors.toSet());
		for (String nExpected : expected) {
			assertTrue("unimplemented method " + nExpected + " expected", methodSet.contains(nExpected));
		}
	}

	private void checkOverridableMethods(String[] expected, List<OverridableMethod> methods) {
		Set<String> methodSet = methods.stream().map((method) -> method.unimplemented ? "" : method.name + "(" + String.join(",", method.parameters) + ")").collect(Collectors.toSet());
		for (String nExpected : expected) {
			assertTrue("override method " + nExpected + " expected", methodSet.contains(nExpected));
		}
	}

	private void checkUnoverridableMethods(String[] expected, List<OverridableMethod> methods) {
		Set<String> methodSet = methods.stream().map((method) -> method.name + "(" + String.join(",", method.parameters) + ")").collect(Collectors.toSet());
		for (String nExpected : expected) {
			assertFalse("cannot override method " + nExpected + " expected", methodSet.contains(nExpected));
		}
	}

	private void checkMethods(String[] expected, IMethod[] methods) {
		int nMethods = methods.length;
		int nExpected = expected.length;
		assertTrue("" + nExpected + " methods expected, is " + nMethods, nMethods == nExpected);
		for (int i = 0; i < nExpected; i++) {
			String methName = expected[i];
			assertTrue("method " + methName + " expected", nameContained(methName, methods));
		}
	}

	private void checkImports(String[] expected, IImportDeclaration[] imports) {
		int nImports = imports.length;
		int nExpected = expected.length;
		if (nExpected != nImports) {
			StringBuilder buf = new StringBuilder();
			buf.append(nExpected).append(" imports expected, is ").append(nImports).append("\n");
			buf.append("expected:\n");
			for (int i = 0; i < expected.length; i++) {
				buf.append(expected[i]).append("\n");
			}
			buf.append("actual:\n");
			for (int i = 0; i < imports.length; i++) {
				buf.append(imports[i]).append("\n");
			}
			assertTrue(buf.toString(), false);
		}
		for (int i = 0; i < nExpected; i++) {
			String impName = expected[i];
			assertTrue("import " + impName + " expected", nameContained(impName, imports));
		}
	}

	private boolean nameContained(String methName, IJavaElement[] methods) {
		for (int i = 0; i < methods.length; i++) {
			if (methods[i].getElementName().equals(methName)) {
				return true;
			}
		}
		return false;
	}
}
