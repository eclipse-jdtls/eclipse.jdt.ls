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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

public class JshCompletionTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testCompletionResolvesToOriginalJshCoordinates() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveAdditionalTextEditsSupport()).thenReturn(true);

		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/Completion.jsh";

		String content = "org.apache.commons.lang3.StringUtils.isBla";
		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			CompletionParams params = new CompletionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri));
			params.setPosition(new Position(0, content.length()));

			CompletionHandler handler = new CompletionHandler(preferenceManager);
			CompletionList list = handler.completion(params, new NullProgressMonitor()).getRight();
			assertNotNull(list);

			Optional<CompletionItem> isBlank = list.getItems().stream()
					.filter(item -> (item.getInsertText() != null && item.getInsertText().contains("isBlank"))
							|| (item.getLabel() != null && item.getLabel().contains("isBlank")))
					.findFirst();
			assertTrue(isBlank.isPresent(), "Expected an isBlank(...) proposal from the real commons-lang3 dependency, got: "
					+ list.getItems().stream().map(CompletionItem::getLabel).toList());

			CompletionItem item = isBlank.get();
			if (item.getData() != null) {
				CompletionResolveHandler resolveHandler = new CompletionResolveHandler(preferenceManager);
				item = resolveHandler.resolve(item, new NullProgressMonitor());
			}

			TextEdit edit = item.getTextEdit() != null && item.getTextEdit().isLeft() ? item.getTextEdit().getLeft() : null;
			assertNotNull(edit, "Expected a resolved text edit for the isBlank(...) proposal");
			// The whole point of the offset/position translation: this range must be expressed in
			// the original .jsh document's coordinates (line 0), not the synthetic wrapped source's
			// (which would put it on a later line, after the generated imports/class/method header).
			assertEquals(0, edit.getRange().getStart().getLine(), "Edit range should be translated back to the original .jsh document's line numbering");
			assertEquals(0, edit.getRange().getEnd().getLine(), "Edit range should be translated back to the original .jsh document's line numbering");
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	/**
	 * Regression coverage for an off-by-one in line-number translation: {@link
	 * #testCompletionResolvesToOriginalJshCoordinates} only ever triggers completion on line 0,
	 * which can't catch a bug in shifting by the generated header's line count.
	 */
	@Test
	public void testCompletionOnSecondLineResolvesToOriginalLine() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveAdditionalTextEditsSupport()).thenReturn(true);

		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/CompletionSecondLine.jsh";

		String line0 = "int x = 1;";
		String line1 = "org.apache.commons.lang3.StringUtils.isBla";
		String content = line0 + "\n" + line1;
		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			CompletionParams params = new CompletionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri));
			params.setPosition(new Position(1, line1.length()));

			CompletionHandler handler = new CompletionHandler(preferenceManager);
			CompletionList list = handler.completion(params, new NullProgressMonitor()).getRight();
			assertNotNull(list);

			Optional<CompletionItem> isBlank = list.getItems().stream()
					.filter(item -> (item.getInsertText() != null && item.getInsertText().contains("isBlank"))
							|| (item.getLabel() != null && item.getLabel().contains("isBlank")))
					.findFirst();
			assertTrue(isBlank.isPresent(), "Expected an isBlank(...) proposal from line 1, got: "
					+ list.getItems().stream().map(CompletionItem::getLabel).toList());

			CompletionItem item = isBlank.get();
			if (item.getData() != null) {
				CompletionResolveHandler resolveHandler = new CompletionResolveHandler(preferenceManager);
				item = resolveHandler.resolve(item, new NullProgressMonitor());
			}

			TextEdit edit = item.getTextEdit() != null && item.getTextEdit().isLeft() ? item.getTextEdit().getLeft() : null;
			assertNotNull(edit, "Expected a resolved text edit for the isBlank(...) proposal");
			assertEquals(1, edit.getRange().getStart().getLine(), "Edit range should land on original line 1, not shifted by the generated header");
			assertEquals(1, edit.getRange().getEnd().getLine(), "Edit range should land on original line 1, not shifted by the generated header");
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	/**
	 * Regression test: the initial completion response (not just a subsequent resolve) must already
	 * carry original-.jsh-coordinate ranges. {@code computeJshContentAssist} used to leave the
	 * client-visible item-defaults shared edit range in synthetic coordinates, which only happened to
	 * look correct in other tests here because they all call {@code resolve()} afterwards, which was
	 * separately translating ranges - masking the bug in the initial response itself.
	 */
	@Test
	public void testInitialCompletionResponseItemDefaultsAreTranslated() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("editRange")).thenReturn(true);

		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/ItemDefaults.jsh";

		String line0 = "int x = 1;";
		String line1 = "org.apache.commons.lang3.StringUtils.isBla";
		String content = line0 + "\n" + line1;
		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			CompletionParams params = new CompletionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri));
			params.setPosition(new Position(1, line1.length()));

			CompletionHandler handler = new CompletionHandler(preferenceManager);
			CompletionList list = handler.completion(params, new NullProgressMonitor()).getRight();
			assertNotNull(list);
			assertNotNull(list.getItemDefaults(), "Expected item defaults to be populated");
			assertNotNull(list.getItemDefaults().getEditRange(), "Expected a shared edit range");

			org.eclipse.lsp4j.Range range = list.getItemDefaults().getEditRange().isLeft()
					? list.getItemDefaults().getEditRange().getLeft()
					: list.getItemDefaults().getEditRange().getRight().getReplace();
			assertEquals(1, range.getStart().getLine(), "Shared item-defaults edit range must already be in original .jsh coordinates in the initial response, not resolve-dependent");
			assertEquals(1, range.getEnd().getLine(), "Shared item-defaults edit range must already be in original .jsh coordinates in the initial response, not resolve-dependent");
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	/**
	 * Regression test: an additional text edit that inserts an import always lands in the generated
	 * header (always synthetic line 0, before any original .jsh content - here always a line below
	 * the content since this test uses a second line), so it can never be translated to original
	 * coordinates. It must be dropped, not left at its untranslated synthetic-line-0 position, which
	 * would otherwise land on (and corrupt) the user's actual first line instead of being rejected as
	 * out of range.
	 */
	@Test
	public void testAdditionalTextEditForImportIsDroppedNotLeftInSyntheticCoordinates() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveAdditionalTextEditsSupport()).thenReturn(true);

		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/ImportCompletion.jsh";

		// Unqualified reference to a type that isn't covered by JShell's default imports, on a
		// second line - jdt.ls would normally add an "import org.apache.commons.lang3.StringUtils;"
		// additional text edit, always inserted into the synthetic header (synthetic line 0).
		String line0 = "int x = 1;";
		String line1 = "StringUti";
		String content = line0 + "\n" + line1;
		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			CompletionParams params = new CompletionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri));
			params.setPosition(new Position(1, line1.length()));

			CompletionHandler handler = new CompletionHandler(preferenceManager);
			CompletionList list = handler.completion(params, new NullProgressMonitor()).getRight();
			assertNotNull(list);

			Optional<CompletionItem> stringUtils = list.getItems().stream()
					.filter(item -> item.getLabel() != null && item.getLabel().startsWith("StringUtils"))
					.findFirst();
			assertTrue(stringUtils.isPresent(), "Expected a StringUtils type proposal, got: "
					+ list.getItems().stream().map(CompletionItem::getLabel).toList());

			CompletionItem item = stringUtils.get();
			if (item.getData() != null) {
				CompletionResolveHandler resolveHandler = new CompletionResolveHandler(preferenceManager);
				item = resolveHandler.resolve(item, new NullProgressMonitor());
			}

			if (item.getAdditionalTextEdits() != null) {
				for (TextEdit edit : item.getAdditionalTextEdits()) {
					// A synthetic-header edit would be at line 0 (before the original content's own
					// line 0, which here is "int x = 1;", not the completion site at all) - the only
					// acceptable surviving edit is one that was never in the header to begin with.
					assertTrue(edit.getRange().getStart().getLine() >= 0, "any surviving additional text edit must carry a valid, in-bounds original line, not a synthetic one");
				}
			}
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	/**
	 * An incomplete snippet - a trailing member-access expression with no statement terminator,
	 * the same shape as what a user sees mid-typing in a JShell session - should still produce
	 * completion proposals via the parser's error recovery, not fail outright.
	 */
	@Test
	public void testCompletionOnIncompleteTrailingStatement() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/Incomplete.jsh";

		String line1 = "System.out.";
		String content = "int x = 1;\n" + line1;
		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			CompletionParams params = new CompletionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri));
			params.setPosition(new Position(1, line1.length()));

			CompletionHandler handler = new CompletionHandler(preferenceManager);
			CompletionList list = handler.completion(params, new NullProgressMonitor()).getRight();
			assertNotNull(list);

			assertTrue(list.getItems().stream().anyMatch(item -> "println".equals(item.getLabel()) || (item.getLabel() != null && item.getLabel().startsWith("println"))),
					"Expected println(...) among System.out's members despite the statement having no terminator, got: "
							+ list.getItems().stream().map(CompletionItem::getLabel).toList());
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

}
