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
