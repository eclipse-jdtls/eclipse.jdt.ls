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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.BaseDocumentLifeCycleHandler.DocumentMonitor;
import org.eclipse.jdt.ls.core.internal.handlers.SemanticTokensHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

public class JshSemanticTokensTest extends AbstractProjectsManagerBasedTest {

	/**
	 * Regression test: semantic tokens used to be returned straight from the synthetic wrapped
	 * source's AST with no translation at all - every token's position was wrong relative to the
	 * real .jsh buffer, scattered unpredictably once declarations get relocated by the Phase 11
	 * snippet-classification rewrite (worse than a simple constant shift). This checks that every
	 * returned token lands within the original document's actual line count, and that the
	 * delta-encoded stream is still well-formed (every line/character delta non-negative) after
	 * re-sorting into original-document order.
	 */
	@Test
	public void testSemanticTokensLandWithinOriginalDocumentBounds() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/SemanticTokens.jsh";

		String content = "import org.apache.commons.lang3.StringUtils;\n"
				+ "import java.util.Arrays;\n"
				+ "import java.util.List;\n"
				+ "\n"
				+ "String greeting = \"Hello\";\n"
				+ "List<String> items = Arrays.asList(\"a\", \"b\");\n"
				+ "\n"
				+ "void printSummary(String msg, List<String> list) {\n"
				+ "    System.out.println(StringUtils.upperCase(msg));\n"
				+ "}\n"
				+ "\n"
				+ "printSummary(greeting, items);\n";
		int lineCount = content.split("\n", -1).length;

		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			SemanticTokens tokens = SemanticTokensHandler.full(new NullProgressMonitor(),
					new SemanticTokensParams(new TextDocumentIdentifier(uri)), mock(DocumentMonitor.class));
			assertFalse(tokens.getData().isEmpty(), "Expected at least one semantic token for realistic .jsh content");

			List<Integer> data = tokens.getData();
			int line = 0;
			for (int i = 0; i + 4 < data.size(); i += 5) {
				int deltaLine = data.get(i);
				int deltaChar = data.get(i + 1);
				assertTrue(deltaLine >= 0, "Semantic token stream must have non-negative line deltas once re-sorted into original-document order");
				if (deltaLine == 0) {
					assertTrue(deltaChar >= 0, "Semantic token stream must have non-negative character deltas on the same line");
				}
				line += deltaLine;
				assertTrue(line < lineCount, "Semantic token at line " + line + " falls outside the original .jsh document (" + lineCount
						+ " lines) - it's still in synthetic wrapped-source coordinates");
			}
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	/**
	 * Regression test: a single import with no trailing newline places the generated
	 * "public class X {" scaffolding immediately after it, starting exactly at the position
	 * {@code toOriginalPosition} inclusively maps to end-of-document (for cursor-position
	 * translation). A token (e.g. the "public" modifier) starting exactly there used to still be
	 * kept even though its length extends past the real document's last character.
	 */
	@Test
	public void testTokenAtEofBoundaryIsNotKeptIfItExtendsPastTheDocument() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/EofBoundary.jsh";

		String content = "import java.util.List;";
		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			SemanticTokens tokens = SemanticTokensHandler.full(new NullProgressMonitor(),
					new SemanticTokensParams(new TextDocumentIdentifier(uri)), mock(DocumentMonitor.class));

			List<Integer> data = tokens.getData();
			int line = 0;
			int character = 0;
			for (int i = 0; i + 4 < data.size(); i += 5) {
				int deltaLine = data.get(i);
				int deltaChar = data.get(i + 1);
				int length = data.get(i + 2);
				character = deltaLine == 0 ? character + deltaChar : deltaChar;
				line += deltaLine;

				assertTrue(line == 0, "This single-line document should never produce a token past line 0");
				assertTrue(character + length <= content.length(),
						"Token at character " + character + " with length " + length + " extends past the document's "
								+ content.length() + " characters - it's a generated scaffolding token, not real content");
			}
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	@Test
	public void testNoSemanticTokensForClosedJshDocument() {
		SemanticTokens tokens = SemanticTokensHandler.full(new NullProgressMonitor(),
				new SemanticTokensParams(new TextDocumentIdentifier("file:///never/opened.jsh")), mock(DocumentMonitor.class));
		assertTrue(tokens.getData().isEmpty(), "Expected no semantic tokens for a .jsh URI with no open document");
	}

}
