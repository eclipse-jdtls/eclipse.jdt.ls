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

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
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
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GenerateAccessorsActionTest extends AbstractCompilationUnitBasedTest {
	@Mock
	private JavaClientConnection connection;
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
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server = new JDTLanguageServer(projectsManager, this.preferenceManager);
	}

	@Test
	public void testGenerateAccessorsEnabled() throws JavaModelException {
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
		Either<Command, CodeAction> generateAccessorsAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS);
		Assert.assertNotNull(generateAccessorsAction);
		Command generateAccessorsCommand = CodeActionHandlerTest.getCommand(generateAccessorsAction);
		Assert.assertNotNull(generateAccessorsCommand);
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, generateAccessorsCommand.getCommand());
	}

	@Test
	public void testAdvancedGenerateAccessorsEnabled() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	static String name;\r\n" +
				"	String address;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> generateAccessorsAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS);
		Assert.assertNotNull(generateAccessorsAction);
		Command generateAccessorsCommand = CodeActionHandlerTest.getCommand(generateAccessorsAction);
		Assert.assertNotNull(generateAccessorsCommand);
		Assert.assertEquals(SourceAssistProcessor.COMMAND_ID_ACTION_GENERATEACCESSORSPROMPT, generateAccessorsCommand.getCommand());
	}

	@Test
	public void testGenerateAccessorsDisabled_emptyFields() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "class A");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> generateAccessorsAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS);
		Assert.assertNull(generateAccessorsAction);
	}

	@Test
	public void testGenerateAccessorsDisabled_interface() throws JavaModelException {
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
		Assert.assertFalse("The operation is not applicable to interfaces", CodeActionHandlerTest.containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS));
	}

	@Test
	public void testGenerateAccessorsForRecordEnabled() throws Exception {
		importProjects("eclipse/java16");
		IProject project = WorkspaceHelper.getProject("java16");
		IJavaProject javaProject = JavaCore.create(project);
		IPackageFragmentRoot root = javaProject.findPackageFragmentRoot(javaProject.getPath().append("src").append("main").append("java"));
		assertNotNull(root);
		IPackageFragment packageFragment = root.createPackageFragment("p", true, null);
		//@formatter:off
		ICompilationUnit unit = packageFragment.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public record A(String name, int age) {\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "A");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> generateAccessorsAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS);
		Assert.assertNotNull(generateAccessorsAction);
		Command generateAccessorsCommand = CodeActionHandlerTest.getCommand(generateAccessorsAction);
		Assert.assertNotNull(generateAccessorsCommand);
		Assert.assertEquals(SourceAssistProcessor.COMMAND_ID_ACTION_GENERATEACCESSORSPROMPT, generateAccessorsCommand.getCommand());
	}

	@Test
	public void testGenerateAccessorsQuickAssistForTypeDeclaration() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public String name = \"name\";\r\n" +
				"	public String pet = \"pet\";\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "A");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Getters and Setters"));
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Getters"));
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Setters"));
	}

	@Test
	public void testGenerateAccessorsQuickAssistForFieldDeclaration() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public String name = \"name\";\r\n" +
				"	public String pet = \"pet\";\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Getter and Setter for 'name'"));
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Getter for 'name'"));
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Setter for 'name'"));
	}

	@Test
	public void testGenerateAccessorsQuickAssistForMultipleFieldDeclaration() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public String name = \"name\";\r\n" +
				"	public String pet = \"pet\";\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name = \"name\";\r\n	public String pet = \"pet\";");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Getters and Setters"));
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Getters"));
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Setters"));
	}

	@Test
	public void testGenerateAccessorsQuickAssistForFinalField() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public final String name = \"name\";\r\n" +
				"	public String pet = \"pet\";\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		Assert.assertFalse(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Getter and Setter for 'name'"));
		Assert.assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Getter for 'name'"));
		Assert.assertFalse(CodeActionHandlerTest.commandExists(quickAssistActions, CodeActionHandler.COMMAND_ID_APPLY_EDIT, "Generate Setter for 'name'"));
	}
}
