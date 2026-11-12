/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.jsh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.jdt.ls.core.internal.jsh.JshSnippetClassifier.Kind;
import org.junit.jupiter.api.Test;

public class JshSnippetClassifierTest {

	@Test
	public void testEmptyIsStatement() {
		assertEquals(Kind.STATEMENT, JshSnippetClassifier.classify(""));
		assertEquals(Kind.STATEMENT, JshSnippetClassifier.classify("   \n  "));
	}

	@Test
	public void testPlainImport() {
		assertEquals(Kind.IMPORT, JshSnippetClassifier.classify("import java.util.List;"));
	}

	@Test
	public void testStaticImport() {
		assertEquals(Kind.IMPORT, JshSnippetClassifier.classify("import static java.util.Collections.emptyList;"));
	}

	@Test
	public void testIdentifierStartingWithImportIsNotAnImport() {
		// "importer" must not be mistaken for the "import" keyword by a naive prefix check.
		assertEquals(Kind.STATEMENT, JshSnippetClassifier.classify("int importer = 1;"));
	}

	@Test
	public void testVariableDeclarationIsAStatementNotADeclaration() {
		// Deliberately a STATEMENT, not DECLARATION: a bare "String s = ...;" at the top level of a
		// .jsh buffer is NOT a valid class body declaration on its own (fields need no initializer
		// restrictions normally, but this exercises that our classifier routes it the same way v1
		// already handled variables - through __run__ - rather than promoting it).
		assertEquals(Kind.STATEMENT, JshSnippetClassifier.classify("String greeting = \"hi\";"));
	}

	@Test
	public void testBareStatementIsAStatement() {
		assertEquals(Kind.STATEMENT, JshSnippetClassifier.classify("System.out.println(\"hi\");"));
	}

	@Test
	public void testMethodDeclarationIsADeclaration() {
		assertEquals(Kind.DECLARATION, JshSnippetClassifier.classify("void foo() { System.out.println(1); }"));
	}

	@Test
	public void testClassDeclarationIsADeclaration() {
		assertEquals(Kind.DECLARATION, JshSnippetClassifier.classify("class Foo { int x; }"));
	}

	@Test
	public void testIfStatementWithBlockIsNotMisclassifiedAsADeclaration() {
		// The exact ambiguity a naive token-pattern heuristic (not a real parse) would get wrong:
		// "if (x) { }" has the same "(...) { }" shape as a method declaration.
		assertEquals(Kind.STATEMENT, JshSnippetClassifier.classify("if (true) { System.out.println(1); }"));
	}

	@Test
	public void testForStatementIsNotMisclassifiedAsADeclaration() {
		assertEquals(Kind.STATEMENT, JshSnippetClassifier.classify("for (int i = 0; i < 1; i++) { }"));
	}

	@Test
	public void testIncompleteMethodDeclarationFallsBackToStatement() {
		// The chunk the user is actively typing - not yet a parseable declaration - must default to
		// STATEMENT (the permissive destination), not crash or wrongly claim DECLARATION.
		assertEquals(Kind.STATEMENT, JshSnippetClassifier.classify("void printSumm"));
	}

}
