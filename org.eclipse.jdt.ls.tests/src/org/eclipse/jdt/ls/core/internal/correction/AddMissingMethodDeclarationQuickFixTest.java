/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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

public class AddMissingMethodDeclarationQuickFixTest extends AbstractSelectionTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testAddMissingMethodDecl_MethodReferenceInStaticContext() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST, CodeActionKind.QuickFix);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		var contents = """
				package test1;
				public class App {
					public static void filter(Func<String> predication) {}

					public static void foo() {
						App.filter(App::notEmpty);
					}

					interface Func<T> {
						boolean test(T value);
					}
				}
				""";
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("App.java", contents, false, null);
		//@formatter:off
		var expected = """
				package test1;
				public class App {
					public static void filter(Func<String> predication) {}

					public static void foo() {
						App.filter(App::notEmpty);
					}

					interface Func<T> {
						boolean test(T value);
					}

				    private static boolean notEmpty(String string1) {
				        return false;
				    }
				}
				""";
		//@formatter:on
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "notEmpty"));
		Expected e1 = new Expected("Add missing method 'notEmpty' to class 'App'", expected, CodeActionKind.QuickFix);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testAddMissingMethodDecl_MethodReferenceInObjectInstanceContext() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST, CodeActionKind.QuickFix);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		//@formatter:off
		var contents = """
				package test1;
				public class App {
					public void filter(Func<String> predication) {}

					public void foo() {
						this.filter(this::notEmpty);
					}

					interface Func<T> {
						boolean test(T value);
					}
				}
				""";
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("App.java", contents, false, null);
		//@formatter:off
		var expected = """
				package test1;
				public class App {
					public void filter(Func<String> predication) {}

					public void foo() {
						this.filter(this::notEmpty);
					}

					interface Func<T> {
						boolean test(T value);
					}

				    private boolean notEmpty(String string1) {
				        return false;
				    }
				}
				""";
		//@formatter:on
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "notEmpty"));
		Expected e1 = new Expected("Add missing method 'notEmpty' to class 'App'", expected, CodeActionKind.QuickFix);
		assertCodeActions(codeActions, e1);
	}
}