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
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jface.text.BadLocationException;
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
	public void testRenameParameter() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class E {\n",
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

		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.getFileURI(cu))), "package test1;\n" +
				"public class E {\n" +
				"   public int foo(String newname) {\n" +
				"  		newname.length()\n" +
				"   }\n" +
				"   public int bar(String str) {\n" +
				"   	str.length()\n" +
				"   }\n" +
				"}\n");
	}

	@Test
	public void testRenameLocalVariable() throws JavaModelException, BadLocationException {
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
		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.getFileURI(cu))),
				"package test1;\n" +
				"public class E {\n" +
				"   public int bar() {\n" +
				"		String str = new String();\n" +
				"   	str.length();\n" +
				"   }\n" +
				"   public int foo() {\n" +
				"		String newname = new String();\n" +
				"   	newname.length()\n" +
				"   }\n" +
				"}\n");
	}

	@Test
	public void testRenameField() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class E {\n",
				"	private int myValue = 2;\n",
				"   public void bar() {\n",
				"		myValue|* = 3;\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "newname");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);
		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.getFileURI(cu))),
				"package test1;\n" +
				"public class E {\n" +
				"	private int newname = 2;\n" +
				"   public void bar() {\n" +
				"		newname = 3;\n" +
				"   }\n" +
				"}\n");
	}

	@Test
	public void testRenameMethod() throws JavaModelException, BadLocationException {
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
		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.getFileURI(cu))),
				"package test1;\n" +
				"public class E {\n" +
				"   public int newname() {\n" +
				"   }\n" +
				"   public int foo() {\n" +
				"		this.newname();\n" +
				"   }\n" +
				"}\n"
				);
	}

	@Test
	public void testRenameSystemLibrary() throws JavaModelException {
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
	public void testRenameMultipleFiles() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes1= {
				"package test1;\n",
				"public class A {\n",
				"   public void foo() {\n",
				"   }\n",
				"}\n"
		};

		String[] codes2 = {
				"package test1;\n",
				"public class B {\n",
				"   public void foo() {\n",
				"		A a = new A();\n",
				"		a.foo|*();\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builderA = new StringBuilder();
		mergeCode(builderA, codes1);
		ICompilationUnit cuA = pack1.createCompilationUnit("A.java", builderA.toString(), false, null);

		StringBuilder builderB = new StringBuilder();
		Position pos = mergeCode(builderB, codes2);
		ICompilationUnit cuB = pack1.createCompilationUnit("B.java", builderB.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cuB, pos, "newname");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 2);

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.getFileURI(cuA))),
				"package test1;\n" +
				"public class A {\n" +
				"   public void newname() {\n" +
				"   }\n" +
				"}\n"
				);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.getFileURI(cuB))),
				"package test1;\n" +
				"public class B {\n" +
				"   public void foo() {\n" +
				"		A a = new A();\n" +
						"		a.newname();\n" +
				"   }\n" +
				"}\n"
				);

	}

	@Test
	public void testRenameOverrideMethod() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes1= {
				"package test1;\n",
				"public class A {\n",
				"   public void foo(){}\n",
				"}\n"
		};

		String[] codes2 = {
				"package test1;\n",
				"public class B extends A {\n",
				"	@Override\n",
				"   public void foo|*() {\n",
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

	@Test
	public void testRenameInterfaceMethod() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes1= {
				"package test1;\n",
				"public interface A {\n",
				"   public void foo();\n",
				"}\n"
		};

		String[] codes2 = {
				"package test1;\n",
				"public class B implements A {\n",
				"	@Override\n",
				"   public void foo|*() {\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builderA = new StringBuilder();
		mergeCode(builderA, codes1);
		ICompilationUnit cuA = pack1.createCompilationUnit("A.java", builderA.toString(), false, null);

		StringBuilder builderB = new StringBuilder();
		Position pos = mergeCode(builderB, codes2);
		ICompilationUnit cuB = pack1.createCompilationUnit("B.java", builderB.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cuB, pos, "newname");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 2);

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.getFileURI(cuA))),
				"package test1;\n" +
				"public interface A {\n" +
				"   public void newname();\n" +
				"}\n"
				);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.getFileURI(cuB))),
				"package test1;\n" +
				"public class B implements A {\n" +
				"	@Override\n" +
				"   public void newname() {\n" +
				"   }\n" +
				"}\n"
				);

	}

	@Test
	public void testRenameType() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes1= {
				"package test1;\n",
				"public class A {\n",
				"   public void foo(){\n",
				"		B b = new B();\n",
				"		b.foo();\n",
				"	}\n",
				"}\n"
		};

		String[] codes2 = {
				"package test1;\n",
				"public class B|* {\n",
				"   public void foo() {}\n",
				"}\n"
		};
		StringBuilder builderA = new StringBuilder();
		mergeCode(builderA, codes1);
		ICompilationUnit cuA = pack1.createCompilationUnit("A.java", builderA.toString(), false, null);

		StringBuilder builderB = new StringBuilder();
		Position pos = mergeCode(builderB, codes2);
		ICompilationUnit cuB = pack1.createCompilationUnit("B.java", builderB.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cuB, pos, "NewType");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 2);

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.getFileURI(cuA))),
				"package test1;\n" +
				"public class A {\n" +
				"   public void foo(){\n" +
				"		NewType b = new NewType();\n" +
				"		b.foo();\n" +
				"	}\n" +
				"}\n"
				);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.getFileURI(cuB))),
				"package test1;\n" +
				"public class NewType {\n" +
				"   public void foo() {}\n" +
				"}\n"
				);
	}

	@Test
	public void testRenameConstructor() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes1= {
				"package test1;\n",
				"public class A {\n",
				"   public void foo(){\n",
				"		B b = new B();\n",
				"		b.foo();\n",
				"	}\n",
				"}\n"
		};

		String[] codes2 = {
				"package test1;\n",
				"public class B {\n",
				"   public B|*() {}\n",
				"   public void foo() {}\n",
				"}\n"
		};
		StringBuilder builderA = new StringBuilder();
		mergeCode(builderA, codes1);
		ICompilationUnit cuA = pack1.createCompilationUnit("A.java", builderA.toString(), false, null);

		StringBuilder builderB = new StringBuilder();
		Position pos = mergeCode(builderB, codes2);
		ICompilationUnit cuB = pack1.createCompilationUnit("B.java", builderB.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cuB, pos, "NewName");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 2);

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.getFileURI(cuA))),
				"package test1;\n" +
				"public class A {\n" +
				"   public void foo(){\n" +
				"		NewName b = new NewName();\n" +
				"		b.foo();\n" +
				"	}\n" +
				"}\n"
				);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.getFileURI(cuB))),
				"package test1;\n" +
				"public class NewName {\n" +
				"   public NewName() {}\n" +
				"   public void foo() {}\n" +
				"}\n"
				);
	}

	@Test
	public void testRenameTypeParameter() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes1= {
				"package test1;\n",
				"public class A<T|*> {\n",
				"	private T t;\n",
				"	public T get() { return t; }\n",
				"}\n"
		};

		StringBuilder builderA = new StringBuilder();
		Position pos = mergeCode(builderA, codes1);
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", builderA.toString(), false, null);


		WorkspaceEdit edit = getRenameEdit(cu, pos, "TT");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.getFileURI(cu))),
				"package test1;\n" +
				"public class A<TT> {\n" +
				"	private TT t;\n" +
				"	public TT get() { return t; }\n" +
				"}\n"
				);

		String[] codes2 = {
				"package test1;\n",
				"public class B<T> {\n",
				"	private T t;\n",
				"	public <U|* extends Number> inspect(U u) { return u; }\n",
				"}\n"
		};

		StringBuilder builderB = new StringBuilder();
		pos = mergeCode(builderB, codes2);
		cu = pack1.createCompilationUnit("B.java", builderB.toString(), false, null);

		edit = getRenameEdit(cu, pos, "UU");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.getFileURI(cu))),
				"package test1;\n" +
				"public class B<T> {\n" +
				"	private T t;\n" +
				"	public <UU extends Number> inspect(UU u) { return u; }\n" +
				"}\n"
				);
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
