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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.handlers.OrganizeImportsHandler;
import org.eclipse.jdt.ls.core.internal.handlers.SaveActionHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentSaveReason;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.jupiter.api.Test;

/**
 * Phase 9 guardrails: a few generic LSP handlers resolve a compilation unit for any URI with no
 * .jsh awareness, unlike completion/diagnostics/hover which were deliberately wired with
 * offset-translation layers. These tests cover the handlers found to be dangerous specifically
 * because they'd silently corrupt state or apply wrong-coordinate edits to a real file, rather
 * than just doing nothing useful - see the jsh-content-assist-plan.md "Known gaps" / guardrails
 * notes for the full audit (most other generically-reachable handlers were left unguarded as
 * lower-priority, since .jsh client wiring (Phase 10) hasn't happened yet).
 */
public class JshGuardrailsTest extends AbstractInvisibleProjectBasedTest {

	/**
	 * Regression test for a real corruption bug: {@code BaseDocumentLifeCycleHandler.handleSaved}
	 * discards and re-creates the working copy's buffer, which would churn
	 * {@code JshDocumentManager}'s long-lived working copy out from under it. didSave must be a
	 * no-op for .jsh files - the working copy is already kept current via didChange.
	 */
	@Test
	public void testDidSaveDoesNotCorruptTheWorkingCopy() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/Save.jsh";

		JshDocumentManager.getInstance().didOpen(uri, "int x = 1;");
		try {
			JshDocumentManager.JshDocument before = JshDocumentManager.getInstance().get(uri);

			DocumentLifeCycleHandler handler = new DocumentLifeCycleHandler(new JavaClientConnection(client), preferenceManager, projectsManager, false);
			DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(new TextDocumentIdentifier(uri));
			handler.didSave(params);

			JshDocumentManager.JshDocument after = JshDocumentManager.getInstance().get(uri);
			assertSame(before, after, "didSave should not replace or discard the .jsh document");
			assertTrue(after.getWorkingCopy().getBuffer().getContents().contains("int x = 1;"),
					"didSave should not have disturbed the synthetic working copy's content");
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	/**
	 * {@code willSaveWaitUntil}'s edits (organize imports / clean-up on save) are computed against
	 * the synthetic wrapped source's offsets, not the original .jsh buffer's - returning them would
	 * have the client silently apply wrong-coordinate edits to the real file on save.
	 */
	@Test
	public void testWillSaveWaitUntilReturnsNoEditsForJshFiles() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/WillSave.jsh";

		JshDocumentManager.getInstance().didOpen(uri, "int x = 1;");
		try {
			SaveActionHandler handler = new SaveActionHandler(preferenceManager);
			WillSaveTextDocumentParams params = new WillSaveTextDocumentParams(new TextDocumentIdentifier(uri), TextDocumentSaveReason.Manual);
			List<TextEdit> edits = handler.willSaveWaitUntil(params, new NullProgressMonitor());
			assertTrue(edits.isEmpty(), "willSaveWaitUntil should return no edits for .jsh files");
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	/**
	 * Same wrong-coordinate-edit concern as willSaveWaitUntil, but reachable any time via the
	 * explicit "Organize Imports" command/code action, not just on save.
	 */
	@Test
	public void testOrganizeImportsReturnsNullForJshFiles() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/OrganizeImports.jsh";

		JshDocumentManager.getInstance().didOpen(uri, "int x = 1;");
		try {
			CodeActionParams params = new CodeActionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri));

			WorkspaceEdit edit = OrganizeImportsHandler.organizeImports(new JavaClientConnection(client), params, new NullProgressMonitor());
			assertNull(edit, "organizeImports should do nothing for .jsh files");
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

}
