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
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.GenerateToStringOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettingsCore;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettingsCore.CustomBuilderSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateToStringHandler.CheckToStringResponse;
import org.eclipse.lsp4j.CodeActionParams;
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
				"		return \"B [aList=\" + aList + \", arrays=\" + (arrays != null ? Arrays.asList(arrays) : null) + \", id=\" + id + \", name=\" + name + \"]\";\r\n" +
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
				"	/* (non-Javadoc)\r\n" +
				"	 * @see java.lang.Object#toString()\r\n" +
				"	 */\r\n" +
				"	\r\n" +
				"	@Override\r\n" +
				"	public String toString() {\r\n" +
				"		final int maxLen = 10;\r\n" +
				"		StringBuilder builder = new StringBuilder();\r\n" +
				"		builder.append(\"B [\");\r\n" +
				"		if (aList != null) {\r\n" +
				"			builder.append(\"aList=\").append(aList.subList(0, Math.min(aList.size(), maxLen))).append(\", \");\r\n" +
				"		}\r\n" +
				"		if (arrays != null) {\r\n" +
				"			builder.append(\"arrays=\").append(arrays).append(\", \");\r\n" +
				"		}\r\n" +
				"		builder.append(\"id=\").append(id).append(\", \");\r\n" +
				"		if (name != null) {\r\n" +
				"			builder.append(\"name=\").append(name);\r\n" +
				"		}\r\n" +
				"		builder.append(\"]\");\r\n" +
				"		return builder.toString();\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}

	private void generateToString(IType type, ToStringGenerationSettingsCore settings) throws ValidateEditException, CoreException {
		CheckToStringResponse response = GenerateToStringHandler.checkToStringStatus(type);
		TextEdit edit = GenerateToStringHandler.generateToString(type, response.fields, settings);
		assertNotNull(edit);
		JavaModelUtil.applyEdit(type.getCompilationUnit(), edit, true, null);
	}
}
