/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GenerateFinalModifiersQuickAssistTest extends AbstractCompilationUnitBasedTest {

	private IJavaProject fJavaProject;
	private IPackageFragmentRoot fRoot;
	private IPackageFragment fPackageP;

	@Override
	@Before
	public void setup() throws Exception {
		fJavaProject = newEmptyProject();
		fRoot = fJavaProject.findPackageFragmentRoot(fJavaProject.getPath().append("src"));
		assertNotNull(fRoot);
		fPackageP = fRoot.createPackageFragment("p", true, null);
		server = new JDTLanguageServer(projectsManager, this.preferenceManager);
	}

	@Test
	public void testInsertFinalModifierForFieldDeclarationQuickAssist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private String name = \"a\";\r\n" +
				"	private String test;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Add final modifier for 'name'"));
		params = CodeActionUtil.constructCodeActionParams(unit, "a\";");
		codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Add final modifier for 'name'"));
		params = CodeActionUtil.constructCodeActionParams(unit, "test;");
		codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Add final modifier for 'test'"));
	}

	@Test
	public void testInsertFinalModifierForFieldDeclarationsQuickAssist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private String name;\r\n" +
				"	private String name1 = \"b\";\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "private String name;\r\n	private String name1 = \"b\";");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Change modifiers to final"));
	}

	@Test
	public void testInsertFinalModifierForParameterQuickAssist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public String getName(String a, String b) {}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "b");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Add final modifier for 'b'"));
		params = CodeActionUtil.constructCodeActionParams(unit, "String a, String b");
		codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Change modifiers to final"));
	}

	@Test
	public void testInsertFinalModifierForLocalVariableSelectionQuickAssist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public String getName(String a, String b) {\r\n" +
				"		String c = a;\r\n" +
				"		String d = b;\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "c");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Add final modifier for 'c'"));
		params = CodeActionUtil.constructCodeActionParams(unit, "String c = a;\r\n		String d = b;");
		codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Change modifiers to final"));
		params = CodeActionUtil.constructCodeActionParams(unit, "c = a;\r\n		String d = b;");
		codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Change modifiers to final"));
	}

	@Test
	public void testInsertFinalModifierForLocalVariableQuickAssist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public String getName() {\r\n" +
				"		String c;\r\n" +
				"		String d, e;\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "c;");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Add final modifier for 'c'"));
		params = CodeActionUtil.constructCodeActionParams(unit, "String d, e;");
		codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Change modifiers to final"));
		params = CodeActionUtil.constructCodeActionParams(unit, "d");
		codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(codeActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Add final modifier for 'd'"));
	}
}
