/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.correction;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AddImportQuickAssistTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testAddImportForQualifiedType() throws Exception {
		IPackageFragment dependencyPack = fSourceFolder.createPackageFragment("com.example", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package com.example;\n");
		buf.append("public class Todo {\n");
		buf.append("}\n");
		dependencyPack.createCompilationUnit("Todo.java", buf.toString(), false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class Example {\n");
		buf.append("    void test() {\n");
		buf.append("        com.example.Todo todo = new Todo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Example.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("import com.example.Todo;\n");
		buf.append("\n");
		buf.append("public class Example {\n");
		buf.append("    void test() {\n");
		buf.append("        Todo todo = new Todo();\n");
		buf.append("    }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add import", buf.toString(), CodeActionKind.Refactor);

		Range selection = CodeActionUtil.getRange(cu, "com.example.Todo");
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testAddImportForParameterizedQualifiedType() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class Example {\n");
		buf.append("    java.util.Map<String, Integer> values;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Example.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("\n");
		buf.append("import java.util.Map;\n");
		buf.append("\n");
		buf.append("public class Example {\n");
		buf.append("    Map<String, Integer> values;\n");
		buf.append("}\n");
		Expected e1 = new Expected("Add import", buf.toString(), CodeActionKind.Refactor);

		Range selection = CodeActionUtil.getRange(cu, "java.util.Map");
		assertCodeActions(cu, selection, e1);
	}
}
