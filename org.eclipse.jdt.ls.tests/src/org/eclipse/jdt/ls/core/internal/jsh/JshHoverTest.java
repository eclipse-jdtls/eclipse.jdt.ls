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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.HoverHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

public class JshHoverTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testHoverResolvesAgainstRealClasspath() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/Hover.jsh";

		String content = "org.apache.commons.lang3.StringUtils.isBlank(\"\");";
		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			// Hover over "isBlank" within the real commons-lang3 method reference.
			int column = content.indexOf("isBlank") + 2;

			TextDocumentPositionParams params = new TextDocumentPositionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri));
			params.setPosition(new Position(0, column));

			HoverHandler handler = new HoverHandler(preferenceManager);
			Hover hover = handler.hover(params, new NullProgressMonitor());
			assertNotNull(hover);

			boolean mentionsIsBlank = hover.getContents().getLeft().stream().anyMatch(either -> {
				String text = either.isLeft() ? either.getLeft() : asPlainText(either.getRight());
				return text != null && text.contains("isBlank");
			});
			assertTrue(mentionsIsBlank, "Expected hover signature to mention isBlank, got: " + hover.getContents());
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	/**
	 * {@link #testHoverResolvesAgainstRealClasspath} only ever hovers on line 0, which can't catch
	 * an off-by-one in shifting by the generated header's line count.
	 */
	@Test
	public void testHoverOnSecondLineResolvesToOriginalLine() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/HoverSecondLine.jsh";

		String line0 = "int x = 1;";
		String line1 = "org.apache.commons.lang3.StringUtils.isBlank(\"\");";
		String content = line0 + "\n" + line1;
		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			int column = line1.indexOf("isBlank") + 2;

			TextDocumentPositionParams params = new TextDocumentPositionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri));
			params.setPosition(new Position(1, column));

			HoverHandler handler = new HoverHandler(preferenceManager);
			Hover hover = handler.hover(params, new NullProgressMonitor());
			assertNotNull(hover);

			boolean mentionsIsBlank = hover.getContents().getLeft().stream().anyMatch(either -> {
				String text = either.isLeft() ? either.getLeft() : asPlainText(either.getRight());
				return text != null && text.contains("isBlank");
			});
			assertTrue(mentionsIsBlank, "Expected hover on line 1 to mention isBlank, got: " + hover.getContents());
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	@Test
	public void testHoverOutsideContentReturnsEmpty() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = project.getLocationURI().toString() + "/HoverEmpty.jsh";

		String content = "int x = 1;";
		JshDocumentManager.getInstance().didOpen(uri, content);
		try {
			TextDocumentPositionParams params = new TextDocumentPositionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri));
			// Whitespace position: no element under the cursor.
			params.setPosition(new Position(0, content.length()));

			HoverHandler handler = new HoverHandler(preferenceManager);
			Hover hover = handler.hover(params, new NullProgressMonitor());
			assertNotNull(hover);
			assertFalse(hover.getContents().getLeft().stream().anyMatch(either -> {
				String text = either.isLeft() ? either.getLeft() : asPlainText(either.getRight());
				return text != null && !text.isBlank();
			}), "Expected no hover content when there is no element at the position, got: " + hover.getContents());
		} finally {
			JshDocumentManager.getInstance().didClose(uri);
		}
	}

	private static String asPlainText(MarkedString markedString) {
		return markedString == null ? null : markedString.getValue();
	}

}
