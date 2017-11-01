/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.Assert.assertEquals;

import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.Before;
import org.junit.Test;

public class OrganizeImportsCommandTest extends AbstractProjectsManagerBasedTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	private OrganizeImportsCommand command = new OrganizeImportsCommand();

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();

		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testOrganizeImportsUnused() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		WorkspaceEdit rootEdit = new WorkspaceEdit();
		command.organizeImportsInCompilationUnit(cu, rootEdit);
		assertEquals(getOrganizeImportResult(cu, rootEdit), buf.toString());
	}

	@Test
	public void testOrganizeImportsSort() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		WorkspaceEdit rootEdit = new WorkspaceEdit();
		command.organizeImportsInCompilationUnit(cu, rootEdit);
		assertEquals(getOrganizeImportResult(cu, rootEdit), buf.toString());
	}

	@Test
	public void testOrganizeImportsAutomaticallyResolve() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		WorkspaceEdit rootEdit = new WorkspaceEdit();
		command.organizeImportsInCompilationUnit(cu, rootEdit);
		assertEquals(getOrganizeImportResult(cu, rootEdit), buf.toString());
	}

	@Test
	public void testOrganizeImportsInPackage() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu1 = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class F {\n");
		buf.append("\n");
		buf.append("    public F() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu2 = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");


		WorkspaceEdit rootEdit = new WorkspaceEdit();
		command.organizeImportsInPackageFragment(pack1, rootEdit);
		assertEquals(getOrganizeImportResult(cu1, rootEdit), buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class F {\n");
		buf.append("\n");
		buf.append("    public F() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		assertEquals(getOrganizeImportResult(cu2, rootEdit), buf.toString());
	}

	@Test
	public void testOrganizeImportsInProject() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu1 = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class F {\n");
		buf.append("\n");
		buf.append("    public F() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu2 = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		WorkspaceEdit rootEdit = command.organizeImportsInProject(pack1.getJavaProject().getProject());
		assertEquals(getOrganizeImportResult(cu1, rootEdit), buf.toString());

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class F {\n");
		buf.append("\n");
		buf.append("    public F() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		assertEquals(getOrganizeImportResult(cu2, rootEdit), buf.toString());
	}

	private String getOrganizeImportResult(ICompilationUnit cu, WorkspaceEdit we) throws BadLocationException, CoreException {
		List<TextEdit> change = we.getChanges().get(JDTUtils.getFileURI(cu));
		Document doc = new Document();
		doc.set(cu.getSource());

		return TextEditUtil.apply(doc, change);
	}
}
