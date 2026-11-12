/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.jsh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.jsh.JshDocumentManager.JshDocument;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.junit.jupiter.api.Test;

public class JshDocumentManagerTest extends AbstractInvisibleProjectBasedTest {

	private static List<TextDocumentContentChangeEvent> fullReplace(String text) {
		TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
		event.setText(text);
		return List.of(event);
	}

	@Test
	public void testOpenChangeCloseLifecycle() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/Scratch.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		assertNull(manager.get(uri), "Nothing should be open yet");

		manager.didOpen(uri, "int x = 1;");
		JshDocument opened = manager.get(uri);
		assertNotNull(opened, "didOpen should create a document");
		assertTrue(opened.getWorkingCopy().getBuffer().getContents().contains("int x = 1;"));

		manager.didChange(uri, fullReplace("org.apache.commons.lang3.StringUtils.isBla"));
		JshDocument changed = manager.get(uri);
		assertTrue(changed.getWorkingCopy().getBuffer().getContents().contains("org.apache.commons.lang3.StringUtils.isBla"),
				"didChange should update the synthetic buffer");
		assertFalse(changed.getWorkingCopy().getBuffer().getContents().contains("int x = 1;"),
				"didChange should replace, not append to, the previous content");

		// Prove the resulting working copy resolves against the real project's classpath, same as
		// the Phase 0 spike, but now going through the actual manager end-to-end.
		ICompilationUnit cu = changed.getWorkingCopy();
		int originalOffset = "org.apache.commons.lang3.StringUtils.isBla".length();
		int syntheticOffset = changed.getWrapResult().toSyntheticOffset(originalOffset);
		List<String> proposals = new ArrayList<>();
		cu.codeComplete(syntheticOffset, new CompletionRequestor() {
			@Override
			public void accept(CompletionProposal proposal) {
				proposals.add(new String(proposal.getCompletion()));
			}
		});
		assertTrue(proposals.stream().anyMatch(p -> p.contains("isBlank")),
				"Expected completion through the manager to resolve the project's commons-lang3 dependency, but got: " + proposals);

