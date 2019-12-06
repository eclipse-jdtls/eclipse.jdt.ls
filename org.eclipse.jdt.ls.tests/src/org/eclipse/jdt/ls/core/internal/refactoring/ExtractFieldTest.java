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

package org.eclipse.jdt.ls.core.internal.refactoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Hashtable;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractFieldRefactoring;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility.InitializeScope;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.junit.Before;
import org.junit.Test;

public class ExtractFieldTest extends AbstractSelectionTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		setOnly(CodeActionKind.Refactor);
	}

	@Test
	public void testExtractToField_DisabledInConstructorInvocation() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	A(int x, int y){\n");
		buf.append("		this(/*]*/x + y/*[*/);\n");
		buf.append("	};\n");
		buf.append("	A(int x){\n");
		buf.append("	};\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		failHelper(cu);
	}

	@Test
	public void testExtractToField_DisabledInFieldDeclaration() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private int x = /*]*/1/*[*/;\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		failHelper(cu);
	}

	@Test
	public void testExtractToField_DisabledInNullLiteral() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void test() {\n");
		buf.append("		Object object = /*]*/null/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		failHelper(cu);
	}

	@Test
	public void testExtractToField_DisabledInArrayInitializer() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void test() {\n");
		buf.append("		int[] array = new int[] /*]*/{ 1 + 2 }/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		failHelper(cu);
	}

	@Test
	public void testExtractToField_DisabledInNonParenthesizedAssignment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void test() {\n");
		buf.append("		int x, y;");
		buf.append("		int z = y = /*]*/ x = 1 + 2 /*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		failHelper(cu);
	}

	@Test
	public void testExtractToField_DisabledInVoid() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void test() {\n");
		buf.append("		/*]*/print()/*[*/;\n");
		buf.append("	}\n");
		buf.append("	public void print() {\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		failHelper(cu);
	}

	@Test
	public void testExtractToField_DisabledInForInitializer() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void test() {\n");
		buf.append("		int i;\n");
		buf.append("		for (/*]*/i = 0/*[*/; i < 10; i++);\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		failHelper(cu);
	}

	@Test
	public void testExtractToField_DisabledInInterface() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("interface E {\n");
		buf.append("	default void print(int x) {\n");
		buf.append("		/*]*/x++/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		failHelper(cu);
	}

	@Test
	public void testExtractToField_DisabledInMethodWithLocalType() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	void print(int x) {\n");
		buf.append("		class Local {}\n");
		buf.append("		Local local = /*]*/new Local()/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		failHelper(cu);
	}

	@Test
	public void testExtractToField_method() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	void m(int i){\n");
		buf.append("		int x= /*]*/0/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private int i;\n\n");
		buf.append("    void m(int i){\n");
		buf.append("		this.i = 0;\n");
		buf.append("        int x= /*]*/this.i/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to field", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_FIELD);

		assertCodeActions(cu, e1);
		assertTrue(helper(cu, InitializeScope.CURRENT_METHOD, buf.toString()));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private int i = 0;\n\n");
		buf.append("    void m(int i){\n");
		buf.append("		int x= /*]*/this.i/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		assertTrue(helper(cu, InitializeScope.FIELD_DECLARATION, buf.toString()));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private int i;\n\n");
		buf.append("    E() {\n");
		buf.append("        this.i = 0;\n");
		buf.append("    }\n\n");
		buf.append("    void m(int i){\n");
		buf.append("		int x= /*]*/this.i/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		assertTrue(helper(cu, InitializeScope.CLASS_CONSTRUCTORS, buf.toString()));
	}

	@Test
	public void testExtractToField_staticMethod() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	static void m(int i){\n");
		buf.append("		int x= /*]*/0/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private static int i;\n\n");
		buf.append("    static void m(int i){\n");
		buf.append("		E.i = 0;\n");
		buf.append("        int x= /*]*/E.i/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to field", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_FIELD);

		assertCodeActions(cu, e1);
		assertTrue(helper(cu, InitializeScope.CURRENT_METHOD, buf.toString()));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private static int i = 0;\n\n");
		buf.append("    static void m(int i){\n");
		buf.append("		int x= /*]*/E.i/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		assertTrue(helper(cu, InitializeScope.FIELD_DECLARATION, buf.toString()));

		assertFalse(helper(cu, InitializeScope.CLASS_CONSTRUCTORS, ""));
	}

	@Test
	public void testExtractToField_constructor() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	E(){\n");
		buf.append("		Object x = /*]*/new Object()/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private Object object;\n\n");
		buf.append("    E(){\n");
		buf.append("		object = new Object();\n");
		buf.append("        Object x = /*]*/object/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to field", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_FIELD);

		assertCodeActions(cu, e1);
		assertTrue(helper(cu, InitializeScope.CURRENT_METHOD, buf.toString()));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private Object object = new Object();\n\n");
		buf.append("    E(){\n");
		buf.append("		Object x = /*]*/object/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");
		assertTrue(helper(cu, InitializeScope.FIELD_DECLARATION, buf.toString()));

		assertFalse(helper(cu, InitializeScope.CLASS_CONSTRUCTORS, ""));
	}

	@Test
	public void testExtractToField_lambdaExpression() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Arrays;\n\n");
		buf.append("class E {\n");
		buf.append("	public void f(){\n");
		buf.append("		Arrays.asList(1, 2).stream().map((number) -> /*]*/number * number/*[*/);\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Arrays;\n\n");
		buf.append("class E {\n");
		buf.append("	private int i;\n\n");
		buf.append("    public void f(){\n");
		buf.append("		Arrays.asList(1, 2).stream().map((number) -> /*]*/{\n");
		buf.append("            i = number * number;\n");
		buf.append("            return i;\n");
		buf.append("        }/*[*/);\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to field", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_FIELD);

		assertCodeActions(cu, e1);
		assertTrue(helper(cu, InitializeScope.CURRENT_METHOD, buf.toString()));
		assertFalse(helper(cu, InitializeScope.FIELD_DECLARATION, ""));
		assertFalse(helper(cu, InitializeScope.CLASS_CONSTRUCTORS, ""));
	}

	@Test
	public void testExtractToField_lambdaExpressionReturnVoid() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void f(){\n");
		buf.append("		new Thread(() -> /*]*/new Object()/*[*/);\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private Object object;\n\n");
		buf.append("    public void f(){\n");
		buf.append("		new Thread(() -> /*]*/{\n");
		buf.append("            object = new Object();\n");
		buf.append("        }/*[*/);\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to field", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_FIELD);

		assertCodeActions(cu, e1);
		assertTrue(helper(cu, InitializeScope.CURRENT_METHOD, buf.toString()));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private Object object = new Object();\n\n");
		buf.append("    public void f(){\n");
		buf.append("		new Thread(() -> /*]*/object/*[*/);\n");
		buf.append("	}\n");
		buf.append("}\n");
		assertTrue(helper(cu, InitializeScope.FIELD_DECLARATION, buf.toString()));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private Object object;\n\n");
		buf.append("    E() {\n");
		buf.append("        object = new Object();\n");
		buf.append("    }\n\n");
		buf.append("    public void f(){\n");
		buf.append("		new Thread(() -> /*]*/object/*[*/);\n");
		buf.append("	}\n");
		buf.append("}\n");
		assertTrue(helper(cu, InitializeScope.CLASS_CONSTRUCTORS, buf.toString()));
	}

	@Test
	public void testExtractToField_anonymousClass() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void f(){\n");
		buf.append("		new Runnable() {\n");
		buf.append("			public void run() {\n");
		buf.append("				Object x = /*]*/new Object()/*[*/;\n");
		buf.append("			}\n");
		buf.append("		};\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void f(){\n");
		buf.append("		new Runnable() {\n");
		buf.append("			private Object object;\n\n");
		buf.append("            public void run() {\n");
		buf.append("				object = new Object();\n");
		buf.append("                Object x = /*]*/object/*[*/;\n");
		buf.append("			}\n");
		buf.append("		};\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to field", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_FIELD);

		assertCodeActions(cu, e1);
		assertTrue(helper(cu, InitializeScope.CURRENT_METHOD, buf.toString()));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void f(){\n");
		buf.append("		new Runnable() {\n");
		buf.append("			private Object object = new Object();\n\n");
		buf.append("            public void run() {\n");
		buf.append("				Object x = /*]*/object/*[*/;\n");
		buf.append("			}\n");
		buf.append("		};\n");
		buf.append("	}\n");
		buf.append("}\n");
		assertTrue(helper(cu, InitializeScope.FIELD_DECLARATION, buf.toString()));
		assertFalse(helper(cu, InitializeScope.CLASS_CONSTRUCTORS, ""));
	}

	@Test
	public void testExtractToField_standaloneStatement() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	public void f(){\n");
		buf.append("		/*]*/new Object()/*[*/;\n");
		buf.append("	}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private Object object;\n\n");
		buf.append("    public void f(){\n");
		buf.append("		/*]*/object = new Object();\n");
		buf.append("	}\n");
		buf.append("}\n");
		Expected e1 = new Expected("Extract to field", buf.toString(), JavaCodeActionKind.REFACTOR_EXTRACT_FIELD);

		assertCodeActions(cu, e1);
		assertTrue(helper(cu, InitializeScope.CURRENT_METHOD, buf.toString()));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private Object object = new Object();\n\n");
		buf.append("    public void f(){\n");
		buf.append("	}\n");
		buf.append("}\n");
		assertTrue(helper(cu, InitializeScope.FIELD_DECLARATION, buf.toString()));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("class E {\n");
		buf.append("	private Object object;\n\n");
		buf.append("    E() {\n");
		buf.append("        object = new Object();\n");
		buf.append("    }\n\n");
		buf.append("    public void f(){\n");
		buf.append("	}\n");
		buf.append("}\n");
		assertTrue(helper(cu, InitializeScope.CLASS_CONSTRUCTORS, buf.toString()));
	}

	protected void failHelper(ICompilationUnit cu) throws OperationCanceledException, CoreException {
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
		IProblem[] problems = astRoot.getProblems();
		Range range = getRange(cu, problems);
		int start = DiagnosticsHelper.getStartOffset(cu, range);
		int end = DiagnosticsHelper.getEndOffset(cu, range);
		ExtractFieldRefactoring refactoring = new ExtractFieldRefactoring(astRoot, start, end - start);
		RefactoringStatus result = refactoring.checkInitialConditions(new NullProgressMonitor());
		assertNotNull("precondition was supposed to fail", result);
		assertEquals("precondition was supposed to fail", false, result.isOK());
	}

	protected boolean helper(ICompilationUnit cu, InitializeScope initializeIn, String expected) throws Exception {
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
		IProblem[] problems = astRoot.getProblems();
		Range range = getRange(cu, problems);
		int start = DiagnosticsHelper.getStartOffset(cu, range);
		int end = DiagnosticsHelper.getEndOffset(cu, range);
		ExtractFieldRefactoring refactoring = new ExtractFieldRefactoring(astRoot, start, end - start);
		refactoring.setFieldName(refactoring.guessFieldName());

		RefactoringStatus activationResult = refactoring.checkInitialConditions(new NullProgressMonitor());
		assertTrue("activation was supposed to be successful", activationResult.isOK());
		if (initializeIn != null) {
			if (initializeIn == InitializeScope.CURRENT_METHOD && !refactoring.canEnableSettingDeclareInMethod()) {
				return false;
			} else if (initializeIn == InitializeScope.CLASS_CONSTRUCTORS && !refactoring.canEnableSettingDeclareInConstructors()) {
				return false;
			} else if (initializeIn == InitializeScope.FIELD_DECLARATION && !refactoring.canEnableSettingDeclareInFieldDeclaration()) {
				return false;
			}

			refactoring.setInitializeIn(initializeIn.ordinal());
		}

		RefactoringStatus checkInputResult = refactoring.checkFinalConditions(new NullProgressMonitor());
		assertTrue("precondition was supposed to pass but was " + checkInputResult.toString(), checkInputResult.isOK());

		Change change = refactoring.createChange(new NullProgressMonitor());
		WorkspaceEdit edit = ChangeUtil.convertToWorkspaceEdit(change);
		assertNotNull(change);
		String actual = evaluateChanges(edit.getChanges());
		assertEquals(expected, actual);
		return true;
	}
}
