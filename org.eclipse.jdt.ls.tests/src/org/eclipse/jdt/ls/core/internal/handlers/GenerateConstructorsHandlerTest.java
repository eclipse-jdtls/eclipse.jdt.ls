/*******************************************************************************
 * Copyright (c) 2019-2021 Microsoft Corporation and others.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateConstructorsHandler.CheckConstructorsResponse;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.text.edits.TextEdit;
import org.junit.Test;

public class GenerateConstructorsHandlerTest extends AbstractSourceTestCase {
	@Test
	public void testCheckConstructorStatus() throws JavaModelException {
		//@formatter:off
		fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	public B(String name) {\r\n" +
				"	}\r\n" +
				"	public B(String name, int id) {\r\n" +
				"	}\r\n" +
				"	private B() {\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit = fPackageP.createCompilationUnit("C.java", "package p;\r\n" +
				"\r\n" +
				"public class C extends B {\r\n" +
				"	private static String logger;\r\n" +
				"	private final String uuid = \"123\";\r\n" +
				"	private final String instance;\r\n" +
				"	private String address;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String address");
		CheckConstructorsResponse response = GenerateConstructorsHandler.checkConstructorsStatus(params);
		assertNotNull(response.constructors);
		assertEquals(2, response.constructors.length);
		assertEquals("B", response.constructors[0].name);
		assertTrue(Arrays.equals(new String[] { "String" }, response.constructors[0].parameters));
		assertEquals("B", response.constructors[1].name);
		assertTrue(Arrays.equals(new String[] { "String", "int" }, response.constructors[1].parameters));
		assertNotNull(response.fields);
		assertEquals(2, response.fields.length);
		assertEquals("instance", response.fields[0].name);
		assertEquals("String", response.fields[0].type);
		assertEquals(false, response.fields[0].isSelected);
		assertEquals("address", response.fields[1].name);
		assertEquals("String", response.fields[1].type);
		assertEquals(true, response.fields[1].isSelected);
	}

	@Test
	public void testCheckConstructorStatus_enum() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public enum B {\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "enum B");
		CheckConstructorsResponse response = GenerateConstructorsHandler.checkConstructorsStatus(params);
		assertNotNull(response.constructors);
		assertEquals(1, response.constructors.length);
		assertEquals("Object", response.constructors[0].name);
		assertNotNull(response.constructors[0].parameters);
		assertEquals(0, response.constructors[0].parameters.length);
		assertNotNull(response.fields);
		assertEquals(0, response.fields.length);
	}

	@Test
	public void testGenerateConstructors() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	public B(String name) {\r\n" +
				"	}\r\n" +
				"	public B(String name, int id) {\r\n" +
				"	}\r\n" +
				"	private B() {\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit = fPackageP.createCompilationUnit("C.java", "package p;\r\n" +
				"\r\n" +
				"public class C extends B {\r\n" +
				"	private static String logger;\r\n" +
				"	private final String uuid = \"123\";\r\n" +
				"	private final String instance;\r\n" +
				"	private String address;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String address");
		CheckConstructorsResponse response = GenerateConstructorsHandler.checkConstructorsStatus(params);
		assertNotNull(response.constructors);
		assertEquals(2, response.constructors.length);
		assertNotNull(response.fields);
		assertEquals(2, response.fields.length);

		CodeGenerationSettings settings = new CodeGenerationSettings();
		settings.createComments = false;
		TextEdit edit = GenerateConstructorsHandler.generateConstructors(unit.findPrimaryType(), response.constructors, response.fields, settings, null, new NullProgressMonitor());
		assertNotNull(edit);
		JavaModelUtil.applyEdit(unit, edit, true, null);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"public class C extends B {\r\n" +
				"	private static String logger;\r\n" +
				"	private final String uuid = \"123\";\r\n" +
				"	private final String instance;\r\n" +
				"	private String address;\r\n" +
				"	public C(String name, String instance, String address) {\r\n" +
				"		super(name);\r\n" +
				"		this.instance = instance;\r\n" +
				"		this.address = address;\r\n" +
				"	}\r\n" +
				"	public C(String name, int id, String instance, String address) {\r\n" +
				"		super(name, id);\r\n" +
				"		this.instance = instance;\r\n" +
				"		this.address = address;\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}

	@Test
	public void testGenerateConstructors_enum() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public enum B {\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "enum B");
		CheckConstructorsResponse response = GenerateConstructorsHandler.checkConstructorsStatus(params);
		assertNotNull(response.constructors);
		assertEquals(1, response.constructors.length);
		assertNotNull(response.fields);
		assertEquals(0, response.fields.length);

		CodeGenerationSettings settings = new CodeGenerationSettings();
		settings.createComments = false;
		TextEdit edit = GenerateConstructorsHandler.generateConstructors(unit.findPrimaryType(), response.constructors, response.fields, settings, null, new NullProgressMonitor());
		assertNotNull(edit);
		JavaModelUtil.applyEdit(unit, edit, true, null);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"public enum B {\r\n" +
				"	;\r\n" +
				"\r\n" +
				"	private B() {\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}

	@Test
	public void testGenerateConstructorsAfterCursorPosition() throws ValidateEditException, CoreException, IOException {
		String oldValue = preferences.getCodeGenerationInsertionLocation();
		try {
			preferences.setCodeGenerationInsertionLocation(CodeGenerationUtils.INSERT_AFTER_CURSOR);
			//@formatter:off
			fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
					"\r\n" +
					"public class B {\r\n" +
					"	public B(String name) {\r\n" +
					"	}\r\n" +
					"	public B(String name, int id) {\r\n" +
					"	}\r\n" +
					"	private B() {\r\n" +
					"	}\r\n" +
					"}"
					, true, null);
			ICompilationUnit unit = fPackageP.createCompilationUnit("C.java", "package p;\r\n" +
					"\r\n" +
					"public class C extends B {\r\n" +
					"	private static String logger;\r\n" +
					"	private final String uuid = \"123\";\r\n" +
					"	private final String instance;/*|*/\r\n" +
					"	private String address;\r\n" +
					"}"
					, true, null);
			//@formatter:on
			CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String address");
			CheckConstructorsResponse response = GenerateConstructorsHandler.checkConstructorsStatus(params);
			assertNotNull(response.constructors);
			assertEquals(2, response.constructors.length);
			assertNotNull(response.fields);
			assertEquals(2, response.fields.length);

			CodeGenerationSettings settings = new CodeGenerationSettings();
			settings.createComments = false;
			Range cursor = CodeActionUtil.getRange(unit, "/*|*/");
			TextEdit edit = GenerateConstructorsHandler.generateConstructors(unit.findPrimaryType(), response.constructors, response.fields, settings, cursor, new NullProgressMonitor());
			assertNotNull(edit);
			JavaModelUtil.applyEdit(unit, edit, true, null);

			/* @formatter:off */
			String expected = "package p;\r\n" +
					"\r\n" +
					"public class C extends B {\r\n" +
					"	private static String logger;\r\n" +
					"	private final String uuid = \"123\";\r\n" +
					"	private final String instance;/*|*/\r\n" +
					"	public C(String name, String instance, String address) {\r\n" +
					"		super(name);\r\n" +
					"		this.instance = instance;\r\n" +
					"		this.address = address;\r\n" +
					"	}\r\n" +
					"	public C(String name, int id, String instance, String address) {\r\n" +
					"		super(name, id);\r\n" +
					"		this.instance = instance;\r\n" +
					"		this.address = address;\r\n" +
					"	}\r\n" +
					"	private String address;\r\n" +
					"}";
			/* @formatter:on */

			compareSource(expected, unit.getSource());
		} finally {
			preferences.setCodeGenerationInsertionLocation(oldValue);
		}
	}

	@Test
	public void testGenerateConstructorsBeforeCursorPosition() throws ValidateEditException, CoreException, IOException {
		String oldValue = preferences.getCodeGenerationInsertionLocation();
		try {
			preferences.setCodeGenerationInsertionLocation(CodeGenerationUtils.INSERT_BEFORE_CURSOR);
			//@formatter:off
			fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
					"\r\n" +
					"public class B {\r\n" +
					"	public B(String name) {\r\n" +
					"	}\r\n" +
					"	public B(String name, int id) {\r\n" +
					"	}\r\n" +
					"	private B() {\r\n" +
					"	}\r\n" +
					"}"
					, true, null);
			ICompilationUnit unit = fPackageP.createCompilationUnit("C.java", "package p;\r\n" +
					"\r\n" +
					"public class C extends B {\r\n" +
					"	private static String logger;\r\n" +
					"	private final String uuid = \"123\";\r\n" +
					"	private final String instance;/*|*/\r\n" +
					"	private String address;\r\n" +
					"}"
					, true, null);
			//@formatter:on
			CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String address");
			CheckConstructorsResponse response = GenerateConstructorsHandler.checkConstructorsStatus(params);
			assertNotNull(response.constructors);
			assertEquals(2, response.constructors.length);
			assertNotNull(response.fields);
			assertEquals(2, response.fields.length);

			CodeGenerationSettings settings = new CodeGenerationSettings();
			settings.createComments = false;
			Range cursor = CodeActionUtil.getRange(unit, "/*|*/");
			TextEdit edit = GenerateConstructorsHandler.generateConstructors(unit.findPrimaryType(), response.constructors, response.fields, settings, cursor, new NullProgressMonitor());
			assertNotNull(edit);
			JavaModelUtil.applyEdit(unit, edit, true, null);

			/* @formatter:off */
			String expected = "package p;\r\n" +
					"\r\n" +
					"public class C extends B {\r\n" +
					"	private static String logger;\r\n" +
					"	private final String uuid = \"123\";\r\n" +
					"	public C(String name, String instance, String address) {\r\n" +
					"		super(name);\r\n" +
					"		this.instance = instance;\r\n" +
					"		this.address = address;\r\n" +
					"	}\r\n" +
					"	public C(String name, int id, String instance, String address) {\r\n" +
					"		super(name, id);\r\n" +
					"		this.instance = instance;\r\n" +
					"		this.address = address;\r\n" +
					"	}\r\n" +
					"	private final String instance;/*|*/\r\n" +
					"	private String address;\r\n" +
					"}";
			/* @formatter:on */

			compareSource(expected, unit.getSource());
		} finally {
			preferences.setCodeGenerationInsertionLocation(oldValue);
		}
	}
}
