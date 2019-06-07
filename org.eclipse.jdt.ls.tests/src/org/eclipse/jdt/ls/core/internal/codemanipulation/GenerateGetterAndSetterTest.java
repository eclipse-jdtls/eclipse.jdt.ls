/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
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

import static org.junit.Assert.assertNull;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.junit.Test;

public class GenerateGetterAndSetterTest extends AbstractSourceTestCase {

	private void runAndApplyOperation(IType type) throws OperationCanceledException, CoreException, MalformedTreeException, BadLocationException {
		runAndApplyOperation(type, true);
	}

	private TextEdit runOperation(IType type) throws OperationCanceledException, CoreException {
		return runOperation(type, true);
	}

	private void runAndApplyOperation(IType type, boolean generateComments) throws OperationCanceledException, CoreException, MalformedTreeException, BadLocationException {
		TextEdit edit = runOperation(type, generateComments);
		ICompilationUnit unit = type.getCompilationUnit();
		JavaModelUtil.applyEdit(unit, edit, true, null);
	}

	private TextEdit runOperation(IType type, boolean generateComments) throws OperationCanceledException, CoreException {
		GenerateGetterSetterOperation operation = new GenerateGetterSetterOperation(type, null, generateComments);
		return operation.createTextEdit(null);
	}

	private IType createNewType(String fQName) throws JavaModelException {
		final String pkg = fQName.substring(0, fQName.lastIndexOf('.'));
		final String typeName = fQName.substring(fQName.lastIndexOf('.') + 1);
		final IPackageFragment fragment = fRoot.createPackageFragment(pkg, true, null);
		final ICompilationUnit unit = fragment.getCompilationUnit(typeName + ".java");
		return unit.createType("public class " + typeName + " {\n}\n", null, true, null);
	}

	/**
	 * Tests normal getter/setter generation for one field.
	 *
	 * @throws Exception
	 */
	@Test
	public void test0() throws Exception {
		IField field1 = fClassA.createField("String field1;", null, false, new NullProgressMonitor());
		runAndApplyOperation(fClassA);

		/* @formatter:off */
		String expected= "public class A {\r\n" +
						"\r\n" +
						"	String field1;\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the field1.\r\n" +
						"	 */\r\n" +
						"	public String getField1() {\r\n" +
						"		return field1;\r\n" +
						"	}\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @param field1 The field1 to set.\r\n" +
						"	 */\r\n" +
						"	public void setField1(String field1) {\r\n" +
						"		this.field1 = field1;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, fClassA.getSource());
	}

	/**
	 * Tests normal getter/setter generation for one field.
	 *
	 * @throws Exception
	 */
	@Test
	public void testDoneWithSmartIs() throws Exception {
		IField field1 = fClassA.createField("boolean done;", null, false, new NullProgressMonitor());
		runAndApplyOperation(fClassA);

		/* @formatter:off */
		String expected= "public class A {\r\n" +
						"\r\n" +
						"	boolean done;\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the done.\r\n" +
						"	 */\r\n" +
						"	public boolean isDone() {\r\n" +
						"		return done;\r\n" +
						"	}\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @param done The done to set.\r\n" +
						"	 */\r\n" +
						"	public void setDone(boolean done) {\r\n" +
						"		this.done = done;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, fClassA.getSource());
	}

	/**
	 * Tests normal getter/setter generation for one field.
	 *
	 * @throws Exception
	 */
	@Test
	public void testIsDoneWithSmartIs() throws Exception {
		IField field1= fClassA.createField("boolean isDone;", null, false, new NullProgressMonitor());
		runAndApplyOperation(fClassA);

		/* @formatter:off */
		String expected= "public class A {\r\n" +
						"\r\n" +
						"	boolean isDone;\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the isDone.\r\n" +
						"	 */\r\n" +
						"	public boolean isDone() {\r\n" +
						"		return isDone;\r\n" +
						"	}\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @param isDone The isDone to set.\r\n" +
						"	 */\r\n" +
						"	public void setDone(boolean isDone) {\r\n" +
						"		this.isDone = isDone;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, fClassA.getSource());
	}

	/**
	 * No setter for final fields (if skipped by user, as per parameter)
	 *
	 * @throws Exception
	 */
	@Test
	public void test1() throws Exception {
		IField field1 = fClassA.createField("final String field1 = null;", null, false, new NullProgressMonitor());
		runAndApplyOperation(fClassA);

		/* @formatter:off */
		String expected= "public class A {\r\n" +
						"\r\n" +
						"	final String field1 = null;\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the field1.\r\n" +
						"	 */\r\n" +
						"	public String getField1() {\r\n" +
						"		return field1;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, fClassA.getSource());
	}

	/**
	 * Tests if full-qualified field declaration type is also full-qualified in
	 * setter parameter.
	 *
	 * @throws Exception
	 */
	@Test
	public void test2() throws Exception {
		createNewType("q.Other");
		IField field1 = fClassA.createField("q.Other field1;", null, false, new NullProgressMonitor());
		runAndApplyOperation(fClassA);

		/* @formatter:off */
		String expected= "public class A {\r\n" +
						"\r\n" +
						"	q.Other field1;\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the field1.\r\n" +
						"	 */\r\n" +
						"	public q.Other getField1() {\r\n" +
						"		return field1;\r\n" +
						"	}\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @param field1 The field1 to set.\r\n" +
						"	 */\r\n" +
						"	public void setField1(q.Other field1) {\r\n" +
						"		this.field1 = field1;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, fClassA.getSource());
	}

