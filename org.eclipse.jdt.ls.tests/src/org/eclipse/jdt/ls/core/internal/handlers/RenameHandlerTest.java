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

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.Before;
import org.junit.Test;

public class RenameHandlerTest extends AbstractCompilationUnitBasedTest {

	private RenameHandler handler;

	private PreferenceManager preferenceManager;

	private IPackageFragmentRoot sourceFolder;

	@Override
	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		IJavaProject javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		preferenceManager = mock(PreferenceManager.class);
		Preferences p = mock(Preferences.class);
		when(preferenceManager.getPreferences()).thenReturn(p);
		when(p.isRenameEnabled()).thenReturn(true);
		handler = new RenameHandler(preferenceManager);
	}

	@Test
	public void testRename_parameter() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class E {\n",
				"   /** This is a method */\n",
				"   public int foo(String str) {\n",
				"  		str|*.length()\n",
				"   }\n",
				"   public int bar(String str) {\n",
				"   	str.length()\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "newname");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);
		assertEquals(edit.getChanges().get(JDTUtils.getFileURI(cu)).size(), 2);
	}

	@Test
	public void testRename_localVariable() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class E {\n",
				"   public int bar() {\n",
				"		String str = new String();\n",
				"   	str.length();\n",
				"   }\n",
				"   public int foo() {\n",
				"		String str = new String();\n",
				"   	str|*.length()\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "newname");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);
		assertEquals(edit.getChanges().get(JDTUtils.getFileURI(cu)).size(), 2);
	}

	@Test
	public void testRename_method() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class E {\n",
				"   public int bar() {\n",
				"   }\n",
				"   public int foo() {\n",
				"		this.bar|*();\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "newname");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);
		assertEquals(edit.getChanges().get(JDTUtils.getFileURI(cu)).size(), 2);
	}

	@Test
	public void testRename_systemLibrary() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class E {\n",
				"   public int bar() {\n",
				"		String str = new String();\n",
				"   	str.len|*gth();\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "newname");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 0);
	}

	@Test
	public void testRename_multipleFiles() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes1= {
				"package test1;\n",
				"public class A {\n",
				"   public void bar() {\n",
				"		String str = new String();\n",
				"   	str.length();\n",
				"   }\n",
				"   public void foo() {\n",
				"		String str = new String();\n",
				"   	str.length()\n",
				"   }\n",
				"}\n"
		};

		String[] codes2 = {
				"package test1;\n",
				"public class B {\n",
				"   public void bar() {\n",
				"		String str = new String();\n",
				"   	str.length();\n",
				"   }\n",
				"   public void foo() {\n",
				"		A a = new A();\n",
				"		a.foo|*();\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builderA = new StringBuilder();
		mergeCode(builderA, codes1);
		pack1.createCompilationUnit("A.java", builderA.toString(), false, null);

		StringBuilder builderB = new StringBuilder();
		Position pos = mergeCode(builderB, codes2);
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", builderB.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "newname");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 2);
		assertEquals(edit.getChanges().get(JDTUtils.getFileURI(cu)).size(), 1);
	}

	private Position mergeCode(StringBuilder builder, String[] codes) {
		Position pos = null;
		for (int i = 0; i < codes.length; i++) {
			int ind = codes[i].indexOf("|*");
			if (ind > 0) {
				pos = new Position(i, ind);
				codes[i] = codes[i].replace("|*", "");
			}
			builder.append(codes[i]);
		}
		return pos;
	}

	private WorkspaceEdit getRenameEdit(ICompilationUnit cu, Position pos, String newName) {
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(JDTUtils.getFileURI(cu));

		RenameParams params = new RenameParams(identifier, pos, newName);
		return handler.rename(params, monitor);
	}
}
