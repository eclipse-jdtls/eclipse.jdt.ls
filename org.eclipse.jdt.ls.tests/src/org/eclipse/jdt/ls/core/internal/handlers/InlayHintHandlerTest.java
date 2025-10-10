/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

public class InlayHintHandlerTest extends AbstractCompilationUnitBasedTest {

	@Before
	public void initPreferences() throws Exception{
		preferences.setInlayHintsSuppressedWhenSameNameNumberedParameter(true);
	}

	@Test
	public void testNoneMode() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.NONE);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(int i) {}\n" +
			"	void bar() {\n" +
			"		foo(123);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertTrue(inlayHints.isEmpty());
	}

	@Test
	public void testOutOfRange() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(int i) {}\n" +
			"	void bar() {\n" +
			"		foo(123);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(1, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertTrue(inlayHints.isEmpty());
	}

	@Test
	public void testBooleanLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(bool b) {}\n" +
			"	void bar() {\n" +
			"		foo(true);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("b:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testCharacterLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(char c) {}\n" +
			"	void bar() {\n" +
			"		foo('c');\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("c:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testNullLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(char c) {}\n" +
			"	void bar() {\n" +
			"		foo(null);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("c:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testNumberLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(int i) {}\n" +
			"	void bar() {\n" +
			"		foo(123);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("i:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testStringLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(String s) {}\n" +
			"	void bar() {\n" +
			"		foo(\"s\");\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("s:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testTypeLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(Class<T> clazz) {}\n" +
			"	void bar() {\n" +
			"		foo(Foo.class);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("clazz:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testNoInlayHintForNonLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(Double d) {}\n" +
			"	void bar() {\n" +
			"		Double d = 0.0;\n" +
			"		foo(d);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertTrue(inlayHints.isEmpty());
	}

	@Test
	public void testAllMode() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(Double doubleParam) {}\n" +
			"	void foo(Integer intParam) {}\n" +
			"	void bar() {\n" +
			"		Double d = 0.0;\n" +
			"		Integer i = 0;\n" +
			"		foo(d);\n" +
			"		foo(i);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(9, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("doubleParam:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("intParam:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testVarargs() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(String... args) {}\n" +
			"	void bar() {\n" +
			"		foo(\"1\", \"2\");\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("...args:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testVarargs2() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo2(Integer i, String... args) {}\n" +
			"	void bar() {\n" +
			"		foo2(1);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("i:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testNoInlayHintsWhenNamesAreEqual() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(Double d) {}\n" +
			"	void bar() {\n" +
			"		Double d = 0.0;\n" +
			"		foo(d);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertTrue(inlayHints.isEmpty());
	}

	@Test
	public void testNoInlayHintsForCastExpression() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(String str) {}\n" +
			"	void bar() {\n" +
			"		String str = \"\";\n" +
			"		foo((CharSequence) str);\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertTrue(inlayHints.isEmpty());
	}

	@Test
	public void testNewExpression() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	Foo(String foo) {}\n" +
			"	void bar() {\n" +
			"		Foo foo = new Foo(\"foo\");\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("foo:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testEnumConstant() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public enum Foo {\n" +
			"	I(\"i\"), J(\"j\");\n" +
			"	String id;\n" +
			"	Foo(String id) {\n" +
			"		this.id = id;" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("id:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("id:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testRecord() throws Exception {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		importProjects("eclipse/java16");
		IProject proj = WorkspaceHelper.getProject("java16");
		IJavaProject javaProject = JavaCore.create(proj);
		ICompilationUnit unit = (ICompilationUnit) javaProject.findElement(new Path("foo/bar/Bar.java"));
		unit.becomeWorkingCopy(null);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(12, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(6, inlayHints.size());
		assertEquals("fromNodeId:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("toNodeId:", inlayHints.get(1).getLabel().getLeft());
		assertEquals("fromPoint:", inlayHints.get(2).getLabel().getLeft());
		assertEquals("toPoint:", inlayHints.get(3).getLabel().getLeft());
		assertEquals("length:", inlayHints.get(4).getLabel().getLeft());
		assertEquals("profile:", inlayHints.get(5).getLabel().getLeft());
	}

	@Test
	public void testComplexExpressions() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(String s) {}\n" +
			"	void bar(int i) {\n" +
			"		foo(switch (i) {\n" +
			"			case 1:\n" +
			"				yield \"foo\";\n" +
			"			default:\n" +
			"				yield \"unknown\"\n" +
			"		});\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(10, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("s:", inlayHints.get(0).getLabel().getLeft());
		assertEquals(3, inlayHints.get(0).getPosition().getLine());
		assertEquals(6, inlayHints.get(0).getPosition().getCharacter());
	}

	@Test
	public void testConstructorInvocation() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	public Foo(Integer a) {\n" +
			"		this(1, 2);\n" +
			"	}\n" +
			"	public Foo(Integer a, Integer b) {\n" +
			"	}\n"+
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("a:", inlayHints.get(0).getLabel().getLeft());
		assertEquals(2, inlayHints.get(0).getPosition().getLine());
		assertEquals(7, inlayHints.get(0).getPosition().getCharacter());

		assertEquals("b:", inlayHints.get(1).getLabel().getLeft());
		assertEquals(2, inlayHints.get(1).getPosition().getLine());
		assertEquals(10, inlayHints.get(1).getPosition().getCharacter());
	}

	@Test
	public void testSuperConstructorInvocation() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	public Foo(Integer a, Integer b) {}\n" +
			"}\n" +
			"class Bar extends Foo {\n" +
			"	public Bar() {\n" +
			"		super(1, 2);\n" +
			"	}\n" +
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(7, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("a:", inlayHints.get(0).getLabel().getLeft());
		assertEquals(5, inlayHints.get(0).getPosition().getLine());
		assertEquals(8, inlayHints.get(0).getPosition().getCharacter());

		assertEquals("b:", inlayHints.get(1).getLabel().getLeft());
		assertEquals(5, inlayHints.get(1).getPosition().getLine());
		assertEquals(11, inlayHints.get(1).getPosition().getCharacter());
	}

	@Test
	public void testSuperMethodInvocation() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	public void foo(Object obj){}\n" +
			"}\n" +
			"class Bar extends Foo {\n" +
			"	public void bar() {\n" +
			"		Bar.super.foo(1);\n" +
			"	}\n" +
			"}\n"
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(7, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		assertEquals("obj:", inlayHints.get(0).getLabel().getLeft());
		assertEquals(5, inlayHints.get(0).getPosition().getLine());
		assertEquals(16, inlayHints.get(0).getPosition().getCharacter());
	}

	@Test
	public void testDisabledLambdaParameterTypeHints() throws Exception {
		setupEclipseProject("java11");
		preferences.setInlayHintsParameterTypesEnabled(false);
		ICompilationUnit unit = getWorkingCopy("src/Foo.java",
		"""
			import java.util.stream.Stream;
			public class Foo {
				void bar() {
					Stream.of(2, 3, 5, 7).map(n -> n * 2);
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));

		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(inlayHints.toString(), 0, inlayHints.size());
	}

	@Test
	public void testLambdaParameterTypeHints() throws Exception {
		setupEclipseProject("java11");
		preferences.setInlayHintsParameterTypesEnabled(true);
		ICompilationUnit unit = getWorkingCopy("src/Foo.java",
		"""
			import java.util.Map;
			import java.util.HashMap;
			public class Foo {
				void bar() {
					Map<String, Integer> map = new HashMap<>();
					map.forEach((key, value) -> System.out.println(key + value));
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(8, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(inlayHints.toString(), 2, inlayHints.size());
		// Should show type hints for lambda parameters only (no 'action:' parameter hint)
		assertEquals("String", inlayHints.get(0).getLabel().getLeft());
		assertEquals("Integer", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testLambdaExplicitTypeNoHints() throws Exception {
		setupEclipseProject("java11");
		preferences.setInlayHintsParameterTypesEnabled(true);
		ICompilationUnit unit = getWorkingCopy("src/Foo.java",
		"""
			import java.util.stream.Stream;
			public class Foo {
				void bar() {
					// Explicit type - should not show inlay hint
					Stream.of(2, 3, 5, 7).map((Integer n) -> n * 2);
					// Implicit type - should show inlay hint
					Stream.of(2, 3, 5, 7).map(x -> x * 2);
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(9, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
		// Should only show hint for the implicit type lambda parameter
		assertEquals("Integer", inlayHints.get(0).getLabel().getLeft());
		assertEquals(6, inlayHints.get(0).getPosition().getLine());
	}

	@Test
	public void testVariableTypeHints() throws Exception {
		setupEclipseProject("java11");
		preferences.setInlayHintsVariableTypesEnabled(true);
		ICompilationUnit unit = getWorkingCopy("src/Foo.java", """
				public class Foo {

					@SuppressWarnings("unused")
				    public void varImplicitTypes() {
				        //Should show var inlayhints
				        var _greeting= getGreeting();
				        var _string = "foo" + "1";

				        //Should NOT show var inlayhints
				        var _int = 1;
				        var _boolean = true;
				        var _double = 1.0;
				        var _float = 1.0f;
				        var _char = 'a';
				        var _long = 1L;
				        var _object = new Object();
				        var _exception= new RuntimeException("Foo");
				        var _castString= (String) getGreeting();
				        var _byte= (byte) 1;
				        var _short = (short) 1;
				        var _array = new int[1];
				        var _arrayInit = new int[] {1, 2, 3};
					}

				    private String getGreeting() {
				        return "Hello";
				    }
				}
				""");
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(28, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());

		// Should show inlay hints for _greeting and _string only
		assertEquals(2, inlayHints.size());
		boolean foundGreeting = false;
		boolean foundString = false;
		for (InlayHint hint : inlayHints) {
			String label = hint.getLabel().getLeft();
			Position pos = hint.getPosition();
			if (label != null && label.contains(": String")) {
				// _greeting or _string
				if (pos.getLine() == 5) {
					foundGreeting = true; // var _greeting= getGreeting();
				}
				if (pos.getLine() == 6) {
					foundString = true;   // var _string = "foo" + "1";
				}
			}
		}
		assertTrue("Should find inlay hint for _greeting", foundGreeting);
		assertTrue("Should find inlay hint for _string", foundString);
	}

	@Test
	public void testSamePrefixParameterFilter() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"""
			import java.util.stream.Stream;
			class Foo {
				public static void process(String s1, String s2, String s3) {}
				public static void main(String[] args) {
					print("first", "second", "third");
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());

		// Should not show inlay hints for methods where all parameters have same prefix+number pattern
		assertTrue("Should not show inlay hints for methods with same prefix+number parameters", inlayHints.isEmpty());
	}

	@Test
	public void testSamePrefixParameterFilterLongerPrefix() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"""
			public class Foo {
				void process(String param1, String param2, String param3) {}
				void bar() {
					process("first", "second", "third");
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());

		// Should not show inlay hints for methods where all parameters have same prefix+number pattern
		assertTrue("Should not show inlay hints for methods with same prefix+number parameters", inlayHints.isEmpty());
	}

	@Test
	public void testSamePrefixParameterFilterMixed() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"""
			public class Foo {
				void mixedParams(String s1, String description, String s2) {}
				void descriptiveParams(String name, String description, String value) {}
				void bar() {
					mixedParams("first", "desc", "second");
					descriptiveParams("name", "desc", "value");
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(8, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());

		// Should show hints for both methods since neither has ALL parameters following same prefix+number pattern
		assertEquals(6, inlayHints.size());
		assertEquals("s1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("description:", inlayHints.get(1).getLabel().getLeft());
		assertEquals("s2:", inlayHints.get(2).getLabel().getLeft());
		assertEquals("name:", inlayHints.get(3).getLabel().getLeft());
		assertEquals("description:", inlayHints.get(4).getLabel().getLeft());
		assertEquals("value:", inlayHints.get(5).getLabel().getLeft());
	}

	@Test
	public void testSamePrefixParameterFilterSingleParam() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"""
			public class Foo {
				void singleParam(String s1) {}
				void bar() {
					singleParam("test");
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());

		// Should show hints for single parameter methods (filter only applies to methods with 2+ params)
		assertEquals(1, inlayHints.size());
		assertEquals("s1:", inlayHints.get(0).getLabel().getLeft());
	}

	@Test
	public void testSamePrefixParameterFilterDifferentPrefixes() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"""
			public class Foo {
				void differentPrefixes(String s1, String t1, String u1) {}
				void bar() {
					differentPrefixes("first", "second", "third");
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());

		// Should show hints since parameters have different prefixes (s, t, u)
		assertEquals(3, inlayHints.size());
		assertEquals("s1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("t1:", inlayHints.get(1).getLabel().getLeft());
		assertEquals("u1:", inlayHints.get(2).getLabel().getLeft());
	}

	@Test
	public void testSamePrefixParameterFilterNotStartingWithOne() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"""
			public class Foo {
				void startingWithZero(String s0, String s1, String s2) {}
				void startingWithTwo(String s2, String s3, String s4) {}
				void bar() {
					startingWithZero("first", "second", "third");
					startingWithTwo("first", "second", "third");
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(8, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());

		// Should show hints since parameters don't start with 1
		assertEquals(6, inlayHints.size());
		assertEquals("s0:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("s1:", inlayHints.get(1).getLabel().getLeft());
		assertEquals("s2:", inlayHints.get(2).getLabel().getLeft());
		assertEquals("s2:", inlayHints.get(3).getLabel().getLeft());
		assertEquals("s3:", inlayHints.get(4).getLabel().getLeft());
		assertEquals("s4:", inlayHints.get(5).getLabel().getLeft());
	}

	@Test
	public void testSamePrefixParameterFilterNonConsecutive() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"""
			public class Foo {
				void nonConsecutive(String s1, String s3, String s5) {}
				void bar() {
					nonConsecutive("first", "second", "third");
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());

		// Should show hints since parameters are not consecutive (1, 3, 5)
		assertEquals(3, inlayHints.size());
		assertEquals("s1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("s3:", inlayHints.get(1).getLabel().getLeft());
		assertEquals("s5:", inlayHints.get(2).getLabel().getLeft());
	}

	@Test
	public void testDisableSameNamedNumberedParameterFilter() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		preferences.setInlayHintsSuppressedWhenSameNameNumberedParameter(false);
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"""
			public class Foo {
				void samePrefixParameter(String s1, String s2, String s3) {}
				void bar() {
					samePrefixParameter("first", "second", "third");
				}
			}
			"""
		);
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(3, inlayHints.size());
		assertEquals("s1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("s2:", inlayHints.get(1).getLabel().getLeft());
		assertEquals("s3:", inlayHints.get(2).getLabel().getLeft());
	}

}