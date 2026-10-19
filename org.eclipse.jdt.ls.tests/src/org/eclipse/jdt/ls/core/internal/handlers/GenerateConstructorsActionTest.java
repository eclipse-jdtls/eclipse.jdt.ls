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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GenerateConstructorsActionTest extends AbstractCompilationUnitBasedTest {
	@Mock
	private JavaClientConnection connection;
	private IJavaProject fJavaProject;
	private IPackageFragmentRoot fRoot;
	private IPackageFragment fPackageP;

	@BeforeEach
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
	public void testGenerateConstructorsEnabled() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", """
			package p;

			public class A {
				String name;
			}
			""", true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		Either<Command, CodeAction> constructorAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS);
		assertNotNull(constructorAction);
		Command constructorCommand = CodeActionHandlerTest.getCommand(constructorAction);
		assertNotNull(constructorCommand);
		assertEquals(SourceAssistProcessor.COMMAND_ID_ACTION_GENERATECONSTRUCTORSPROMPT, constructorCommand.getCommand());
	}

	@Test
	public void testGenerateConstructorsQuickAssist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", """
			package p;

			public class A {
				String name;
			}
			""", true, null);
		//@formatter:on
		// test for field declaration
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertNotNull(quickAssistActions);
		assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, SourceAssistProcessor.COMMAND_ID_ACTION_GENERATECONSTRUCTORSPROMPT));
		// test for type declaration
		params = CodeActionUtil.constructCodeActionParams(unit, "A");
		codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, SourceAssistProcessor.COMMAND_ID_ACTION_GENERATECONSTRUCTORSPROMPT));
	}

	@Test
	public void testGenerateConstructorsQuickAssistWithAllStaticFields() throws JavaModelException {
			//@formatter:off
			ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", """
				package p;

				public class A {
					static String name;
				}
				""", true, null);
			//@formatter:on
			// test for field declaration
			CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "static String name");
			List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
			assertNotNull(codeActions);
			Either<Command, CodeAction> quickAssistActions = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.QUICK_ASSIST);
			assertNotNull(quickAssistActions);
			assertFalse(CodeActionHandlerTest.commandExists(List.of(quickAssistActions), SourceAssistProcessor.COMMAND_ID_ACTION_GENERATECONSTRUCTORSPROMPT), "Generate constructors quick assist should not be available for a static field");
			// test for type declaration
			params = CodeActionUtil.constructCodeActionParams(unit, "class A");
			codeActions = server.codeAction(params).join();
			assertNotNull(codeActions);
			Either<Command, CodeAction> constructorAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS);
			// constructor should not refer to static fields
			assertNotNull(constructorAction);

		}

	@Test
	public void testGenerateConstructors_emptyFields() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", """
			package p;

			public class A {
			}
			""", true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "class A");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		Either<Command, CodeAction> constructorAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS);
		assertNotNull(constructorAction);

	}

	@Test
	public void testGenerateConstructorsDisabled_interface() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", """
			package p;

			public interface A {
				final String name = "test";
			}
			""", true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		assertFalse(CodeActionHandlerTest.containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS), "The operation is not applicable to interfaces");
	}

	@Test
	public void testGenerateConstructorsDisabled_anonymous() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", """
			package p;

			public class A {
				public Runnable getRunnable() {
					return new Runnable() {
						@Override
						public void run() {
						}
					};
				}
			}
			""", true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "run()");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		assertFalse(CodeActionHandlerTest.containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS), "The operation is not applicable to anonymous");
	}

	@Test
	public void testGenerateConstructorsWithSuperDelegation() throws Exception {
		fPackageP.createCompilationUnit("A.java", """
			package p;

			public class A {
				public A() {
				}
				public A(String a) {
				}
			}
			""", true, null);
		ICompilationUnit unitB = fPackageP.createCompilationUnit("B.java", """
			package p;

			public class B extends A {
			}
			""", true, null);
		// Verify the code action is available
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unitB, "class B");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		Either<Command, CodeAction> constructorAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS);
		assertNotNull(constructorAction, "Generate constructors action should be available");

		// Verify quick assist is also available
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, SourceAssistProcessor.COMMAND_ID_ACTION_GENERATECONSTRUCTORSPROMPT), "Quick assist should be available");

		// Generate constructors using the handler and verify they delegate to super
		GenerateConstructorsHandler.CheckConstructorsResponse response = GenerateConstructorsHandler.checkConstructorsStatus(params);
		assertNotNull(response.constructors);
		assertEquals(2, response.constructors.length, "Should have 2 constructors from superclass");
		assertNotNull(response.fields);
		assertEquals(0, response.fields.length, "Should have no fields");

		CodeGenerationSettings settings = new CodeGenerationSettings();
		settings.createComments = false;
		TextEdit edit = GenerateConstructorsHandler.generateConstructors(unitB.findPrimaryType(), response.constructors, response.fields, settings, null, new NullProgressMonitor());
		assertNotNull(edit);
		JavaModelUtil.applyEdit(unitB, edit, true, null);

		// Verify the generated constructors delegate to super
		String actual = unitB.getSource();
		assertTrue(actual.contains("public B() {"), "Should contain B() constructor\n" + actual);
		assertTrue(actual.contains("public B(String a) {"), "Should contain B(String a) constructor\n" + actual);
		assertTrue(actual.contains("super(a);"), "Should contain super(a) call\n" + actual);
	}
}
