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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.eclipse.jdt.ls.core.internal.jsh.JshSnippetClassifier.FieldInfo;
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

	/**
	 * Regression test: a plain {@code startsWith("import")} check is fooled by a leading comment -
	 * the chunk's text still includes it verbatim, even though the splitter's scanner skips
	 * comments when finding chunk boundaries.
	 */
	@Test
	public void testImportPrecededByALineCommentIsStillAnImport() {
		assertEquals(Kind.IMPORT, JshSnippetClassifier.classify("// note\nimport java.util.List;"));
	}

	@Test
	public void testImportPrecededByABlockCommentIsStillAnImport() {
		assertEquals(Kind.IMPORT, JshSnippetClassifier.classify("/* note */ import java.util.List;"));
	}

	@Test
	public void testIdentifierStartingWithImportIsNotAnImport() {
		// "importer" must not be mistaken for the "import" keyword by a naive prefix check - it's a
		// variable declaration (DECLARATION, since those are promoted), not an IMPORT.
		assertEquals(Kind.DECLARATION, JshSnippetClassifier.classify("int importer = 1;"));
	}

	/**
	 * Variable declarations are promoted (not left as STATEMENTs in __run__) so a later-declared
	 * method can see an earlier-declared script variable - the real JShell pattern CodeRabbit's
	 * review flagged ("int n = 2; int twice() { return n * 2; }" previously failed to resolve n).
	 */
	@Test
	public void testVariableDeclarationIsADeclaration() {
		assertEquals(Kind.DECLARATION, JshSnippetClassifier.classify("String greeting = \"hi\";"));
	}

	@Test
	public void testVarTypedVariableIsAStatementNotADeclaration() {
		// var is local-variable-only in Java - can't be promoted to a field, so this one stays local
		// to __run__ (a narrow, documented exception).
		assertEquals(Kind.STATEMENT, JshSnippetClassifier.classify("var greeting = \"hi\";"));
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

	@Test
	public void testParseAsFieldReturnsNullForNonFieldChunks() {
		assertNull(JshSnippetClassifier.parseAsField("System.out.println(1);"));
		assertNull(JshSnippetClassifier.parseAsField("void foo() { }"));
		assertNull(JshSnippetClassifier.parseAsField(""));
	}

	@Test
	public void testParseAsFieldExtractsNameAndVarness() {
		FieldInfo plain = JshSnippetClassifier.parseAsField("int n = 2;");
		assertEquals(java.util.List.of("n"), plain.names());
		assertFalse(plain.isVar());

		FieldInfo var = JshSnippetClassifier.parseAsField("var n = 2;");
		assertEquals(java.util.List.of("n"), var.names());
		assertTrue(var.isVar());
	}

	@Test
	public void testParseAsFieldExtractsMultipleFragmentNames() {
		FieldInfo info = JshSnippetClassifier.parseAsField("int a = 1, b = 2;");
		assertEquals(java.util.List.of("a", "b"), info.names());
	}

	@Test
	public void testReferencesAnyIdentifierFindsAMatchingToken() {
		assertTrue(JshSnippetClassifier.referencesAnyIdentifier("int m = n;", Set.of("n")));
		assertFalse(JshSnippetClassifier.referencesAnyIdentifier("int m = 5;", Set.of("n")));
	}

	@Test
	public void testReferencesAnyIdentifierDoesNotMatchASubstringOfALongerIdentifier() {
		// "nn" contains "n" as a substring, but isn't a reference to a variable named "n" - a
		// token-based check must not treat it as one.
		assertFalse(JshSnippetClassifier.referencesAnyIdentifier("int m = nn;", Set.of("n")));
	}

}
