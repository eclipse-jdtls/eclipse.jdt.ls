/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.handlers.HashCodeEqualsHandler.CheckHashCodeEqualsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.HashCodeEqualsHandler.GenerateHashCodeEqualsParams;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.text.edits.TextEdit;
import org.junit.Test;

public class HashCodeEqualsHandlerTest extends AbstractSourceTestCase {

	@Test
	public void testCheckHashCodeEqualsStatus() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = HashCodeEqualsActionTest.constructCodeActionParams(unit, "String name");
		CheckHashCodeEqualsResponse response = HashCodeEqualsHandler.checkHashCodeEqualsStatus(params);
		assertEquals("B", response.type);
		assertNotNull(response.fields);
		assertEquals(2, response.fields.length);
		assertTrue(response.existingMethods == null || response.existingMethods.length == 0);
	}

	@Test
	public void testCheckHashCodeEqualsStatus_methodsExist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"   public int hashCode() {\r\n" +
				"	}\r\n" +
				"	public boolean equals(Object a) {\r\n" +
				"		return true;\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = HashCodeEqualsActionTest.constructCodeActionParams(unit, "String name");
		CheckHashCodeEqualsResponse response = HashCodeEqualsHandler.checkHashCodeEqualsStatus(params);
		assertEquals("B", response.type);
		assertNotNull(response.fields);
		assertEquals(2, response.fields.length);
		assertNotNull(response.existingMethods);
		assertEquals(2, response.existingMethods.length);
	}

	@Test
	public void testGenerateHashCodeEquals() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"import java.util.List;\r\n\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"	double rate;\r\n" +
				"	Cloneable[] anArray;\r\n" +
				"	List<String> aList;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = HashCodeEqualsActionTest.constructCodeActionParams(unit, "String name");
		CheckHashCodeEqualsResponse response = HashCodeEqualsHandler.checkHashCodeEqualsStatus(params);
		GenerateHashCodeEqualsParams genParams = new GenerateHashCodeEqualsParams();
		genParams.context = params;
		genParams.fields = response.fields;
		genParams.regenerate = false;
		TextEdit edit = HashCodeEqualsHandler.generateHashCodeEqualsTextEdit(genParams, false);
		assertNotNull(edit);
		JavaModelUtil.applyEdit(unit, edit, true, null);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"import java.util.Arrays;\r\n" +
				"import java.util.List;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"	double rate;\r\n" +
				"	Cloneable[] anArray;\r\n" +
				"	List<String> aList;\r\n" +
				"	@Override\r\n" +
				"	public int hashCode() {\r\n" +
				"		final int prime = 31;\r\n" +
				"		int result = 1;\r\n" +
				"		result = prime * result + ((aList == null) ? 0 : aList.hashCode());\r\n" +
				"		result = prime * result + Arrays.deepHashCode(anArray);\r\n" +
				"		result = prime * result + id;\r\n" +
				"		result = prime * result + ((name == null) ? 0 : name.hashCode());\r\n" +
				"		long temp;\r\n" +
				"		temp = Double.doubleToLongBits(rate);\r\n" +
				"		result = prime * result + (int) (temp ^ (temp >>> 32));\r\n" +
				"		return result;\r\n" +
				"	}\r\n" +
				"	@Override\r\n" +
				"	public boolean equals(Object obj) {\r\n" +
				"		if (this == obj)\r\n" +
				"			return true;\r\n" +
				"		if (obj == null)\r\n" +
				"			return false;\r\n" +
				"		if (getClass() != obj.getClass())\r\n" +
				"			return false;\r\n" +
				"		B other = (B) obj;\r\n" +
				"		if (aList == null) {\r\n" +
				"			if (other.aList != null)\r\n" +
				"				return false;\r\n" +
				"		} else if (!aList.equals(other.aList))\r\n" +
				"			return false;\r\n" +
				"		if (!Arrays.deepEquals(anArray, other.anArray))\r\n" +
				"			return false;\r\n" +
				"		if (id != other.id)\r\n" +
				"			return false;\r\n" +
				"		if (name == null) {\r\n" +
				"			if (other.name != null)\r\n" +
				"				return false;\r\n" +
				"		} else if (!name.equals(other.name))\r\n" +
				"			return false;\r\n" +
				"		if (Double.doubleToLongBits(rate) != Double.doubleToLongBits(other.rate))\r\n" +
				"			return false;\r\n" +
				"		return true;\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}

	@Test
	public void testGenerateHashCodeEquals_J7() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"import java.util.List;\r\n\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"	double rate;\r\n" +
				"	Cloneable[] anArray;\r\n" +
				"	List<String> aList;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = HashCodeEqualsActionTest.constructCodeActionParams(unit, "String name");
		CheckHashCodeEqualsResponse response = HashCodeEqualsHandler.checkHashCodeEqualsStatus(params);
		GenerateHashCodeEqualsParams genParams = new GenerateHashCodeEqualsParams();
		genParams.context = params;
		genParams.fields = response.fields;
		genParams.regenerate = false;
		TextEdit edit = HashCodeEqualsHandler.generateHashCodeEqualsTextEdit(genParams, true);
		assertNotNull(edit);
		JavaModelUtil.applyEdit(unit, edit, true, null);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"import java.util.Arrays;\r\n" +
				"import java.util.List;\r\n" +
				"import java.util.Objects;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"	double rate;\r\n" +
				"	Cloneable[] anArray;\r\n" +
				"	List<String> aList;\r\n" +
				"	@Override\r\n" +
				"	public int hashCode() {\r\n" +
				"		final int prime = 31;\r\n" +
				"		int result = 1;\r\n" +
				"		result = prime * result + Arrays.deepHashCode(anArray);\r\n" +
				"		result = prime * result + Objects.hash(aList, id, name, rate);\r\n" +
				"		return result;\r\n" +
				"	}\r\n" +
				"	@Override\r\n" +
				"	public boolean equals(Object obj) {\r\n" +
				"		if (this == obj)\r\n" +
				"			return true;\r\n" +
				"		if (obj == null)\r\n" +
				"			return false;\r\n" +
				"		if (getClass() != obj.getClass())\r\n" +
				"			return false;\r\n" +
				"		B other = (B) obj;\r\n" +
				"		return Objects.equals(aList, other.aList) && Arrays.deepEquals(anArray, other.anArray) && id == other.id "
				+ "&& Objects.equals(name, other.name) && Double.doubleToLongBits(rate) == Double.doubleToLongBits(other.rate);\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}

	@Test
	public void testGenerateHashCodeEquals_regenerate() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	public int hashCode() {\r\n" +
				"		return 0;\r\n" +
				"	}\r\n" +
				"	public boolean equals(Object obj) {\r\n" +
				"		return false;\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = HashCodeEqualsActionTest.constructCodeActionParams(unit, "String name");
		CheckHashCodeEqualsResponse response = HashCodeEqualsHandler.checkHashCodeEqualsStatus(params);
		GenerateHashCodeEqualsParams genParams = new GenerateHashCodeEqualsParams();
		genParams.context = params;
		genParams.fields = response.fields;
		genParams.regenerate = true;
		TextEdit edit = HashCodeEqualsHandler.generateHashCodeEqualsTextEdit(genParams, false);
		assertNotNull(edit);
		JavaModelUtil.applyEdit(unit, edit, true, null);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	@Override\r\n" +
				"	public int hashCode() {\r\n" +
				"		final int prime = 31;\r\n" +
				"		int result = 1;\r\n" +
				"		result = prime * result + ((name == null) ? 0 : name.hashCode());\r\n" +
				"		return result;\r\n" +
				"	}\r\n" +
				"	@Override\r\n" +
				"	public boolean equals(Object obj) {\r\n" +
				"		if (this == obj)\r\n" +
				"			return true;\r\n" +
				"		if (obj == null)\r\n" +
				"			return false;\r\n" +
				"		if (getClass() != obj.getClass())\r\n" +
				"			return false;\r\n" +
				"		B other = (B) obj;\r\n" +
				"		if (name == null) {\r\n" +
				"			if (other.name != null)\r\n" +
				"				return false;\r\n" +
				"		} else if (!name.equals(other.name))\r\n" +
				"			return false;\r\n" +
				"		return true;\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}
}
