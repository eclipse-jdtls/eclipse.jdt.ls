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

package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.Before;
import org.junit.Test;

public class JDTDelegateCommandHandlerTest extends AbstractProjectsManagerBasedTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	private JDTDelegateCommandHandler handler = new JDTDelegateCommandHandler();

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

		assertEquals(getOrganizeImportResult(cu, handler.organizeImports(cu)), buf.toString());
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

		assertEquals(getOrganizeImportResult(cu, handler.organizeImports(cu)), buf.toString());
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

		assertEquals(getOrganizeImportResult(cu, handler.organizeImports(cu)), buf.toString());
	}

	private String getOrganizeImportResult(ICompilationUnit cu, WorkspaceEdit we) throws BadLocationException, CoreException {
		Iterator<Entry<String, List<TextEdit>>> editEntries = we.getChanges().entrySet().iterator();
		Entry<String, List<TextEdit>> entry = editEntries.next();
		assertNotNull("No edits generated", entry);
		assertEquals("More than one resource modified", false, editEntries.hasNext());

		Document doc = new Document();
		doc.set(cu.getSource());

		return TextEditUtil.apply(doc, entry.getValue());
	}
}
