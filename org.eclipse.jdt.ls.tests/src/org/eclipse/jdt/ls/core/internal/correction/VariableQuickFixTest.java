/*******************************************************************************
 * Copyright (c) 2023 Gayan Perera and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Gayan Perera - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

public class VariableQuickFixTest extends AbstractSelectionTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		setOnly(CodeActionKind.QuickFix);
	}

	@Test
	public void testSplitVariableExpectDeclarationAndAssignment() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		String contents = "package test1;\r\n"
				+ "public class V {\r\n"
				+ "    public void foo() {\r\n"
				+ "        int maxCount = 10;\r\n"
				+ "    }\r\n"
				+ "}";
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("V.java", contents, false, null);
		//@formatter:off
		String expected = "package test1;\r\n"
				+ "public class V {\r\n"
				+ "    public void foo() {\r\n"
				+ "        int maxCount;\r\n"
				+ "        maxCount = 10;\r\n"
				+ "    }\r\n"
				+ "}";
		//@formatter:on
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "maxCount"));
		Expected e1 = new Expected("Split variable declaration", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testJoinVariableExpectDeclarationAndAssignment() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		String contents = "package test1;\r\n"
				+ "public class V {\r\n"
				+ "    public void foo() {\r\n"
				+ "        int maxCount;\r\n"
				+ "        maxCount = 10;\r\n"
				+ "    }\r\n"
				+ "}";
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("V.java", contents, false, null);
		//@formatter:off
		String expected = "package test1;\r\n"
				+ "public class V {\r\n"
				+ "    public void foo() {\r\n"
				+ "        int maxCount = 10;\r\n"
				+ "    }\r\n"
				+ "}";
		//@formatter:on
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "maxCount"));
		Expected e1 = new Expected("Join variable declaration", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testInvertEqualsExpectVariablesSwapped() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String contents = """
				package test1;
				public class E {
				    public void foo() {
						String name1 = "John";
						String name2 = "Doe";
						if (name1.equals(name2)) {
						}
				    }
				}""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", contents, false, null);
		String expected = """
				package test1;
				public class E {
				    public void foo() {
						String name1 = "John";
						String name2 = "Doe";
						if (name2.equals(name1)) {
						}
				    }
				}""";
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "name1.equals(name2)"));
		Expected e1 = new Expected("Invert equals", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}
}