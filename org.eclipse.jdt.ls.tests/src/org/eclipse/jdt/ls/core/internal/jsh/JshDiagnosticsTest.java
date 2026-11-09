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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.junit.jupiter.api.Test;

public class JshDiagnosticsTest extends AbstractInvisibleProjectBasedTest {

	@SuppressWarnings("unchecked")
	private <T> List<T> getClientRequests(String name) {
		List<?> requests = clientRequests.get(name);
		return requests != null ? (List<T>) requests : Collections.emptyList();
	}

	@Test
	public void testDiagnosticPublishedWithOriginalCoordinates() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/Diagnostics.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		manager.setClientConnection(new JavaClientConnection(client));
		try {
			// "undefinedVariable" doesn't exist anywhere - a real, unambiguous compile error on
			// line 0 of the .jsh content.
			manager.didOpen(uri, "int x = undefinedVariable;");

			List<PublishDiagnosticsParams> publishes = getClientRequests("publishDiagnostics");
			assertFalse(publishes.isEmpty(), "Expected at least one publishDiagnostics call after didOpen");
			PublishDiagnosticsParams last = publishes.get(publishes.size() - 1);
			assertEquals(uri, last.getUri(), "Diagnostics should be published against the original .jsh URI, not a synthetic one");

			List<Diagnostic> diagnostics = last.getDiagnostics();
			assertFalse(diagnostics.isEmpty(), "Expected a real diagnostic for the undefined variable");
			Diagnostic diagnostic = diagnostics.get(0);
			// The whole point: this must be in original .jsh coordinates (line 0), not the
			// synthetic wrapped source's line numbering.
			assertEquals(0, diagnostic.getRange().getStart().getLine());
			assertEquals(0, diagnostic.getRange().getEnd().getLine());
		} finally {
			manager.didClose(uri);
		}
	}

	/**
	 * {@link #testDiagnosticPublishedWithOriginalCoordinates} only ever puts the error on line 0,
	 * which can't catch an off-by-one in shifting by the generated header's line count.
	 */
	@Test
	public void testDiagnosticOnSecondLineResolvesToOriginalLine() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/DiagnosticsSecondLine.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		manager.setClientConnection(new JavaClientConnection(client));
		try {
			manager.didOpen(uri, "int x = 1;\nint y = undefinedVariable;");

			List<PublishDiagnosticsParams> publishes = getClientRequests("publishDiagnostics");
			assertFalse(publishes.isEmpty(), "Expected at least one publishDiagnostics call after didOpen");
			PublishDiagnosticsParams last = publishes.get(publishes.size() - 1);

			List<Diagnostic> diagnostics = last.getDiagnostics();
			assertFalse(diagnostics.isEmpty(), "Expected a real diagnostic for the undefined variable");
			Diagnostic diagnostic = diagnostics.get(0);
			assertEquals(1, diagnostic.getRange().getStart().getLine(), "Diagnostic should land on original line 1, not shifted by the generated header");
			assertEquals(1, diagnostic.getRange().getEnd().getLine(), "Diagnostic should land on original line 1, not shifted by the generated header");
		} finally {
			manager.didClose(uri);
		}
	}

	/**
	 * Characterization test for a known, real gap found while adding Phase 8 coverage - tracked on
	 * https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3973, not yet understood well enough to
	 * fix: a genuine, unambiguous syntax error (unterminated string literal) present on the very
	 * first {@code didOpen} of a `.jsh` document produces <b>no diagnostic at all</b>. Confirmed this
	 * isn't the offset-translation/filtering logic dropping a found problem - the exact same
	 * {@code CoreASTProvider.getInstance().getAST(cu, WAIT_YES, ...)} call, re-run moments later
	 * against the identical working copy, does find the problem (plus a harmless, separately
	 * surprising one: every `.jsh` file's generated header always triggers "the import
	 * java.util.prefs cannot be resolved", filtered out only because it lands inside the header).
	 * This smells like a JDT-internal timing/caching quirk specific to the very first reconcile of a
	 * freshly created working copy, not a bug in this wrapper's own logic - needs more investigation
	 * before attempting a fix. If this test starts failing because a real diagnostic now comes
	 * through, that's progress - update/remove this test and the linked plan note accordingly.
	 */
	@Test
	public void testDiagnosticForUnterminatedStringIsKnownToBeMissingOnFirstOpen() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/IncompleteStatement.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		manager.setClientConnection(new JavaClientConnection(client));
		try {
			// Missing the closing quote - a real, unambiguous syntax error - but see the javadoc
			// above: this currently produces no diagnostic at all on first open.
			manager.didOpen(uri, "String s = \"abc");

			List<PublishDiagnosticsParams> publishes = getClientRequests("publishDiagnostics");
			assertFalse(publishes.isEmpty(), "Expected at least one publishDiagnostics call after didOpen");
			PublishDiagnosticsParams last = publishes.get(publishes.size() - 1);
			assertTrue(last.getDiagnostics().isEmpty(),
					"Known gap (see javadoc): the unterminated string literal's diagnostic is currently lost on first open");
		} finally {
			manager.didClose(uri);
		}
	}

	@Test
	public void testDiagnosticsClearedOnClose() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/ClearedOnClose.jsh";

		JshDocumentManager manager = new JshDocumentManager();
		manager.setClientConnection(new JavaClientConnection(client));
		manager.didOpen(uri, "int x = undefinedVariable;");
		assertFalse(getClientRequests("publishDiagnostics").isEmpty());

		manager.didClose(uri);

		List<PublishDiagnosticsParams> publishes = getClientRequests("publishDiagnostics");
		PublishDiagnosticsParams last = publishes.get(publishes.size() - 1);
		assertEquals(uri, last.getUri());
		assertTrue(last.getDiagnostics().isEmpty(), "Closing a .jsh document should clear its diagnostics");
	}

}
