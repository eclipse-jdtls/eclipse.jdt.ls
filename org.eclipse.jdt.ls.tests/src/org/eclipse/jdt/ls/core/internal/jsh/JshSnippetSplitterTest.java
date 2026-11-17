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

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.ls.core.internal.jsh.JshSnippetSplitter.Chunk;
import org.junit.jupiter.api.Test;

public class JshSnippetSplitterTest {

	private static List<String> texts(String source) {
		return JshSnippetSplitter.split(source).stream().map(c -> source.substring(c.start(), c.end())).toList();
	}

	private static void assertContiguousCoverage(String source) {
		List<Chunk> chunks = JshSnippetSplitter.split(source);
		int expectedStart = 0;
		for (Chunk chunk : chunks) {
			assertEquals(expectedStart, chunk.start(), "Chunks must contiguously partition the source with no gaps");
			expectedStart = chunk.end();
		}
		assertEquals(source.length(), expectedStart, "The last chunk must reach the end of the source");
	}

	@Test
	public void testEmptySourceYieldsOneEmptyChunk() {
		assertContiguousCoverage("");
		List<Chunk> chunks = JshSnippetSplitter.split("");
		assertEquals(1, chunks.size());
		assertEquals(0, chunks.get(0).start());
		assertEquals(0, chunks.get(0).end());
	}

	@Test
	public void testSingleStatementIsOneChunk() {
		String source = "int x = 1;";
		assertContiguousCoverage(source);
		assertEquals(List.of(source), texts(source));
	}

	@Test
	public void testMultipleStatementsSplitAtTopLevelSemicolons() {
		String source = "int x = 1;int y = 2;";
		assertContiguousCoverage(source);
		assertEquals(List.of("int x = 1;", "int y = 2;"), texts(source));
	}

	@Test
	public void testImportAndStatementAreSeparateChunks() {
		String source = "import java.util.List;\nList<String> l = List.of();";
		assertContiguousCoverage(source);
		List<String> chunks = texts(source);
		assertEquals(2, chunks.size());
		assertEquals("import java.util.List;", chunks.get(0));
		assertEquals("\nList<String> l = List.of();", chunks.get(1));
	}

	@Test
	public void testMethodDeclarationIsOneChunkDespiteInternalSemicolonsAndBraces() {
		String source = "void foo() { int x = 1; System.out.println(x); }";
		assertContiguousCoverage(source);
		assertEquals(List.of(source), texts(source));
	}

	@Test
	public void testTrailingIncompleteStatementIsItsOwnChunk() {
		String source = "int x = 1;\nSystem.out.";
		assertContiguousCoverage(source);
		List<String> chunks = texts(source);
		assertEquals(List.of("int x = 1;", "\nSystem.out."), chunks);
	}

	@Test
	public void testForLoopSemicolonsDoNotSplitTheStatement() {
		String source = "for (int i = 0; i < 10; i++) { System.out.println(i); }";
		assertContiguousCoverage(source);
		assertEquals(List.of(source), texts(source));
	}

	@Test
	public void testArrayInitializerBraceDoesNotEndTheStatementEarly() {
		String source = "int[] a = {1, 2, 3};\nint y = 4;";
		assertContiguousCoverage(source);
		List<String> chunks = texts(source);
		assertEquals(List.of("int[] a = {1, 2, 3};", "\nint y = 4;"), chunks);
	}

	@Test
	public void testLambdaWithBlockBodyInsideAMethodCallDoesNotSplitTheStatement() {
		String source = "list.forEach(item -> { System.out.println(item); });";
		assertContiguousCoverage(source);
		assertEquals(List.of(source), texts(source));
	}

	@Test
	public void testStringAndTextBlockContentIsNotMistakenForStructure() {
		String source = "String s = \"a;b{c\";\nString t = \"\"\"\n};\n\"\"\";";
		assertContiguousCoverage(source);
		List<String> chunks = texts(source);
		assertEquals(2, chunks.size());
	}

	@Test
	public void testRealisticMixedContentSplitsIntoExpectedChunkCount() {
		String source = "import java.time.LocalDate;\n"
				+ "import java.util.List;\n"
				+ "\n"
				+ "String greeting = \"Hello\";\n"
				+ "List<String> items = List.of(\"a\", \"b\");\n"
				+ "\n"
				+ "void printSummary(String msg, List<String> list) {\n"
				+ "    System.out.println(msg);\n"
				+ "}\n"
				+ "\n"
				+ "printSummary(greeting, items);\n";
		assertContiguousCoverage(source);
		List<String> chunks = texts(source).stream().map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.toList());
		assertEquals(6, chunks.size(), "Expected 2 imports + 2 var decls + 1 method decl + 1 statement, got: " + chunks);
	}

}
