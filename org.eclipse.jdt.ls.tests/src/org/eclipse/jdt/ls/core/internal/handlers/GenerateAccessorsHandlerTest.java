/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation.AccessorField;
import org.eclipse.text.edits.TextEdit;
import org.junit.Test;

public class GenerateAccessorsHandlerTest extends AbstractSourceTestCase {
	@Test
	public void testResolveUnimplementedAccessors() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String staticField = \"23434343\";\r\n" +
				"	private final String finalField;\r\n" +
				"	String name;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		AccessorField[] accessors = GenerateAccessorsHandler.getUnimplementedAccessors(unit.findPrimaryType());
		assertNotNull(accessors);
		assertEquals(3, accessors.length);
		assertEquals("staticField", accessors[0].fieldName);
		assertTrue(accessors[0].isStatic);
		assertTrue(accessors[0].generateGetter && accessors[0].generateSetter);
		assertEquals("finalField", accessors[1].fieldName);
		assertFalse(accessors[1].isStatic);
		assertTrue(accessors[1].generateGetter);
		assertFalse(accessors[1].generateSetter);
		assertEquals("name", accessors[2].fieldName);
		assertFalse(accessors[2].isStatic);
		assertTrue(accessors[2].generateGetter);
		assertTrue(accessors[2].generateSetter);
	}

	@Test
	public void testResolveUnimplementedAccessors_methodsExist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"	public String getName() {\r\n" +
				"		return name;\r\n" +
				"	}\r\n" +
				"	public void setName(String name) {\r\n" +
				"		this.name = name;\r\n" +
				"	}\r\n" +
				"	public int getId() {\r\n" +
				"		return id;\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		AccessorField[] accessors = GenerateAccessorsHandler.getUnimplementedAccessors(unit.findPrimaryType());
		assertNotNull(accessors);
		assertEquals(1, accessors.length);
		assertEquals("id", accessors[0].fieldName);
		assertFalse(accessors[0].generateGetter);
		assertTrue(accessors[0].generateSetter);
	}

	@Test
	public void testGenerateAccessors() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String staticField = \"23434343\";\r\n" +
				"	private final String finalField;\r\n" +
				"	String name;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		IType classB = unit.getType("B");
		generateAccessors(classB);

		/* @formatter:off */
		String expected = "public class B {\r\n" +
						"	private static String staticField = \"23434343\";\r\n" +
						"	private final String finalField;\r\n" +
						"	String name;\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the staticField.\r\n" +
						"	 */\r\n" +
						"	public static String getStaticField() {\r\n" +
						"		return staticField;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @param staticField The staticField to set.\r\n" +
						"	 */\r\n" +
						"	public static void setStaticField(String staticField) {\r\n" +
						"		B.staticField = staticField;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the finalField.\r\n" +
						"	 */\r\n" +
						"	public String getFinalField() {\r\n" +
						"		return finalField;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @return Returns the name.\r\n" +
						"	 */\r\n" +
						"	public String getName() {\r\n" +
						"		return name;\r\n" +
						"	}\r\n" +
						"	/**\r\n" +
						"	 * @param name The name to set.\r\n" +
						"	 */\r\n" +
						"	public void setName(String name) {\r\n" +
						"		this.name = name;\r\n" +
						"	}\r\n" +
						"}";
		/* @formatter:on */

		compareSource(expected, classB.getSource());
	}

	private void generateAccessors(IType type) throws ValidateEditException, CoreException {
		AccessorField[] accessors = GenerateAccessorsHandler.getUnimplementedAccessors(type);
		TextEdit edit = GenerateAccessorsHandler.generateAccessors(type, accessors, true);
		assertNotNull(edit);
		JavaModelUtil.applyEdit(type.getCompilationUnit(), edit, true, null);
	}
}
