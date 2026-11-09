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

	private static void assertNoPublicClassMustMatchFileNameProblem(JshDocument document) {
		ICompilationUnit cu = document.getWorkingCopy();
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		assertNotNull(astRoot, "Expected the synthetic unit to produce an AST");
		boolean hasMismatch = Arrays.stream(astRoot.getProblems()).anyMatch(p -> p.getID() == IProblem.PublicClassMustMatchFileName);
		assertFalse(hasMismatch, "Synthetic unit's class name should match its own file name");
	}

}
