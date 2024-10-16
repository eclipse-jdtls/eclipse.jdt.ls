/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.ProjectUtils.getJavaSourceLevel;
import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RenameHandlerTest extends AbstractProjectsManagerBasedTest {

	private RenameHandler handler;

	private ClientPreferences clientPreferences;

	private IPackageFragmentRoot sourceFolder;

	@Before
	public void setup() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isResourceOperationSupported()).thenReturn(false);
		Preferences p = mock(Preferences.class);
		when(p.getProjectConfigurations()).thenReturn(null);
		when(preferenceManager.getPreferences()).thenReturn(p);
		when(p.isRenameEnabled()).thenReturn(true);
		when(p.getMavenDefaultMojoExecutionAction()).thenReturn("ignore");
		handler = new RenameHandler(preferenceManager);
	}

	@Test
	public void testRenameParameter() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class E {\n",
				"   public int foo(String str) {\n",
				"  		str|*.length();\n",
				"   }\n",
				"   public int bar(String str) {\n",
				"   	str.length();\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "newname");

		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);

		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.toURI(cu))),
				"package test1;\n" +
				"public class E {\n" +
				"   public int foo(String newname) {\n" +
				"  		newname.length();\n" +
				"   }\n" +
				"   public int bar(String str) {\n" +
				"   	str.length();\n" +
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
		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.toURI(cu))),
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
		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.toURI(cu))),
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
		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.toURI(cu))),
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
	public void testRenameTypeWithResourceChanges() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = { "package test1;\n",
				           "public class E|* {\n",
				           "   public E() {\n",
				           "   }\n",
				           "   public int bar() {\n", "   }\n",
				           "   public int foo() {\n",
				           "		this.bar();\n",
				           "   }\n",
				           "}\n" };
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "Newname");
		assertNotNull(edit);
		List<Either<TextDocumentEdit, ResourceOperation>> resourceChanges = edit.getDocumentChanges();

		assertEquals(resourceChanges.size(), 2);

		Either<TextDocumentEdit, ResourceOperation> change = resourceChanges.get(1);
		RenameFile resourceChange = (RenameFile) change.getRight();
		assertEquals(JDTUtils.toURI(cu), resourceChange.getOldUri());
		assertEquals(JDTUtils.toURI(cu).replaceFirst("(?s)E(?!.*?E)", "Newname"), resourceChange.getNewUri());

		List<TextEdit> testChanges = new LinkedList<>();
		testChanges.addAll(resourceChanges.get(0).getLeft().getEdits());

		String expected = "package test1;\n" +
						  "public class Newname {\n" +
						  "   public Newname() {\n" +
						  "   }\n" +
						  "   public int bar() {\n" +
						  "   }\n" +
						  "   public int foo() {\n" +
						  "		this.bar();\n" +
						  "   }\n" +
						  "}\n";

		assertEquals(expected, TextEditUtil.apply(builder.toString(), testChanges));
	}

	@Test(expected = ResponseErrorException.class)
	public void testRenameTypeWithErrors() throws JavaModelException, BadLocationException {
		Mockito.lenient().when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = { "package test1;\n",
				           "public class Newname {\n",
				           "   }\n",
				           "}\n" };
		StringBuilder builder = new StringBuilder();
		mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("Newname.java", builder.toString(), false, null);


		String[] codes1 = { "package test1;\n",
				           "public class E|* {\n",
				           "   public E() {\n",
				           "   }\n",
				           "   public int bar() {\n", "   }\n",
				           "   public int foo() {\n",
				           "		this.bar();\n",
				           "   }\n",
				           "}\n" };
		builder = new StringBuilder();
		Position pos = mergeCode(builder, codes1);
		cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "Newname");
		assertNotNull(edit);
		List<Either<TextDocumentEdit, ResourceOperation>> resourceChanges = edit.getDocumentChanges();

		assertEquals(resourceChanges.size(), 3);
	}

	@Test(expected = ResponseErrorException.class)
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

		getRenameEdit(cu, pos, "newname");
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

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.toURI(cuA))),
				"package test1;\n" +
				"public class A {\n" +
				"   public void newname() {\n" +
				"   }\n" +
				"}\n"
				);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.toURI(cuB))),
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
	public void testRenameOverrideMethodSimple() throws JavaModelException, BadLocationException {
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
		ICompilationUnit cuA = pack1.createCompilationUnit("A.java", builderA.toString(), false, null);

		StringBuilder builderB = new StringBuilder();
		Position pos = mergeCode(builderB, codes2);
		ICompilationUnit cuB = pack1.createCompilationUnit("B.java", builderB.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cuB, pos, "newname");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 2);

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.toURI(cuA))),
				"package test1;\n" +
				"public class A {\n" +
				"   public void newname(){}\n" +
				"}\n"
				);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.toURI(cuB))),
				"package test1;\n" +
				"public class B extends A {\n" +
				"	@Override\n" +
				"   public void newname() {\n" +
				"   }\n" +
				"}\n"
				);
	}

	@Test
	public void testRenameOverrideMethodComplex() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"class B extends A {\n",
				"	public void foo|*() {\n",
				"	};\n",
				"}\n",
				"abstract class A {\n",
				"	public abstract void foo();\n",
				"}\n",
				"class C extends A implements D {\n",
				"	public void foo() {\n",
				"	};\n",
				"}\n",
				"interface D {\n",
				"	void foo();\n",
				"}\n"
		};

		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", builder.toString(), false, null);


		WorkspaceEdit edit = getRenameEdit(cu, pos, "newfoo");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);

		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.toURI(cu))),
				"package test1;\n" +
				"class B extends A {\n" +
				"	public void newfoo() {\n" +
				"	};\n" +
				"}\n" +
				"abstract class A {\n" +
				"	public abstract void newfoo();\n" +
				"}\n" +
				"class C extends A implements D {\n" +
				"	public void newfoo() {\n" +
				"	};\n" +
				"}\n" +
				"interface D {\n" +
				"	void newfoo();\n" +
				"}\n"
				);

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

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.toURI(cuA))),
				"package test1;\n" +
				"public interface A {\n" +
				"   public void newname();\n" +
				"}\n"
				);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.toURI(cuB))),
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
				"	public B() {}\n",
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

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.toURI(cuA))),
				"package test1;\n" +
				"public class A {\n" +
				"   public void foo(){\n" +
				"		NewType b = new NewType();\n" +
				"		b.foo();\n" +
				"	}\n" +
				"}\n"
				);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.toURI(cuB))),
				"package test1;\n" +
				"public class NewType {\n" +
				"	public NewType() {}\n" +
				"   public void foo() {}\n" +
				"}\n"
				);
	}

	// this test should pass when starting with -javaagent:<lombok_jar> (-javagent:~/.m2/repository/org/projectlombok/lombok/1.18.28/lombok-1.18.28.jar)
	// https://github.com/eclipse/eclipse.jdt.ls/issues/1775
	@Test
	public void testRenameTypeLombok() throws Exception {
		when(preferenceManager.getPreferences().isImportMavenEnabled()).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("mavenlombok");
		IFile file = project.getFile("src/main/java/org/sample/Test.java");
		assertTrue(file.exists());
		ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
		Position pos = new Position(5, 15);
		String source = cu.getSource();
		String expected = source.replace("Test", "Test1");
		WorkspaceEdit edit = getRenameEdit(cu, pos, "Test1");
		assertNotNull(edit);
		assertEquals(2, edit.getChanges().size());
		assertEquals(expected, TextEditUtil.apply(source, edit.getChanges().get(JDTUtils.toURI(cu))));
	}

	// this test should pass when starting with -javaagent:<lombok_jar> (-javagent:~/.m2/repository/org/projectlombok/lombok/1.18.28/lombok-1.18.28.jar)
	// https://github.com/redhat-developer/vscode-java/issues/3203
	@Test
	public void testLombokSingular() throws Exception {
		when(preferenceManager.getPreferences().isImportMavenEnabled()).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("mavenlombok");
		IFile file = project.getFile("src/main/java/org/sample/Test2.java");
		assertTrue(file.exists());
		ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
		Position pos = new Position(9, 18);
		String source = cu.getSource();
		String expected = source.replace("singulars", "singulars2");
		WorkspaceEdit edit = getRenameEdit(cu, pos, "singulars2");
		assertNotNull(edit);
		assertEquals(1, edit.getChanges().size());
		assertEquals(expected, TextEditUtil.apply(source, edit.getChanges().get(JDTUtils.toURI(cu))));
	}

	// this test should pass when starting with -javaagent:<lombok_jar> (-javagent:~/.m2/repository/org/projectlombok/lombok/1.18.28/lombok-1.18.28.jar)
	// https://github.com/redhat-developer/vscode-java/issues/2805
	@Test
	public void testRenameMethodLombok() throws Exception {
		if (Boolean.getBoolean("jdt.ls.lombok.disabled")) {
			return;
		}
		when(preferenceManager.getPreferences().isImportMavenEnabled()).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("mavenlombok");
		List<IMarker> markers = ResourceUtils.getErrorMarkers(project);
		if (!markers.isEmpty()) {
			// there isn't the lombok agent
			return;
		}
		IFile main = project.getFile("src/main/java/org/sample/Main.java");
		assertTrue(main.exists());
		ICompilationUnit mainCu = JavaCore.createCompilationUnitFrom(main);
		String mainSource = mainCu.getSource();
		String mainExpected = mainSource.replace("getName", "getName1");
		IFile file = project.getFile("src/main/java/org/sample/Test.java");
		assertTrue(file.exists());
		ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
		Position pos = new Position(6, 23);
		String source = cu.getSource();
		String expected = source.replace("name", "name1");
		WorkspaceEdit edit = getRenameEdit(cu, pos, "name1");
		assertNotNull(edit);
		assertEquals(2, edit.getChanges().size());
		assertEquals(expected, TextEditUtil.apply(source, edit.getChanges().get(JDTUtils.toURI(cu))));
		assertEquals(mainExpected, TextEditUtil.apply(mainSource, edit.getChanges().get(JDTUtils.toURI(mainCu))));
	}

	@Test
	public void testRenameSuperMethod() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"class A {\n",
				"   public void bar() {\n",
				"   }\n",
				"}\n",
				"class B extends A {\n",
				"   public void bar() {\n",
				"		super|*.bar();\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "TypeA");
		assertNotNull(edit);
		assertEquals(1, edit.getChanges().size());

		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.toURI(cu))),
				"package test1;\n" +
				"class TypeA {\n" +
				"   public void bar() {\n" +
				"   }\n" +
				"}\n" +
				"class B extends TypeA {\n" +
				"   public void bar() {\n" +
				"		super.bar();\n" +
				"   }\n" +
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

		assertEquals(TextEditUtil.apply(builderA.toString(), edit.getChanges().get(JDTUtils.toURI(cuA))),
				"package test1;\n" +
				"public class A {\n" +
				"   public void foo(){\n" +
				"		NewName b = new NewName();\n" +
				"		b.foo();\n" +
				"	}\n" +
				"}\n"
				);

		assertEquals(TextEditUtil.apply(builderB.toString(), edit.getChanges().get(JDTUtils.toURI(cuB))),
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

		String[] codes= {
				"package test1;\n",
				"public class A<T|*> {\n",
				"	private T t;\n",
				"	public T get() { return t; }\n",
				"}\n"
		};

		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", builder.toString(), false, null);


		WorkspaceEdit edit = getRenameEdit(cu, pos, "TT");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);

		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.toURI(cu))),
				"package test1;\n" +
				"public class A<TT> {\n" +
				"	private TT t;\n" +
				"	public TT get() { return t; }\n" +
				"}\n"
				);
	}


	@Test
	public void testRenameTypeParameterInMethod() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class B<T> {\n",
				"	private T t;\n",
				"	public <U|* extends Number> U inspect(U u) { return u; }\n",
				"}\n"
		};

		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "UU");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);

		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.toURI(cu))),
				"package test1;\n" +
				"public class B<T> {\n" +
				"	private T t;\n" +
				"	public <UU extends Number> UU inspect(UU u) { return u; }\n" +
				"}\n"
				);
	}

	@Test
	public void testRenameJavadoc() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class E {\n",
				"	/**\n",
				"	 *@param i int\n",
				"	 */\n",
				"   public int foo(int i|*) {\n",
				"		E e = new E();\n",
				"		e.foo();\n",
				"   }\n",
				"}\n"
		};
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cu, pos, "i2");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 1);
		assertEquals(TextEditUtil.apply(builder.toString(), edit.getChanges().get(JDTUtils.toURI(cu))),
				"package test1;\n" +
				"public class E {\n" +
				"	/**\n" +
				"	 *@param i2 int\n" +
				"	 */\n" +
				"   public int foo(int i2) {\n" +
				"		E e = new E();\n" +
				"		e.foo();\n" +
				"   }\n" +
				"}\n"
				);
	}

	@Test
	public void testRenamePackage() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		IPackageFragment pack2 = sourceFolder.createPackageFragment("parent.test2", false, null);

		String[] codes1= {
				"package test1;\n",
				"import parent.test2.B;\n",
				"public class A {\n",
				"   public void foo(){\n",
				"		B b = new B();\n",
				"		b.foo();\n",
				"	}\n",
				"}\n"
		};

		String[] codes2 = {
				"package parent.test2|*;\n",
				"public class B {\n",
				"	public B() {}\n",
				"   public void foo() {}\n",
				"}\n"
		};
		StringBuilder builderA = new StringBuilder();
		mergeCode(builderA, codes1);
		ICompilationUnit cuA = pack1.createCompilationUnit("A.java", builderA.toString(), false, null);

		StringBuilder builderB = new StringBuilder();
		Position pos = mergeCode(builderB, codes2);
		ICompilationUnit cuB = pack2.createCompilationUnit("B.java", builderB.toString(), false, null);

		WorkspaceEdit edit = getRenameEdit(cuB, pos, "parent.newpackage");
		assertNotNull(edit);

		List<Either<TextDocumentEdit, ResourceOperation>> resourceChanges = edit.getDocumentChanges();

		assertEquals(5, resourceChanges.size());

		List<TextEdit> testChangesA = new LinkedList<>();
		testChangesA.addAll(resourceChanges.get(0).getLeft().getEdits());

		List<TextEdit> testChangesB = new LinkedList<>();
		testChangesB.addAll(resourceChanges.get(1).getLeft().getEdits());

		String expectedA =
				"package test1;\n" +
				"import parent.newpackage.B;\n" +
				"public class A {\n" +
				"   public void foo(){\n" +
				"		B b = new B();\n" +
				"		b.foo();\n" +
				"	}\n" +
				"}\n";

		String expectedB =
				"package parent.newpackage;\n" +
				"public class B {\n" +
				"	public B() {}\n" +
				"   public void foo() {}\n" +
				"}\n";
		assertEquals(expectedA, TextEditUtil.apply(builderA.toString(), testChangesA));
		assertEquals(expectedB, TextEditUtil.apply(builderB.toString(), testChangesB));

		//moved package
		CreateFile resourceChange = (CreateFile) resourceChanges.get(2).getRight();
		assertEquals(ResourceUtils.fixURI(pack2.getResource().getRawLocationURI()).replaceFirst("test2[/]?", "newpackage/.temp"), resourceChange.getUri());

		//moved class B
		RenameFile resourceChange2 = (RenameFile) resourceChanges.get(3).getRight();
		assertEquals(ResourceUtils.fixURI(cuB.getResource().getRawLocationURI()), resourceChange2.getOldUri());
		assertEquals(ResourceUtils.fixURI(cuB.getResource().getRawLocationURI()).replace("test2", "newpackage"), resourceChange2.getNewUri());
	}

	// https://github.com/redhat-developer/vscode-java/issues/2433
	@Test
	public void testRenameRecordField() throws Exception {
		String name = "java17";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertEquals("17", getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("/src/main/java"));
		clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isResourceOperationSupported()).thenReturn(false);
		Preferences p = mock(Preferences.class);
		Mockito.lenient().when(p.getProjectConfigurations()).thenReturn(null);
		when(preferenceManager.getPreferences()).thenReturn(p);
		when(p.isRenameEnabled()).thenReturn(true);
		handler = new RenameHandler(preferenceManager);
		IType type = javaProject.findType("test1.Test");
		assertNotNull(type);
		ICompilationUnit cu = type.getCompilationUnit();
		Position pos = new Position(1, 29);
		WorkspaceEdit edit = getRenameEdit(cu, pos, "value2");
		assertNotNull(edit);
		assertEquals(edit.getChanges().size(), 2);
		ICompilationUnit mainCu = javaProject.findType("test1.Main").getCompilationUnit();
		assertNotNull(mainCu);
		List<TextEdit> change = edit.getChanges().get(JDTUtils.toURI(mainCu));
		assertNotNull(change);
		String text = TextEditUtil.apply(mainCu.getSource(), change);
		/* @formatter:off */
		String expected = "package test1;\n"
				+ "public class Main {\n"
				+ "    public static void main(String[] args) {\n"
				+ "        Test instance = new Test(2);\n"
				+ "        System.out.println(instance.value2());\n"
				+ "    }\n"
				+ "}\n";
		/* @formatter:on */
		assertEquals(ResourceUtils.dos2Unix(expected), ResourceUtils.dos2Unix(text));
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
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(JDTUtils.toURI(cu));

		RenameParams params = new RenameParams(identifier, pos, newName);
		return handler.rename(params, monitor);
	}
}