		manager.didClose(uri);
		assertNull(manager.get(uri), "didClose should remove the document");
		// This synthetic unit was never persisted anywhere; once its working copy is discarded it
		// has nothing left to "exist" as.
		assertFalse(cu.exists(), "didClose should discard the backing working copy");
	}

	@Test
	public void testDidChangeWithoutPriorDidOpenEstablishesTheDocument() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/OutOfOrder.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		manager.didChange(uri, fullReplace("int x = 1;"));
		assertNotNull(manager.get(uri), "didChange without a prior didOpen should still establish the document");

		manager.didClose(uri);
	}

	/**
	 * Regression test: a duplicate didOpen for the same URI (or a didChange-without-prior-didOpen
	 * fallback racing with a real didOpen) used to leak the previous working copy forever, since
	 * {@code documents.put(uri, document)} discarded no reference to what it overwrote.
	 */
	@Test
	public void testDuplicateDidOpenDiscardsThePreviousWorkingCopy() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/DuplicateOpen.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		manager.didOpen(uri, "int x = 1;");
		ICompilationUnit firstCu = manager.get(uri).getWorkingCopy();
		assertTrue(firstCu.exists(), "First working copy should exist right after its own didOpen");

		manager.didOpen(uri, "int y = 2;");
		try {
			assertFalse(firstCu.exists(), "Duplicate didOpen should discard the previous working copy, not leak it");
			ICompilationUnit secondCu = manager.get(uri).getWorkingCopy();
			assertTrue(secondCu.exists(), "The new working copy from the second didOpen should be live");
			assertTrue(secondCu.getBuffer().getContents().contains("int y = 2;"));
		} finally {
			manager.didClose(uri);
		}
	}

	@Test
	public void testCloseWithoutOpenIsANoOp() {
		JshDocumentManager manager = new JshDocumentManager();
		manager.didClose("file:///never/opened.jsh");
		assertNull(manager.get("file:///never/opened.jsh"));
	}

	@Test
	public void testIncrementalRangeBasedChangeIsAppliedLikeARealJavaFile() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/Incremental.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		manager.didOpen(uri, "int x = 1;");

		// Replace the "1" in "int x = 1;" with "42", the same kind of range-based incremental edit
		// a real editor sends for normal .java files - not a whole-document replacement.
		TextDocumentContentChangeEvent incrementalEdit = new TextDocumentContentChangeEvent();
		incrementalEdit.setRange(new Range(new Position(0, 8), new Position(0, 9)));
		incrementalEdit.setText("42");
		manager.didChange(uri, List.of(incrementalEdit));

		JshDocument document = manager.get(uri);
		assertTrue(document.getWorkingCopy().getBuffer().getContents().contains("int x = 42;"),
				"Incremental range edit should have been applied against the raw .jsh content");

		manager.didClose(uri);
	}

	/**
	 * Regression test for the file-name/class-name mismatch bug: each open .jsh document's
	 * synthetic unit's file name includes a per-document counter (to avoid collisions between
	 * concurrently open .jsh files sharing a package), which must also be reflected in the
	 * generated class name - otherwise every .jsh file would carry a permanent, wrapper-induced
	 * "type must match file name" diagnostic, independent of anything in the actual .jsh content.
	 */
	@Test
	public void testSyntheticTypeNameMatchesFileNameWithMultipleOpenDocuments() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri1 = project.getLocationURI().toString() + "/First.jsh";
		String uri2 = project.getLocationURI().toString() + "/Second.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		// Two concurrently open .jsh documents - the scenario that actually exercises per-document
		// uniqueness, unlike the other tests in this class which only ever open one at a time.
		manager.didOpen(uri1, "int x = 1;");
		manager.didOpen(uri2, "int y = 2;");
		try {
			assertNoPublicClassMustMatchFileNameProblem(manager.get(uri1));
			assertNoPublicClassMustMatchFileNameProblem(manager.get(uri2));
		} finally {
			manager.didClose(uri1);
			manager.didClose(uri2);
		}
	}

	/**
	 * Every other test in this suite only ever opens single-line content, so they can't catch an
	 * off-by-one in the line-number translation - the generated header is exactly one line, so
	 * every original line is shifted down by one in the synthetic source. This test exercises a
	 * position on the third original line specifically to catch that class of bug.
	 */
	@Test
	public void testPositionTranslationAcrossMultipleLines() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/MultiLine.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		String content = "int a = 1;\nint b = 2;\nint c = a + b;\n";
		manager.didOpen(uri, content);
		try {
			JshDocument document = manager.get(uri);

			// The "a" in "a + b", on the third original line (index 2).
			Position original = new Position(2, 8);
			int syntheticOffset = document.toSyntheticOffset(original);

			int[] syntheticLine = JsonRpcHelpers.toLine(document.getWorkingCopy().getBuffer(), syntheticOffset);
			assertEquals(4, syntheticLine[0], "Original line 2 should land on synthetic line 4, shifted down by the generated header's line count (imports+class line, run-method line)");
			assertEquals(8, syntheticLine[1], "Column should be unaffected by the line shift");

			assertEquals(original, document.toOriginalPosition(syntheticOffset),
					"Round trip through the synthetic offset should recover the exact original position");
			assertEquals(original, document.toOriginalPosition(new Position(syntheticLine[0], syntheticLine[1])),
					"Round trip through a synthetic line/column position, as completion/hover edit ranges use, should agree");
		} finally {
			manager.didClose(uri);
		}
	}

	@Test
	public void testToOriginalRangeAcrossMultipleLines() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/MultiLineRange.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		String content = "int a = 1;\nint b = 2;\n";
		manager.didOpen(uri, content);
		try {
			JshDocument document = manager.get(uri);
			// A range spanning from the "b" declaration's start (original line 1) back to the "a"
			// declaration's start (original line 0), i.e. start and end land on different lines.
			Range syntheticRange = new Range(new Position(2, 4), new Position(3, 4));
			Range originalRange = document.toOriginalRange(syntheticRange);
			assertNotNull(originalRange);
			assertEquals(new Position(0, 4), originalRange.getStart());
			assertEquals(new Position(1, 4), originalRange.getEnd());
		} finally {
			manager.didClose(uri);
		}
	}

	@Test
	public void testToOriginalPositionReturnsNullInsideGeneratedHeader() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/HeaderPosition.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		manager.didOpen(uri, "int x = 1;");
		try {
			JshDocument document = manager.get(uri);
			// Line 0, column 0 of the synthetic source is the start of the generated imports, never
			// part of the original .jsh content.
			assertNull(document.toOriginalPosition(new Position(0, 0)));
		} finally {
			manager.didClose(uri);
		}
	}

	/**
	 * Regression test: a stale position - e.g. a request computed against document state that a
	 * concurrent didChange has since replaced with shorter content - used to make
	 * {@code toSyntheticOffset} throw {@code IllegalArgumentException} (from the underlying
	 * {@code JshWrapResult.toSyntheticOffset}'s bounds check) instead of returning {@code -1} for
	 * callers' existing empty-result paths to handle gracefully.
	 */
	@Test
	public void testToSyntheticOffsetReturnsNegativeOneForOutOfRangePositionRatherThanThrowing() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/StalePosition.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		manager.didOpen(uri, "int x = 1;");
		try {
			JshDocument document = manager.get(uri);
			// Line 5 doesn't exist in a one-line document - JsonRpcHelpers.toOffset returns -1 for it,
			// which toSyntheticOffset must pass through directly.
			assertEquals(-1, document.toSyntheticOffset(new Position(5, 0)));
			// A column far past the end of the one real line resolves to a raw offset past the
			// document's length (JsonRpcHelpers.toOffset doesn't validate the column, only the line) -
			// toSyntheticOffset must catch the resulting IllegalArgumentException from the underlying
			// JshWrapResult's bounds check rather than letting it propagate.
			assertEquals(-1, document.toSyntheticOffset(new Position(0, 1000)));
		} finally {
			manager.didClose(uri);
		}
	}

	private static void assertNoPublicClassMustMatchFileNameProblem(JshDocument document) {
		ICompilationUnit cu = document.getWorkingCopy();
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		assertNotNull(astRoot, "Expected the synthetic unit to produce an AST");
		boolean hasMismatch = Arrays.stream(astRoot.getProblems()).anyMatch(p -> p.getID() == IProblem.PublicClassMustMatchFileName);
		assertFalse(hasMismatch, "Synthetic unit's class name should match its own file name");
	}

}
