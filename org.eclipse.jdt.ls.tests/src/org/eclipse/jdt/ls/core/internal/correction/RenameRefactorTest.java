/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RenameRefactorTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Override
	protected ClientPreferences initPreferenceManager(boolean supportClassFileContents) {
		ClientPreferences clientPreferences = super.initPreferenceManager(supportClassFileContents);
		when(clientPreferences.isAdvancedExtractRefactoringSupported()).thenReturn(true);
		return clientPreferences;
	}

	@Test
	public void testRenameMethod() throws Exception {
		testRename(createTestUnit(), "foo");
	}

	@Test
	public void testRenameClass() throws Exception {
		testRename(createTestUnit(), "E");
	}

	@Test
	public void testRenameVariable() throws Exception {
		testRename(createTestUnit(), "text");
	}

	@Test
	public void testNoRenameForBinaryType() throws Exception {
		ICompilationUnit cu = createTestUnit();
		assertCodeActionNotExists(cu, CodeActionUtil.getRange(cu, "String"), "Rename...");
	}

	@Test
	public void testNoRenameForKeyword() throws Exception {
		ICompilationUnit cu = createTestUnit();
		assertCodeActionNotExists(cu, CodeActionUtil.getRange(cu, "public"), "Rename...");
	}

	private void testRename(ICompilationUnit cu, String name) throws Exception {
		Range range = CodeActionUtil.getRange(cu, name);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, range);
		Either<Command, CodeAction> codeAction = codeActions.stream().filter(ca -> getTitle(ca).equals("Rename...")).findFirst().orElse(null);
		assertNotNull(codeAction);
		CodeAction action = codeAction.getRight();
		assertEquals(JavaCodeActionKind.REFACTOR_RENAME, action.getKind());
		Command c = action.getCommand();
		assertEquals(GetRefactorEditHandler.RENAME_COMMAND, c.getCommand());
		assertNotNull(c.getArguments());
		GetRefactorEditHandler.RenamePosition position = (GetRefactorEditHandler.RenamePosition) c.getArguments().get(0);
		assertEquals(cu.getSource().lastIndexOf(name), position.offset);
		assertEquals(name.length(), position.length);
	}

	private ICompilationUnit createTestUnit() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String text = \"\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		return pack1.createCompilationUnit("E.java", buf.toString(), false, null);
	}
}