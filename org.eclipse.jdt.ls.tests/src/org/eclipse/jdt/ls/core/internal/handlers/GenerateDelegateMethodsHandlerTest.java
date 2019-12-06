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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateDelegateMethodsHandler.CheckDelegateMethodsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateDelegateMethodsHandler.LspDelegateEntry;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateDelegateMethodsHandler.LspDelegateField;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.text.edits.TextEdit;
import org.junit.Test;

public class GenerateDelegateMethodsHandlerTest extends AbstractSourceTestCase {
	@Test
	public void testCheckDelegateMethodsStatus() throws JavaModelException {
		//@formatter:off
		fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private String name;\r\n" +
				"	public String getName() {\r\n" +
				"		return this.name;\r\n" +
				"	}\r\n" +
				"	public void setName(String name) {\r\n" +
				"		this.name = name;\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit = fPackageP.createCompilationUnit("C.java", "package p;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"	private int id;\r\n" +
				"	private int B[] array;\r\n" +
				"	private B b;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "B b;");
		CheckDelegateMethodsResponse response = GenerateDelegateMethodsHandler.checkDelegateMethodsStatus(params);
		assertNotNull(response.delegateFields);
		assertEquals(1, response.delegateFields.length);
		LspDelegateField delegateField = response.delegateFields[0];
		assertEquals("b", delegateField.field.name);
		assertNotNull(delegateField.delegateMethods);
		assertEquals(5, delegateField.delegateMethods.length);
		assertEquals("getName", delegateField.delegateMethods[0].name);
		assertEquals("setName", delegateField.delegateMethods[1].name);
		assertEquals("equals", delegateField.delegateMethods[2].name);
		assertEquals("hashCode", delegateField.delegateMethods[3].name);
		assertEquals("toString", delegateField.delegateMethods[4].name);
	}

	@Test
	public void testCheckDelegateMethodsStatus_excludeExists() throws JavaModelException {
		//@formatter:off
		fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private String name;\r\n" +
				"	public String getName() {\r\n" +
				"		return this.name;\r\n" +
				"	}\r\n" +
				"	public void setName(String name) {\r\n" +
				"		this.name = name;\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit = fPackageP.createCompilationUnit("C.java", "package p;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"	private B b;\r\n" +
				"	public String getName() {\r\n" +
				"		return b.getName();\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "B b;");
		CheckDelegateMethodsResponse response = GenerateDelegateMethodsHandler.checkDelegateMethodsStatus(params);
		assertNotNull(response.delegateFields);
		assertEquals(1, response.delegateFields.length);
		LspDelegateField delegateField = response.delegateFields[0];
		assertEquals("b", delegateField.field.name);
		assertNotNull(delegateField.delegateMethods);
		assertEquals(4, delegateField.delegateMethods.length);
		assertEquals("setName", delegateField.delegateMethods[0].name);
	}

	@Test
	public void testGenerateDelegateMethods() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	private String name;\r\n" +
				"	public String getName() {\r\n" +
				"		return this.name;\r\n" +
				"	}\r\n" +
				"	public void setName(String name) {\r\n" +
				"		this.name = name;\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit = fPackageP.createCompilationUnit("C.java", "package p;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"	private int id;\r\n" +
				"	private int B[] array;\r\n" +
				"	private B b;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "B b;");
		CheckDelegateMethodsResponse response = GenerateDelegateMethodsHandler.checkDelegateMethodsStatus(params);
		assertNotNull(response.delegateFields);
		assertEquals(1, response.delegateFields.length);
		LspDelegateField delegateField = response.delegateFields[0];
		assertEquals("b", delegateField.field.name);
		assertNotNull(delegateField.delegateMethods);
		assertEquals(5, delegateField.delegateMethods.length);
		assertEquals("getName", delegateField.delegateMethods[0].name);
		assertEquals("setName", delegateField.delegateMethods[1].name);

		List<LspDelegateEntry> entries = new ArrayList<>();
		entries.add(new LspDelegateEntry(delegateField.field, delegateField.delegateMethods[0]));
		entries.add(new LspDelegateEntry(delegateField.field, delegateField.delegateMethods[1]));
		CodeGenerationSettings settings = new CodeGenerationSettings();
		settings.createComments = false;
		TextEdit edit = GenerateDelegateMethodsHandler.generateDelegateMethods(unit.findPrimaryType(), entries.toArray(new LspDelegateEntry[0]), settings);
		assertNotNull(edit);
		JavaModelUtil.applyEdit(unit, edit, true, null);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"	private int id;\r\n" +
				"	private int B[] array;\r\n" +
				"	private B b;\r\n" +
				"	public String getName() {\r\n" +
				"		return b.getName();\r\n" +
				"	}\r\n" +
				"	public void setName(String name) {\r\n" +
				"		b.setName(name);\r\n" +
				"	}\r\n" +
				"}";
		/* @formatter:on */

		compareSource(expected, unit.getSource());
	}
}
