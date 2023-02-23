/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.manipulation.search.BreakContinueTargetFinder;
import org.eclipse.jdt.internal.core.manipulation.search.ExceptionOccurrencesFinder;
import org.eclipse.jdt.internal.core.manipulation.search.IOccurrencesFinder;
import org.eclipse.jdt.internal.core.manipulation.search.ImplementOccurrencesFinder;
import org.eclipse.jdt.internal.core.manipulation.search.MethodExitsFinder;
import org.eclipse.jdt.internal.core.manipulation.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.core.manipulation.search.OccurrencesFinder;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

/**
 * Handler for {@code textDocument/documentHighlight} requests.
 */
public class DocumentHighlightHandler {

	/**
	 * Handles a {@code textDocument/documentHighlight} request.
	 *
	 * @param params the position at which to find highlights
	 * @param monitor the progress monitor
	 * @return the document highlights for the given position
	 */
	public static List<DocumentHighlight> documentHighlight(TextDocumentPositionParams params, IProgressMonitor monitor) {
		ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri());
		if (typeRoot == null || monitor.isCanceled()) {
			return Collections.emptyList();
		}
		CompilationUnit ast = CoreASTProvider.getInstance().getAST(typeRoot, CoreASTProvider.WAIT_YES, monitor);
		if (ast == null || monitor.isCanceled()) {
			return Collections.emptyList();
		}

		int offset = JsonRpcHelpers.toOffset(typeRoot,
			params.getPosition().getLine(), params.getPosition().getCharacter());
		ASTNode node = NodeFinder.perform(ast, offset, 0);
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}

		return findHighlights(ast, node, monitor);
	}

	/**
	 * Finds {@link DocumentHighlight}s in a {@link CompilationUnit}.
	 * The highlights are searched using the following {@link IOccurrencesFinder}s:
	 * <ol>
	 *   <li>{@link ExceptionOccurrencesFinder}</li>
	 *   <li>{@link MethodExitsFinder}</li>
	 *   <li>{@link BreakContinueTargetFinder}</li>
	 *   <li>{@link ImplementOccurrencesFinder}</li>
	 *   <li>{@link OccurrencesFinder}</li>
	 * </ol>
	 *
	 * @param ast the {@link CompilationUnit}
	 * @param node the selected {@link ASTNode} to find highlights for
	 * @param monitor the progress monitor
	 * @return the highlights, or an empty list if none were found
	 */
	private static List<DocumentHighlight> findHighlights(CompilationUnit ast, ASTNode node, IProgressMonitor monitor) {
		IOccurrencesFinder finder;

		finder = new ExceptionOccurrencesFinder();
		if (finder.initialize(ast, node) == null) {
			return convertToHighlights(ast, finder.getOccurrences());
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}

		finder = new MethodExitsFinder();
		if (finder.initialize(ast, node) == null) {
			return convertToHighlights(ast, finder.getOccurrences());
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}

		finder = new BreakContinueTargetFinder();
		if (finder.initialize(ast, node) == null) {
			return convertToHighlights(ast, finder.getOccurrences());
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}

		finder = new ImplementOccurrencesFinder();
		if (finder.initialize(ast, node) == null) {
			return convertToHighlights(ast, finder.getOccurrences());
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}

		finder = new OccurrencesFinder();
		if (finder.initialize(ast, node) == null) {
			return convertToHighlights(ast, finder.getOccurrences());
		}

		return Collections.emptyList();
	}

	private static List<DocumentHighlight> convertToHighlights(CompilationUnit ast, OccurrenceLocation[] locations) {
		if (locations == null || locations.length == 0) {
			return Collections.emptyList();
		}
		List<DocumentHighlight> highlights = new ArrayList<>(locations.length);
		for (OccurrenceLocation loc : locations) {
			highlights.add(convertToHighlight(ast, loc));
		}
		return highlights;
	}

	private static DocumentHighlight convertToHighlight(CompilationUnit ast, OccurrenceLocation occurrence) {
		DocumentHighlight highlight = new DocumentHighlight();
		if ((occurrence.getFlags() & IOccurrencesFinder.F_WRITE_OCCURRENCE) != 0) {
			highlight.setKind(DocumentHighlightKind.Write);
		} else {
			// highlight kind for symbols should be either Read or Write (not Text), see
			// https://microsoft.github.io/language-server-protocol/specifications/specification-3-17/#textDocument_documentHighlight
			highlight.setKind(DocumentHighlightKind.Read);
		}

		int[] startPos = JsonRpcHelpers.toLine(ast.getTypeRoot(), occurrence.getOffset());
		int[] endPos = JsonRpcHelpers.toLine(ast.getTypeRoot(), occurrence.getOffset() + occurrence.getLength());
		highlight.setRange(new Range(
			new Position(startPos[0], startPos[1]),
			new Position(endPos[0], endPos[1])
		));
		return highlight;
	}

}
