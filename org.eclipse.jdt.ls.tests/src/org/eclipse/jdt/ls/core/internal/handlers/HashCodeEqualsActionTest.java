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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HashCodeEqualsActionTest extends AbstractCompilationUnitBasedTest {
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
	public void testHashCodeEqualsEnabled() throws JavaModelException {
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
		assertNotNull(codeActions);
		assertTrue(CodeActionHandlerTest.containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_HASHCODE_EQUALS));
	}

	@Test
	public void testHashCodeEqualsDisabled_emptyFields() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public static String name;\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		assertFalse(CodeActionHandlerTest.containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_HASHCODE_EQUALS), "The operation is not applicable to class without any non-static fields");
	}

	@Test
	public void testHashCodeEqualsDisabled_interface() throws JavaModelException {
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
		assertNotNull(codeActions);
		assertFalse(CodeActionHandlerTest.containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_HASHCODE_EQUALS), "The operation is not applicable to interfaces");
	}

	@Test
	public void testHashCodeEqualsDisabled_enum() throws JavaModelException {
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
		assertNotNull(codeActions);
		assertFalse(CodeActionHandlerTest.containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_HASHCODE_EQUALS), "The operation is not applicable to enums");
	}

	@Test
	public void testHashCodeEqualsQuickAssist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public final String name = \"test\";\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "A");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertFalse(quickAssistActions.isEmpty());
		assertTrue(CodeActionHandlerTest.commandExists(quickAssistActions, SourceAssistProcessor.COMMAND_ID_ACTION_HASHCODEEQUALSPROMPT));
		// Test if the quick assist exists only for type declaration
		params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertFalse(quickAssistActions.isEmpty());
		assertFalse(CodeActionHandlerTest.commandExists(quickAssistActions, SourceAssistProcessor.COMMAND_ID_ACTION_HASHCODEEQUALSPROMPT));
	}

	@Test
	public void testNoHashCodeEqualsQuickAssist() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	String name;\r\n" +
				"   public int hashCode() {\r\n" +
				"	}\r\n" +
				"	public boolean equals(Object a) {\r\n" +
				"		return true;\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "A");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		assertNull(CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.QUICK_ASSIST, "Generate hashCode() and equals()..."));
	}
}