	/**
	 * Test parameterized types in field declarations
	 * @throws Exception
	 */
	@Test
	public void test3() throws Exception {
		/* @formatter:off */
		ICompilationUnit b = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"import java.util.HashMap;\r\n" +
				"import java.util.Map;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"\r\n" +
				"	Map<String,String> a = new HashMap<String,String>();\r\n" +
				"}\r\n", true, null);
		/* @formatter:on */

		IType classB = b.getType("B");
		runAndApplyOperation(classB);

		/* @formatter:off */
		String expected= "public class B {\r\n" +
						"\r\n" +
						"	Map<String,String> a = new HashMap<String,String>();\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the a.\r\n" +
						"	 */\r\n" +
						"	public Map<String, String> getA() {\r\n" +
						"		return a;\r\n" +
						"	}\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @param a The a to set.\r\n" +
						"	 */\r\n" +
						"	public void setA(Map<String, String> a) {\r\n" +
						"		this.a = a;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, classB.getSource());
	}

	/**
	 * Tests enum typed fields
	 *
	 * @throws Exception
	 */
	@Test
	public void test4() throws Exception {
		IType theEnum = fClassA.createType("private enum ENUM { C,D,E };", null, false, null);
		IField field1 = fClassA.createField("private ENUM someEnum;", theEnum, false, null);
		runAndApplyOperation(fClassA);

		/* @formatter:off */
		String expected= "public class A {\r\n" +
						"\r\n" +
						"	private ENUM someEnum;\r\n" +
						"\r\n" +
						"	private enum ENUM { C,D,E }\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the someEnum.\r\n" +
						"	 */\r\n" +
						"	public ENUM getSomeEnum() {\r\n" +
						"		return someEnum;\r\n" +
						"	}\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @param someEnum The someEnum to set.\r\n" +
						"	 */\r\n" +
						"	public void setSomeEnum(ENUM someEnum) {\r\n" +
						"		this.someEnum = someEnum;\r\n" +
						"	};\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, fClassA.getSource());
	}

	/**
	 * Test generation for more than one field
	 * @throws Exception
	 */
	@Test
	public void test5() throws Exception {
		createNewType("q.Other");
		/* @formatter:off */
		ICompilationUnit b= fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"import q.Other;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"\r\n" +
				"	private String a;\r\n" +
				"	private String b;\r\n" +
				"	protected Other c;\r\n" +
				"	public String d;\r\n" +
				"}", true, null);
		/* @formatter:on */

		IType classB= b.getType("B");
		IField field1= classB.getField("a");
		IField field2= classB.getField("b");
		IField field3= classB.getField("c");
		IField field4= classB.getField("d");
		runAndApplyOperation(classB);

		/* @formatter:off */
		String expected= "public class B {\r\n" +
						"\r\n" +
						"	private String a;\r\n" +
						"	private String b;\r\n" +
						"	protected Other c;\r\n" +
						"	public String d;\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the a.\r\n" +
						"	 */\r\n" +
						"	public String getA() {\r\n" +
						"		return a;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @param a The a to set.\r\n" +
						"	 */\r\n" +
						"	public void setA(String a) {\r\n" +
						"		this.a = a;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the b.\r\n" +
						"	 */\r\n" +
						"	public String getB() {\r\n" +
						"		return b;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @param b The b to set.\r\n" +
						"	 */\r\n" +
						"	public void setB(String b) {\r\n" +
						"		this.b = b;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the c.\r\n" +
						"	 */\r\n" +
						"	public Other getC() {\r\n" +
						"		return c;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @param c The c to set.\r\n" +
						"	 */\r\n" +
						"	public void setC(Other c) {\r\n" +
						"		this.c = c;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the d.\r\n" +
						"	 */\r\n" +
						"	public String getD() {\r\n" +
						"		return d;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @param d The d to set.\r\n" +
						"	 */\r\n" +
						"	public void setD(String d) {\r\n" +
						"		this.d = d;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, classB.getSource());
	}

	/**
	 * Test getter/setter generation in anonymous type
	 *
	 * @throws Exception
	 */
	@Test
	public void test7() throws Exception {
		/* @formatter:off */
		ICompilationUnit b= fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"\r\n" +
				"	{\r\n" +
				"		B someAnon = new B() {\r\n" +
				"			\r\n" +
				"			A innerfield;\r\n" +
				"			\r\n" +
				"		};\r\n" +
				"	}\r\n" +
				"}", true, null);
		/* @formatter:on */

		IType anon = (IType) b.getElementAt(60); // This is the position of the constructor of the anonymous type
		runAndApplyOperation(anon);

		/* @formatter:off */
		String expected= "public class B {\r\n" +
						"\r\n" +
						"	{\r\n" +
						"		B someAnon = new B() {\r\n" +
						"			\r\n" +
						"			A innerfield;\r\n" +
						"\r\n" +
						"			/**\r\n" +
						"			 * @return Returns the innerfield.\r\n" +
						"			 */\r\n" +
						"			public A getInnerfield() {\r\n" +
						"				return innerfield;\r\n" +
						"			}\r\n" +
						"\r\n" +
						"			/**\r\n" +
						"			 * @param innerfield The innerfield to set.\r\n" +
						"			 */\r\n" +
						"			public void setInnerfield(A innerfield) {\r\n" +
						"				this.innerfield = innerfield;\r\n" +
						"			}\r\n" +
						"			\r\n" +
						"		};\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */
		compareSource(expected, b.getType("B").getSource());
	}

	/**
	 * Verify existing getters are not overwritten, and setters are created
	 *
	 * @throws Exception
	 */
	@Test
	public void test9() throws Exception {
		/* @formatter:off */
		ICompilationUnit b= fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"\r\n" +
				"	private Object o;\r\n" +
				"\r\n" +
				"	/**\r\n" +
				"	 * @return Returns the o.\r\n" +
				"	 */\r\n" +
				"	synchronized Object getO() {\r\n" +
				"		return o;\r\n" +
				"	}\r\n" +
				"}", true, null);
		/* @formatter:on */

		IType classB = b.getType("B");
		runAndApplyOperation(classB);

		/* @formatter:off */
		String expected= "public class B {\r\n" +
				"\r\n" +
				"	private Object o;\r\n" +
				"\r\n" +
				"	/**\r\n" +
				"	 * @return Returns the o.\r\n" +
				"	 */\r\n" +
				"	synchronized Object getO() {\r\n" +
				"		return o;\r\n" +
				"	}\r\n" +
				"\r\n" +
				"	/**\r\n" +
				"	 * @param o The o to set.\r\n" +
				"	 */\r\n" +
				"	public void setO(Object o) {\r\n" +
				"		this.o = o;\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, classB.getSource());
	}

	/**
	 * Verify existing setters are not overwritten, and getters are created
	 *
	 * @throws Exception
	 */
	@Test
	public void test10() throws Exception {
		/* @formatter:off */
		ICompilationUnit b= fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"\r\n" +
				"	private Object o;\r\n" +
				"\r\n" +
				"	/**\r\n" +
				"	 * @param o The o to set.\r\n" +
				"	 */\r\n" +
				"	synchronized void setO(Object o) {\r\n" +
				"		this.o = o;\r\n" +
				"	}\r\n" +
				"}", true, null);
		/* @formatter:on */

		IType classB = b.getType("B");
		runAndApplyOperation(classB);

		/* @formatter:off */
		String expected= "public class B {\r\n" +
				"\r\n" +
				"	private Object o;\r\n" +
				"\r\n" +
				"	/**\r\n" +
				"	 * @param o The o to set.\r\n" +
				"	 */\r\n" +
				"	synchronized void setO(Object o) {\r\n" +
				"		this.o = o;\r\n" +
				"	}\r\n" +
				"\r\n" +
				"	/**\r\n" +
				"	 * @return Returns the o.\r\n" +
				"	 */\r\n" +
				"	public Object getO() {\r\n" +
				"		return o;\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, classB.getSource());
	}

	@Test
	public void testNoGeneratorForInterface() throws OperationCanceledException, CoreException {
		/* @formatter:off */
		ICompilationUnit b = fPackageP.createCompilationUnit("C.java", "package p;\r\n" +
				"\r\n" +
				"public interface C {\r\n" +
				"\r\n" +
				"	Object o;\r\n" +
				"\r\n" +
				"}", true, null);
		/* @formatter:on */
		IType classC = b.getType("C");
		assertNull(runOperation(classC));
	}

	@Test
	public void testGeneratorForStatic() throws Exception {
		fClassA.createField("static String field1;", null, false, new NullProgressMonitor());
		runAndApplyOperation(fClassA);

		/* @formatter:off */
		String expected= "public class A {\r\n" +
						"\r\n" +
						"	static String field1;\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the field1.\r\n" +
						"	 */\r\n" +
						"	public static String getField1() {\r\n" +
						"		return field1;\r\n" +
						"	}\r\n" +
						"\r\n" +
						"	/**\r\n" +
						"	 * @param field1 The field1 to set.\r\n" +
						"	 */\r\n" +
						"	public static void setField1(String field1) {\r\n" +
						"		A.field1 = field1;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, fClassA.getSource());
	}

	@Test
	public void testWithoutGeneratingComments() throws Exception {
		IField field1 = fClassA.createField("String field1;", null, false, new NullProgressMonitor());
		runAndApplyOperation(fClassA, false);

		/* @formatter:off */
		String expected= "public class A {\r\n" +
						"\r\n" +
						"	String field1;\r\n" +
						"\r\n" +
						"	public String getField1() {\r\n" +
						"		return field1;\r\n" +
						"	}\r\n" +
						"\r\n" +
						"	public void setField1(String field1) {\r\n" +
						"		this.field1 = field1;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, fClassA.getSource());
	}
}
