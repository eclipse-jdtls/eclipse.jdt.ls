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
import org.junit.Test;

public class InlayHintHandlerTest extends AbstractCompilationUnitBasedTest {

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
}
