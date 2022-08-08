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
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class SortMembersActionTest extends AbstractCompilationUnitBasedTest {
	@Mock
	private JavaClientConnection connection;
	private IJavaProject fJavaProject;
	private IPackageFragmentRoot fRoot;
	private IPackageFragment fPackageP;

	@Before
	@Override
	public void setup() throws Exception {
		fJavaProject = newEmptyProject();
		fRoot = fJavaProject.findPackageFragmentRoot(fJavaProject.getPath().append("src"));
		assertNotNull(fRoot);
		fPackageP = fRoot.createPackageFragment("p", true, null);
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server = new JDTLanguageServer(projectsManager, this.preferenceManager);
	}

	@Test
	public void testSortMemberActionExists() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private String name;\r\n" +
				"	private String getPrivateStr() { return \"private\"; }\r\n" +
				"	public String publicName;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> sortMemberAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_SORT_MEMBERS, "Sort Members for 'A.java'");
		Assert.assertNotNull(sortMemberAction);
		Command sortMemberCommand = CodeActionHandlerTest.getCommand(sortMemberAction);
		Assert.assertNotNull(sortMemberCommand);
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, sortMemberCommand.getCommand());
	}

	@Test
	public void testSortMemberActionNotExists() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private String name;\r\n" +
				"	public String publicName;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> sortMemberAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_SORT_MEMBERS, "Sort Members for 'A.java'");
		Assert.assertNull(sortMemberAction);
	}

	@Test
	public void testSortMemberActionExistsWithVolatileChanges() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private String name;\r\n" +
				"	public String publicName;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		this.preferenceManager.getPreferences().setAvoidVolatileChanges(false);
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> sortMemberAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_SORT_MEMBERS, "Sort Members for 'A.java'");
		Assert.assertNotNull(sortMemberAction);
		Command sortMemberCommand = CodeActionHandlerTest.getCommand(sortMemberAction);
		Assert.assertNotNull(sortMemberCommand);
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, sortMemberCommand.getCommand());
	}

	@Test
	public void testSortMemberQuickAssistExistsForTypeDeclaration() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private String name;\r\n" +
				"	private String getPrivateStr() { return \"private\"; }\r\n" +
				"	public String publicName;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "A");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> sortMemberQuickAssist = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.QUICK_ASSIST, "Sort Members for 'A.java'");
		Assert.assertNotNull(sortMemberQuickAssist);
		Command sortMemberCommand = CodeActionHandlerTest.getCommand(sortMemberQuickAssist);
		Assert.assertNotNull(sortMemberCommand);
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, sortMemberCommand.getCommand());
	}

	@Test
	public void testSortMemberQuickAssistExistsForSelection() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private String name;\r\n" +
				"	private String getPrivateStr() { return \"private\"; }\r\n" +
				"	public String publicName;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "private String getPrivateStr() { return \"private\"; }\r\n\tpublic String publicName;");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> sortMemberQuickAssist = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.QUICK_ASSIST, "Sort Selected Members");
		Assert.assertNotNull(sortMemberQuickAssist);
		Command sortMemberCommand = CodeActionHandlerTest.getCommand(sortMemberQuickAssist);
		Assert.assertNotNull(sortMemberCommand);
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, sortMemberCommand.getCommand());
	}
}
