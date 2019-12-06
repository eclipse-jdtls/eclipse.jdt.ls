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
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GenerateToStringActionTest extends AbstractCompilationUnitBasedTest {
	@Mock
	private JavaClientConnection connection;
	private IJavaProject fJavaProject;
	private IPackageFragmentRoot fRoot;
	private IPackageFragment fPackageP;

	@Before
	public void setUp() throws Exception {
		fJavaProject = newEmptyProject();
		fRoot = fJavaProject.findPackageFragmentRoot(fJavaProject.getPath().append("src"));
		assertNotNull(fRoot);
		fPackageP = fRoot.createPackageFragment("p", true, null);
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server = new JDTLanguageServer(projectsManager, this.preferenceManager);
	}

	@Test
	public void testGenerateToStringEnabled() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	String name;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> toStringAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_TO_STRING);
		Assert.assertNotNull(toStringAction);
		Command toStringCommand = CodeActionHandlerTest.getCommand(toStringAction);
		Assert.assertNotNull(toStringCommand);
		Assert.assertEquals(SourceAssistProcessor.COMMAND_ID_ACTION_GENERATETOSTRINGPROMPT, toStringCommand.getCommand());
	}

	@Test
	public void testGenerateToStringEnabled_emptyFields() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	private static String name;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> toStringAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_TO_STRING);
		Assert.assertNotNull(toStringAction);
		Command toStringCommand = CodeActionHandlerTest.getCommand(toStringAction);
		Assert.assertNotNull(toStringCommand);
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, toStringCommand.getCommand());
	}

	@Test
	public void testGenerateToStringDisabled_interface() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public interface A {\r\n" +
				"	public final String name = \"test\";\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertFalse("The operation is not applicable to interfaces", CodeActionHandlerTest.containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_TO_STRING));
	}

	@Test
	public void testGenerateToStringDisabled_enum() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public enum A {\r\n" +
				"	MONDAY,\r\n" +
				"	TUESDAY;\r\n" +
				"	private String name;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Assert.assertFalse("The operation is not applicable to enums", CodeActionHandlerTest.containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_TO_STRING));
	}
}
