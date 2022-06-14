/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

public class ConvertMethodReferenceToLambaTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
		setIgnoredKind(CodeActionKind.Refactor, JavaCodeActionKind.SOURCE_OVERRIDE_METHODS, JavaCodeActionKind.SOURCE_GENERATE_TO_STRING, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS, JavaCodeActionKind.SOURCE_GENERATE_FINAL_MODIFIERS,
				JavaCodeActionKind.REFACTOR_EXTRACT_FIELD, JavaCodeActionKind.REFACTOR_EXTRACT_VARIABLE, JavaCodeActionKind.REFACTOR_INTRODUCE_PARAMETER, CodeActionKind.RefactorInline);
	}

	@Override
	protected ClientPreferences initPreferenceManager(boolean supportClassFileContents) {
		ClientPreferences clientPreferences = super.initPreferenceManager(supportClassFileContents);
		when(clientPreferences.isAdvancedExtractRefactoringSupported()).thenReturn(true);
		return clientPreferences;
	}

	@Test
	public void testMethodReferenceToLambda() throws Exception {
		setIgnoredCommands("Assign statement to new field", "Extract to constant", "Extract to field", "Extract to local variable (replace all occurrences)", "Extract to local variable", "Introduce Parameter...",
				ActionMessages.GenerateConstructorsAction_ellipsisLabel, ActionMessages.GenerateConstructorsAction_label);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.stream.Stream;\n");
		buf.append("public class E {\n");
		buf.append("    private Stream<String> asHex(Stream<Integer> stream) {\n");
		buf.append("        return stream.map(Integer::toHexString);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range range = new Range(new Position(4, 34), new Position(4, 34));
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, range);
		assertEquals(2, codeActions.size());
		Either<Command, CodeAction> codeAction = codeActions.get(0);
		CodeAction action = codeAction.getRight();
		assertEquals(CodeActionKind.QuickFix, action.getKind());
		assertEquals("Convert to lambda expression", action.getTitle());
		Command c = action.getCommand();
		assertEquals("java.apply.workspaceEdit", c.getCommand());
		assertNotNull(c.getArguments());
		assertEquals("Convert to lambda expression", c.getTitle());
	}

	@Test
	public void testLambdaToMethodReference() throws Exception {
		setIgnoredCommands("Assign statement to new field", "Extract to constant", "Extract to field", "Extract to local variable (replace all occurrences)", "Extract to local variable", "Introduce Parameter...",
				ActionMessages.GenerateConstructorsAction_ellipsisLabel, ActionMessages.GenerateConstructorsAction_label, "Extract lambda body to method");
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.stream.Stream;\n");
		buf.append("public class E {\n");
		buf.append("    private Stream<String> asHex(Stream<Integer> stream) {\n");
		buf.append("        return stream.map(t -> Integer.toHexString(t));\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range range = new Range(new Position(4, 39), new Position(4, 39));
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, range);
		assertEquals(2, codeActions.size());
		Either<Command, CodeAction> codeAction = codeActions.get(0);
		CodeAction action = codeAction.getRight();
		assertEquals(CodeActionKind.QuickFix, action.getKind());
		assertEquals("Convert to method reference", action.getTitle());
		Command c = action.getCommand();
		assertEquals("java.apply.workspaceEdit", c.getCommand());
		assertNotNull(c.getArguments());
		assertEquals("Convert to method reference", c.getTitle());
	}

}
