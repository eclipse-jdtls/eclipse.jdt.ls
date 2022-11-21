/*******************************************************************************
* Copyright (c) 2018 Microsoft Corporation and others.
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrepareRenameHandlerTest extends AbstractProjectsManagerBasedTest {

	private PrepareRenameHandler handler;

	private ClientPreferences clientPreferences;

	private IPackageFragmentRoot sourceFolder;

	@Before
	public void setup() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		this.clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isResourceOperationSupported()).thenReturn(false);
		Preferences p = mock(Preferences.class);
		when(preferenceManager.getPreferences()).thenReturn(p);
		when(p.isRenameEnabled()).thenReturn(true);
		handler = new PrepareRenameHandler(preferenceManager);
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

		Either<Range, PrepareRenameResult> result = prepareRename(cu, pos, "newname");

		assertNotNull(result.getLeft());
		assertTrue(result.getLeft().getStart().getLine() > 0);
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

		Either<Range, PrepareRenameResult> result = prepareRename(cu, pos, "newname");
		assertNotNull(result.getLeft());
		assertTrue(result.getLeft().getStart().getLine() > 0);
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

		Either<Range, PrepareRenameResult> result = prepareRename(cu, pos, "newname");
		assertNotNull(result.getLeft());
		assertTrue(result.getLeft().getStart().getLine() > 0);
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

		Either<Range, PrepareRenameResult> result = prepareRename(cu, pos, "newname");
		assertNotNull(result.getLeft());
		assertTrue(result.getLeft().getStart().getLine() > 0);
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

		Either<Range, PrepareRenameResult> result = prepareRename(cu, pos, "Newname");
		assertNotNull(result.getLeft());
		assertTrue(result.getLeft().getStart().getLine() > 0);
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


		Either<Range, PrepareRenameResult> result = prepareRename(cu, pos, "TT");
		assertNotNull(result.getLeft());
		assertTrue(result.getLeft().getStart().getLine() > 0);
	}


	@Test
	public void testRenameTypeParameterInMethod() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String[] codes = {
				"package test1;\n",
				"public class B<T> {\n",
				"	private T t;\n",
				"	public <U|* extends Number> inspect(U u) { return u; }\n",
				"}\n"
		};

		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", builder.toString(), false, null);

		Either<Range, PrepareRenameResult> result = prepareRename(cu, pos, "UU");
		assertNotNull(result.getLeft());
		assertTrue(result.getLeft().getStart().getLine() > 0);
	}

	@Test
	public void testRenameLambdaParameter() throws JavaModelException, BadLocationException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		// @formatter:off
		String[] codes =
			{
				"package test1;\n",
				"import java.util.function.Function;\n",
				"public class Test {\n",
				"    Function<Integer, String> f = i|* -> \"\" + i;\n",
				"}\n"
			};
		// @formatter:on
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, codes);
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", builder.toString(), false, null);

		Either<Range, PrepareRenameResult> result = prepareRename(cu, pos, "j");
		assertNotNull(result.getLeft());
		assertTrue(result.getLeft().getStart().getLine() > 0);
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

		Either<Range, PrepareRenameResult> result = prepareRename(cu, pos, "i2");
		assertNotNull(result.getLeft());
		assertTrue(result.getLeft().getStart().getLine() > 0);
	}

	@Test(expected = ResponseErrorException.class)
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
		pack1.createCompilationUnit("A.java", builderA.toString(), false, null);

		StringBuilder builderB = new StringBuilder();
		Position pos = mergeCode(builderB, codes2);
		ICompilationUnit cuB = pack2.createCompilationUnit("B.java", builderB.toString(), false, null);

		prepareRename(cuB, pos, "parent.newpackage");
	}

	@Test(expected = ResponseErrorException.class)
	public void testRenameMiddleOfPackage() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("ex.amples", false, null);

		//@formatter:off
		String[] content = {
			"package |*ex.amples;\n",
			"public class A {}\n"
		};
		//@formatter:on
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, content);
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", builder.toString(), false, null);

		prepareRename(cu, pos, "ex.am.ple");

		//@formatter:off
		String[] content2 = {
			"package ex.|*amples;\n",
			"public class A {}\n"
		};
		//@formatter:on
		builder = new StringBuilder();
		pos = mergeCode(builder, content2);
		cu = pack1.createCompilationUnit("A.java", builder.toString(), false, null);

		prepareRename(cu, pos, "ex.am.ple");
	}

	@Test(expected = ResponseErrorException.class)
	public void testRenameClassFile() throws JavaModelException, BadLocationException {
		testRenameClassFile("Ex|*ception");
	}

	@Test(expected = ResponseErrorException.class)
	public void testRenameFQCNClassFile() throws JavaModelException, BadLocationException {
		testRenameClassFile("java.lang.Ex|*ception");
	}

	@Test(expected = ResponseErrorException.class)
	public void testRenameBinaryPackage() throws JavaModelException, BadLocationException {
		testRenameClassFile("java.net|*.URI");
	}

	@Test(expected = ResponseErrorException.class)
	public void testRenameImportDeclaration() throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("ex.amples", false, null);

		{
		//@formatter:off
		String[] content = {
			"package ex.amples;\n",
			"import java.ne|*t.URI;\n",
			"public class A {}\n"
		};
		//@formatter:on
			StringBuilder builder = new StringBuilder();
			Position pos = mergeCode(builder, content);
			ICompilationUnit cu = pack1.createCompilationUnit("A.java", builder.toString(), false, null);

			prepareRename(cu, pos, "");
		}
	}

	private void testRenameClassFile(String type) throws JavaModelException, BadLocationException {
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);

		IPackageFragment pack1 = sourceFolder.createPackageFragment("ex.amples", false, null);

		{
		//@formatter:off
		String[] content = {
			"package ex.amples;\n",
			"public class A extends "+type+"{}\n"
		};
		//@formatter:on
		StringBuilder builder = new StringBuilder();
		Position pos = mergeCode(builder, content);
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", builder.toString(), false, null);

		prepareRename(cu, pos, "MyException");
		}
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

	private Either<Range, PrepareRenameResult> prepareRename(ICompilationUnit cu, Position pos, String newName) {
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(JDTUtils.toURI(cu));

		TextDocumentPositionParams params = new TextDocumentPositionParams(identifier, pos);
		return handler.prepareRename(params, monitor);
	}
}
