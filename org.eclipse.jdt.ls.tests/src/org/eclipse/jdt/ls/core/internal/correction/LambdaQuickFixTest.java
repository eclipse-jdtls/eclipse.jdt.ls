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
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
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

	@Test
	public void testAddInferredLambdaParameterTypesExpectTypes() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String contents = """
				package test1;
				public class L {
				    public void foo() {
						Func f = (a, b) -> System.out.println(a + b);
				    }

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		ICompilationUnit cu = pack1.createCompilationUnit("L.java", contents, false, null);
		String expected = """
				package test1;
				public class L {
				    public void foo() {
						Func f = (String a, String b) -> System.out.println(a + b);
				    }

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "(a, b)"));
		Expected e1 = new Expected("Add inferred lambda parameter types", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testAddVarLambdaParameterTypesExpectVarKeyword() throws Exception {
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		JavaCore.setComplianceOptions("11", options);
		fJProject1.setOptions(options);

		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String contents = """
				package test1;
				public class L {
				    public void foo() {
						Func f = (a, b) -> System.out.println(a + b);
				    }

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		ICompilationUnit cu = pack1.createCompilationUnit("L.java", contents, false, null);
		String expected = """
				package test1;
				public class L {
				    public void foo() {
						Func f = (var a, var b) -> System.out.println(a + b);
				    }

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "(a, b)"));
		Expected e1 = new Expected("Add 'var' lambda parameter types", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testRemoveVarOrInferredLambdaParameterTypesExpectNoType() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String contents = """
				package test1;
				public class L {
				    public void foo() {
						Func f = (String a, String b) -> System.out.println(a + b);
				    }

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		ICompilationUnit cu = pack1.createCompilationUnit("L.java", contents, false, null);
		String expected = """
				package test1;
				public class L {
				    public void foo() {
						Func f = (a, b) -> System.out.println(a + b);
				    }

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "(String a, String b) ->"));
		Expected e1 = new Expected("Remove lambda parameter types", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testRemoveVarOrInferredLambdaParameterTypesExpectNoVarKeyword() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String contents = """
				package test1;
				public class L {
				    public void foo() {
						Func f = (var a, var b) -> System.out.println(a + b);
				    }

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		ICompilationUnit cu = pack1.createCompilationUnit("L.java", contents, false, null);
		String expected = """
				package test1;
				public class L {
				    public void foo() {
						Func f = (a, b) -> System.out.println(a + b);
				    }

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "(var a, var b) ->"));
		Expected e1 = new Expected("Remove lambda parameter types", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testChangeLambdaBodyToBlockExpectBlock() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String contents = """
				package test1;
				public class L {
					public void foo() {
						Func f = (a, b) -> System.out.println(a + b);
					}

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		ICompilationUnit cu = pack1.createCompilationUnit("L.java", contents, false, null);
		String expected = """
				package test1;
				public class L {
					public void foo() {
						Func f = (a, b) -> {
				            System.out.println(a + b);
				        };
					}

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "->"));
		Expected e1 = new Expected("Change body expression to block", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testChangeLambdaBodyToExpressionExpectExpression() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String contents = """
				package test1;
				public class L {
					public void foo() {
						Func f = (a, b) -> {
							System.out.println(a + b);
						};
					}

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		ICompilationUnit cu = pack1.createCompilationUnit("L.java", contents, false, null);
		String expected = """
				package test1;
				public class L {
					public void foo() {
						Func f = (a, b) -> System.out.println(a + b);
					}

					public interface Func {
						void foo(String a, String b);
					}
				}""";
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "->"));
		Expected e1 = new Expected("Change body block to expression", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}

	@Test
	public void testConvertLambdaToMethodReferenceExpectMethodRef() throws Exception {
		setOnly(JavaCodeActionKind.QUICK_ASSIST);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String contents = """
				package test1;
				public class L {
					public void foo() {
						this.consume(a -> a.print());
					}

					public void consume(Func f) {
					}

					public interface Func {
						void foo(Obj a);
					}

					public class Obj {
						public void print() {}
					}
				}""";
		ICompilationUnit cu = pack1.createCompilationUnit("L.java", contents, false, null);
		String expected = """
				package test1;
				public class L {
					public void foo() {
						this.consume(Obj::print);
					}

					public void consume(Func f) {
					}

					public interface Func {
						void foo(Obj a);
					}

					public class Obj {
						public void print() {}
					}
				}""";
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu, CodeActionUtil.getRange(cu, "->"));
		Expected e1 = new Expected("Convert to method reference", expected, JavaCodeActionKind.QUICK_ASSIST);
		assertCodeActions(codeActions, e1);
	}
}