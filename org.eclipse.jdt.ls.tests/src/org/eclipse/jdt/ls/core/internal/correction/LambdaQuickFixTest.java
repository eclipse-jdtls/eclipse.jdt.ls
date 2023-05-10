/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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

import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

public class LambdaQuickFixTest extends AbstractSelectionTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		setOnly(CodeActionKind.Refactor, CodeActionKind.QuickFix);
	}

	@Test
	public void testCleanUpLambdaConvertLambdaBlockToExpression() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		//@formatter:off
		String contents = "package test1;\r\n"
				+ "interface F1 {\r\n"
				+ "    int foo1(int a);\r\n"
				+ "}\r\n"
				+ "public class E {\r\n"
				+ "    public void foo(int a) {\r\n"
				+ "        F1 k = (e) -> {\r\n"
				+ "            return a;\r\n"
				+ "        };\r\n"
				+ "        k.foo1(5);\r\n"
				+ "    }\r\n"
				+ "}";
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", contents, false, null);
		//@formatter:off
		String expected = "package test1;\r\n"
				+ "interface F1 {\r\n"
				+ "    int foo1(int a);\r\n"
				+ "}\r\n"
				+ "public class E {\r\n"
				+ "    public void foo(int a) {\r\n"
				+ "        F1 k = e -> a;\r\n"
				+ "        k.foo1(5);\r\n"
				+ "    }\r\n"
				+ "}";
		//@formatter:on
		Range range = new Range(new Position(7, 16), new Position(7, 16));
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, range);
		Expected e1 = new Expected("Clean up lambda expression", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testCleanUpLambdaConvertLambdaBlockToExpressionAddParenthesis() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		//@formatter:off
		String contents = "package test1;\r\n"
				+ "interface F1 {\r\n"
				+ "    int foo1(int a);\r\n"
				+ "}\r\n"
				+ "public class E {\r\n"
				+ "    public void foo(int a) {\r\n"
				+ "        F1 k = (e) -> {\r\n"
				+ "            return a + 1;\r\n"
				+ "        };\r\n"
				+ "        k.foo1(5);\r\n"
				+ "    }\r\n"
				+ "}";
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", contents, false, null);
		//@formatter:off
		String expected = "package test1;\r\n"
				+ "interface F1 {\r\n"
				+ "    int foo1(int a);\r\n"
				+ "}\r\n"
				+ "public class E {\r\n"
				+ "    public void foo(int a) {\r\n"
				+ "        F1 k = e -> (a + 1);\r\n"
				+ "        k.foo1(5);\r\n"
				+ "    }\r\n"
				+ "}";
		//@formatter:on
		Range range = new Range(new Position(7, 16), new Position(7, 16));
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, range);
		Expected e1 = new Expected("Clean up lambda expression", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}
}