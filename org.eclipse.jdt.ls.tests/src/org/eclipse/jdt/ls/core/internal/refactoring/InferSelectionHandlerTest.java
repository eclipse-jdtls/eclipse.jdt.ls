/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.handlers.InferSelectionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.InferSelectionHandler.InferSelectionParams;
import org.eclipse.jdt.ls.core.internal.handlers.InferSelectionHandler.SelectionInfo;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class InferSelectionHandlerTest extends AbstractSelectionTest {
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	private static final String VERTICAL_BAR = "/*|*/";

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		setOnly(CodeActionKind.Refactor);
	}

	@Test
	public void testInferSelectionWhenExtractMethod() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        boolean b1 = true;\n");
		buf.append("        boolean b2 = false;\n");
		buf.append("        boolean b3 = true && !b2;\n");
		buf.append("        if (b1 && (/*|*/b2 || b3))\n");
		buf.append("            return 1;\n");
		buf.append("        \n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getVerticalBarRange(cu);
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);
		InferSelectionParams inferParams = new InferSelectionParams(RefactorProposalUtility.EXTRACT_METHOD_COMMAND, params);
		List<SelectionInfo> infos = InferSelectionHandler.inferSelectionsForRefactor(inferParams);
		Assert.assertNotNull(infos);
		Assert.assertEquals(infos.size(), 3);
		Assert.assertEquals(infos.get(0).name, "b2");
		Assert.assertEquals(infos.get(0).length, 2);
		Assert.assertEquals(infos.get(1).name, "b2 || b3");
		Assert.assertEquals(infos.get(1).length, 8);
		Assert.assertEquals(infos.get(2).name, "b1 && (b2 || b3)");
		Assert.assertEquals(infos.get(2).length, 21);
	}

	@Test
	public void testInferSelectionWhenExtractVariable() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        boolean b1 = true;\n");
		buf.append("        boolean b2 = false;\n");
		buf.append("        boolean b3 = true && !b2;\n");
		buf.append("        boolean b4 = b3 || /*|*/b2 && b1;\n");
		buf.append("        if ((b1||b4) && (b2||b3))\n");
		buf.append("            return 1;\n");
		buf.append("        \n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getVerticalBarRange(cu);
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);
		InferSelectionParams inferParams = new InferSelectionParams(RefactorProposalUtility.EXTRACT_VARIABLE_COMMAND, params);
		List<SelectionInfo> infos = InferSelectionHandler.inferSelectionsForRefactor(inferParams);
		Assert.assertNotNull(infos);
		Assert.assertEquals(infos.size(), 3);
		Assert.assertEquals(infos.get(0).name, "b2");
		Assert.assertEquals(infos.get(0).length, 2);
		Assert.assertEquals(infos.get(1).name, "b2 && b1");
		Assert.assertEquals(infos.get(1).length, 8);
		Assert.assertEquals(infos.get(2).name, "b3 || b2 && b1");
		Assert.assertEquals(infos.get(2).length, 19);
	}

	@Test
	public void testInferSelectionWhenExtractConstant() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        boolean b1 = true;\n");
		buf.append("        boolean b2 = false;\n");
		buf.append("        boolean b3 = /*|*/true || false;\n");
		buf.append("        boolean b4 = b3 || b2 && b1;\n");
		buf.append("        if ((b1||b4) && (b2||b3))\n");
		buf.append("            return 1;\n");
		buf.append("        \n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getVerticalBarRange(cu);
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);
		InferSelectionParams inferParams = new InferSelectionParams(RefactorProposalUtility.EXTRACT_CONSTANT_COMMAND, params);
		List<SelectionInfo> infos = InferSelectionHandler.inferSelectionsForRefactor(inferParams);
		Assert.assertNotNull(infos);
		Assert.assertEquals(infos.size(), 2);
		Assert.assertEquals(infos.get(0).name, "true");
		Assert.assertEquals(infos.get(0).length, 4);
		Assert.assertEquals(infos.get(1).name, "true || false");
		Assert.assertEquals(infos.get(1).length, 13);
	}

	@Test
	public void testInferSelectionWhenExtractField() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("    private String test = \"test\";\n");
		buf.append("    public int foo() {\n");
		buf.append("        int hashCode = this./*|*/test.hashCode();\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Range selection = getVerticalBarRange(cu);
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(cu, selection);
		InferSelectionParams inferParams = new InferSelectionParams(RefactorProposalUtility.EXTRACT_FIELD_COMMAND, params);
		List<SelectionInfo> infos = InferSelectionHandler.inferSelectionsForRefactor(inferParams);
		Assert.assertNotNull(infos);
		Assert.assertEquals(infos.size(), 2);
		Assert.assertEquals(infos.get(0).name, "this.test");
		Assert.assertEquals(infos.get(0).length, 14);
		Assert.assertEquals(infos.get(1).name, "this.test.hashCode()");
		Assert.assertEquals(infos.get(1).length, 25);
	}

	/**
	 * Find the last position of vertical bar comment.
	 * @param cu the ICompilationUnit containing source
	 *
	 * @return the last position of the vertical bar comment
	 */
	private Range getVerticalBarRange(ICompilationUnit cu) throws JavaModelException {
		return JDTUtils.toRange(cu, cu.getSource().indexOf(VERTICAL_BAR) + VERTICAL_BAR.length(), 0);
	}
}
