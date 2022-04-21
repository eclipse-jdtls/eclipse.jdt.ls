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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.proposed.InlayHint;
import org.eclipse.lsp4j.proposed.InlayHintParams;
import org.junit.Test;

public class InlayHintHandlerTest extends AbstractCompilationUnitBasedTest {

	@Test
	public void testNoneMode() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.NONE);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(int val1, int val2) {}\n" +
			"	void bar() {\n" +
			"		foo(123);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
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
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(int val1, int val2) {}\n" +
			"	void bar() {\n" +
			"		foo(1, 2);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
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
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(boolean val1, boolean val2) {}\n" +
			"	void bar() {\n" +
			"		foo(true, false);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("val1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("val2:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testCharacterLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(char val1, char val2) {}\n" +
			"	void bar() {\n" +
			"		foo('c', 'c');\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("val1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("val2:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testNullLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(char val1, char val2) {}\n" +
			"	void bar() {\n" +
			"		foo(null, null);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("val1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("val2:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testNumberLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(int val1, int val2) {}\n" +
			"	void bar() {\n" +
			"		foo(1, 2);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("val1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("val2:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testStringLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(String val1, String val2) {}\n" +
			"	void bar() {\n" +
			"		foo(\"s1\", \"s2\");\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("val1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("val2:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testTypeLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(Class<T> val1, Class<T> val2) {}\n" +
			"	void bar() {\n" +
			"		foo(Foo.class, Foo.class);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("val1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("val2:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testNoInlayHintForNonLiteral() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(Double val1, Double val2) {}\n" +
			"	void bar() {\n" +
			"		Double d = 0.0;\n" +
			"		foo(d, d);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
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
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(Double doubleParam, Integer intParam) {}\n" +
			"	void foo(Integer intParam) {}\n" +
			"	void bar() {\n" +
			"		Double d = 0.0;\n" +
			"		Integer i = 0;\n" +
			"		foo(d, i);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
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
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(String... args) {}\n" +
			"	void bar() {\n" +
			"		foo(\"1\", \"2\");\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
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
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(Integer val, String... args) {}\n" +
			"	void bar() {\n" +
			"		foo(1);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(0, inlayHints.size());
	}

	@Test
	public void testVarargs3() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(String... args) {}\n" +
			"	void bar() {\n" +
			"		foo(\"1\");\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(0, inlayHints.size());
	}

	@Test
	public void testVarargs4() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(Integer val, String... args) {}\n" +
			"	void bar() {\n" +
			"		foo(1, \"a\");\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("val:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("...args:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testNoInlayHintsWhenNamesAreEqual() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(Double val) {}\n" +
			"	void bar() {\n" +
			"		Double val = 0.0;\n" +
			"		foo(val);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
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
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(String str) {}\n" +
			"	void bar() {\n" +
			"		String str = \"\";\n" +
			"		foo((CharSequence) str);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
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
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	Foo(String foo, String bar) {}\n" +
			"	void bar() {\n" +
			"		Foo foo = new Foo(\"foo\", \"bar\");\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("foo:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("bar:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testEnumConstant() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public enum Foo {\n" +
			"	I(\"i\", \"j\");\n" +
			"	String id;\n" +
			"	Foo(String id, String name) {\n" +
			"		this.id = id;" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("id:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("name:", inlayHints.get(1).getLabel().getLeft());
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
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("length:", inlayHints.get(0).getLabel().getLeft());
		assertEquals("width:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testComplexExpressions1() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(String type, Integer val) {}\n" +
			"	void bar(int i) {\n" +
			"		foo(switch (i) {\n" +
			"			case 1:\n" +
			"				yield \"foo\";\n" +
			"			default:\n" +
			"				yield \"unknown\"\n" +
			"		}, 1);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(10, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("type:", inlayHints.get(0).getLabel().getLeft());
		assertEquals(3, inlayHints.get(0).getPosition().getLine());
		assertEquals(6, inlayHints.get(0).getPosition().getCharacter());
		assertEquals("val:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testComplexExpressions2() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.LITERALS);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(String path, String source) {}\n" +
			"	void bar() {\n" +
			"		foo(\"path\", \"a\" + \"b\");\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(1, inlayHints.size());
	}

	@Test
	public void testConstructorInvocation() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	public Foo(Integer a) {\n" +
			"		this(1, 2);\n" +
			"	}\n" +
			"	public Foo(Integer val1, Integer val2) {\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(6, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("val1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals(2, inlayHints.get(0).getPosition().getLine());
		assertEquals(7, inlayHints.get(0).getPosition().getCharacter());

		assertEquals("val2:", inlayHints.get(1).getLabel().getLeft());
		assertEquals(2, inlayHints.get(1).getPosition().getLine());
		assertEquals(10, inlayHints.get(1).getPosition().getCharacter());
	}

	@Test
	public void testSuperConstructorInvocation() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	public Foo(Integer val1, Integer val2) {}\n" +
			"}\n" +
			"class Bar extends Foo {\n" +
			"	public Bar() {\n" +
			"		super(1, 2);\n" +
			"	}\n" +
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(7, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("val1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals(5, inlayHints.get(0).getPosition().getLine());
		assertEquals(8, inlayHints.get(0).getPosition().getCharacter());

		assertEquals("val2:", inlayHints.get(1).getLabel().getLeft());
		assertEquals(5, inlayHints.get(1).getPosition().getLine());
		assertEquals(11, inlayHints.get(1).getPosition().getCharacter());
	}

	@Test
	public void testSuperMethodInvocation() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	public void foo(Object obj1, Object obj2){}\n" +
			"}\n" +
			"class Bar extends Foo {\n" +
			"	public void bar() {\n" +
			"		Bar.super.foo(1, 2);\n" +
			"	}\n" +
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(7, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertEquals(2, inlayHints.size());
		assertEquals("obj1:", inlayHints.get(0).getLabel().getLeft());
		assertEquals(5, inlayHints.get(0).getPosition().getLine());
		assertEquals(16, inlayHints.get(0).getPosition().getCharacter());
		assertEquals("obj2:", inlayHints.get(1).getLabel().getLeft());
	}

	@Test
	public void testMeaninglessName() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(int x, int y) {}\n" +
			"	void bar() {\n" +
			"		foo(1, 2);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertTrue(inlayHints.isEmpty());
	}

	@Test
	public void testMethodWithOneParam() throws JavaModelException {
		preferences.setInlayHintsParameterMode(InlayHintsParameterMode.ALL);
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n" +
			"	void foo(int val) {}\n" +
			"	void bar() {\n" +
			"		foo(1);\n" +
			"	}\n"+
			"}\n"
		);
		unit.getResource().getLocationURI().toString();
		InlayHintsHandler handler = new InlayHintsHandler(preferenceManager);
		InlayHintParams params = new InlayHintParams();
		params.setTextDocument(new TextDocumentIdentifier(unit.getResource().getLocationURI().toString()));
		params.setRange(new Range(new Position(0, 0), new Position(5, 0)));
		List<InlayHint> inlayHints = handler.inlayHint(params, new NullProgressMonitor());
		assertTrue(inlayHints.isEmpty());
	}
}
