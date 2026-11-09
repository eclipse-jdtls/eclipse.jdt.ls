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

}
