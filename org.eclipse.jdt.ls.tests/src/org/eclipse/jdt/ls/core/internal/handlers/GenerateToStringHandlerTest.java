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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.GenerateToStringOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettingsCore;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettingsCore.CustomBuilderSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateToStringHandler.CheckToStringResponse;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.text.edits.TextEdit;
import org.junit.Test;

public class GenerateToStringHandlerTest extends AbstractSourceTestCase {
	@Test
	public void testGenerateToStringStatus() throws JavaModelException {
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
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		CheckToStringResponse response = GenerateToStringHandler.checkToStringStatus(params);
		assertEquals("B", response.type);
		assertNotNull(response.fields);
		assertEquals(2, response.fields.length);
		assertFalse(response.exists);
	}

	@Test
	public void testCheckToStringStatus_methodsExist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"	public String toString() {\r\n" +
				"		return \"B[]\";\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		CheckToStringResponse response = GenerateToStringHandler.checkToStringStatus(params);
		assertEquals("B", response.type);
		assertNotNull(response.fields);
		assertEquals(2, response.fields.length);
		assertTrue(response.exists);
	}

	@Test
	public void testGenerateToString() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"import java.util.List;\r\n\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"	List<String> aList;\r\n" +
				"	String[] arrays;\r\n" +
				"}"
				, true, null);
		//@formatter:on

		ToStringGenerationSettingsCore settings = new ToStringGenerationSettingsCore();
		settings.overrideAnnotation = true;
		settings.createComments = false;
		settings.useBlocks = false;
		settings.stringFormatTemplate = GenerateToStringHandler.DEFAULT_TEMPLATE;
		settings.toStringStyle = GenerateToStringOperation.STRING_CONCATENATION;
		settings.skipNulls = false;
		settings.customArrayToString = true;
		settings.limitElements = false;
		settings.customBuilderSettings = new CustomBuilderSettings();
		generateToString(unit.findPrimaryType(), settings);

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
				"	List<String> aList;\r\n" +
				"	String[] arrays;\r\n" +
				"	@Override\r\n" +
				"	public String toString() {\r\n" +
				"		return \"B [name=\" + name + \", id=\" + id + \", aList=\" + aList + \", arrays=\" + (arrays != null ? Arrays.asList(arrays) : null) + \"]\";\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}

	@Test
	public void testGenerateToStringOrder() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	public String stringField;\r\n" +
				"	public int intField;\r\n" +
				"	public static int staticIntField;\r\n" +
				"	public boolean booleanField;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String stringField");
		CheckToStringResponse response = GenerateToStringHandler.checkToStringStatus(params);
		assertEquals("B", response.type);
		assertNotNull(response.fields);
		assertEquals(3, response.fields.length);
		assertFalse(response.exists);
		assertEquals("stringField", response.fields[0].name);
		assertEquals("intField", response.fields[1].name);
		assertEquals("booleanField", response.fields[2].name);

		ToStringGenerationSettingsCore settings = new ToStringGenerationSettingsCore();
		settings.overrideAnnotation = true;
		settings.createComments = false;
		settings.useBlocks = false;
		settings.stringFormatTemplate = GenerateToStringHandler.DEFAULT_TEMPLATE;
		settings.toStringStyle = GenerateToStringOperation.STRING_CONCATENATION;
		settings.skipNulls = false;
		settings.customArrayToString = true;
		settings.limitElements = false;
		settings.customBuilderSettings = new CustomBuilderSettings();
		generateToString(unit.findPrimaryType(), settings);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	public String stringField;\r\n" +
				"	public int intField;\r\n" +
				"	public static int staticIntField;\r\n" +
				"	public boolean booleanField;\r\n" +
				"	@Override\r\n" +
				"	public String toString() {\r\n" +
				"		return \"B [stringField=\" + stringField + \", intField=\" + intField + \", booleanField=\" + booleanField + \"]\";\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}

	@Test
	public void testGenerateToString_customizedSettings() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"import java.util.List;\r\n\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"	List<String> aList;\r\n" +
				"	String[] arrays;\r\n" +
				"}"
				, true, null);
		//@formatter:on

		ToStringGenerationSettingsCore settings = new ToStringGenerationSettingsCore();
		settings.overrideAnnotation = true;
		settings.createComments = true;
		settings.useBlocks = true;
		settings.stringFormatTemplate = GenerateToStringHandler.DEFAULT_TEMPLATE;
		settings.toStringStyle = GenerateToStringOperation.STRING_BUILDER_CHAINED;
		settings.skipNulls = true;
		settings.customArrayToString = false;
		settings.limitElements = true;
		settings.limitValue = 10;
		settings.customBuilderSettings = new CustomBuilderSettings();
		settings.is50orHigher = true;
		settings.is60orHigher = true;
		generateToString(unit.findPrimaryType(), settings);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"import java.util.List;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private static String UUID = \"23434343\";\r\n" +
				"	String name;\r\n" +
				"	int id;\r\n" +
				"	List<String> aList;\r\n" +
				"	String[] arrays;\r\n" +
				"	@Override\r\n" +
				"	public String toString() {\r\n" +
				"		final int maxLen = 10;\r\n" +
				"		StringBuilder builder = new StringBuilder();\r\n" +
				"		builder.append(\"B [\");\r\n" +
				"		if (name != null) {\r\n" +
				"			builder.append(\"name=\").append(name).append(\", \");\r\n" +
				"		}\r\n" +
				"		builder.append(\"id=\").append(id).append(\", \");\r\n" +
				"		if (aList != null) {\r\n" +
				"			builder.append(\"aList=\").append(aList.subList(0, Math.min(aList.size(), maxLen))).append(\", \");\r\n" +
				"		}\r\n" +
				"		if (arrays != null) {\r\n" +
				"			builder.append(\"arrays=\").append(arrays);\r\n" +
				"		}\r\n" +
				"		builder.append(\"]\");\r\n" +
				"		return builder.toString();\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}

	@Test
	public void testGenerateToStringAfterCursorPosition() throws ValidateEditException, CoreException, IOException {
		String oldValue = preferences.getCodeGenerationInsertionLocation();
		try {
			preferences.setCodeGenerationInsertionLocation(CodeGenerationUtils.INSERT_AFTER_CURSOR);
			//@formatter:off
			ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
					"\r\n" +
					"import java.util.List;\r\n\r\n" +
					"public class B {\r\n" +
					"	private static String UUID = \"23434343\";\r\n" +
					"	String name;\r\n" +
					"	int id;\r\n" +
					"	List<String> aList;/*|*/\r\n" +
					"	String[] arrays;\r\n" +
					"}"
					, true, null);
			//@formatter:on

			ToStringGenerationSettingsCore settings = new ToStringGenerationSettingsCore();
			settings.overrideAnnotation = true;
			settings.createComments = false;
			settings.useBlocks = false;
			settings.stringFormatTemplate = GenerateToStringHandler.DEFAULT_TEMPLATE;
			settings.toStringStyle = GenerateToStringOperation.STRING_CONCATENATION;
			settings.skipNulls = false;
			settings.customArrayToString = true;
			settings.limitElements = false;
			settings.customBuilderSettings = new CustomBuilderSettings();
			Range cursor = CodeActionUtil.getRange(unit, "/*|*/");
			generateToString(unit.findPrimaryType(), settings, cursor);

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
					"	List<String> aList;/*|*/\r\n" +
					"	@Override\r\n" +
					"	public String toString() {\r\n" +
					"		return \"B [name=\" + name + \", id=\" + id + \", aList=\" + aList + \", arrays=\" + (arrays != null ? Arrays.asList(arrays) : null) + \"]\";\r\n" +
					"	}\r\n" +
					"	String[] arrays;\r\n" +
					"}";
			/* @formatter:on */

			compareSource(expected, unit.getSource());
		} finally {
			preferences.setCodeGenerationInsertionLocation(oldValue);
		}
	}

	@Test
	public void testGenerateToStringBeforeCursorPosition() throws ValidateEditException, CoreException, IOException {
		String oldValue = preferences.getCodeGenerationInsertionLocation();
		try {
			preferences.setCodeGenerationInsertionLocation(CodeGenerationUtils.INSERT_BEFORE_CURSOR);
			//@formatter:off
			ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
					"\r\n" +
					"import java.util.List;\r\n\r\n" +
					"public class B {\r\n" +
					"	private static String UUID = \"23434343\";\r\n" +
					"	String name;\r\n" +
					"	int id;\r\n" +
					"	List<String> aList;/*|*/\r\n" +
					"	String[] arrays;\r\n" +
					"}"
					, true, null);
			//@formatter:on

			ToStringGenerationSettingsCore settings = new ToStringGenerationSettingsCore();
			settings.overrideAnnotation = true;
			settings.createComments = false;
			settings.useBlocks = false;
			settings.stringFormatTemplate = GenerateToStringHandler.DEFAULT_TEMPLATE;
			settings.toStringStyle = GenerateToStringOperation.STRING_CONCATENATION;
			settings.skipNulls = false;
			settings.customArrayToString = true;
			settings.limitElements = false;
			settings.customBuilderSettings = new CustomBuilderSettings();
			Range cursor = CodeActionUtil.getRange(unit, "/*|*/");
			generateToString(unit.findPrimaryType(), settings, cursor);

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
					"	@Override\r\n" +
					"	public String toString() {\r\n" +
					"		return \"B [name=\" + name + \", id=\" + id + \", aList=\" + aList + \", arrays=\" + (arrays != null ? Arrays.asList(arrays) : null) + \"]\";\r\n" +
					"	}\r\n" +
					"	List<String> aList;/*|*/\r\n" +
					"	String[] arrays;\r\n" +
					"}";
			/* @formatter:on */

			compareSource(expected, unit.getSource());
		} finally {
			preferences.setCodeGenerationInsertionLocation(oldValue);
		}
	}

	private void generateToString(IType type, ToStringGenerationSettingsCore settings) throws ValidateEditException, CoreException {
		generateToString(type, settings, null);
	}

	private void generateToString(IType type, ToStringGenerationSettingsCore settings, Range cursor) throws ValidateEditException, CoreException {
		CheckToStringResponse response = GenerateToStringHandler.checkToStringStatus(type);
		// If cursor position is not specified, then insert to the last by default.
		IJavaElement insertPosition = CodeGenerationUtils.findInsertElement(type, cursor);
		TextEdit edit = GenerateToStringHandler.generateToString(type, response.fields, settings, insertPosition, new NullProgressMonitor());
		assertNotNull(edit);
		JavaModelUtil.applyEdit(type.getCompilationUnit(), edit, true, null);
	}
}
