/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.handlers.BaseDocumentLifeCycleHandler.DocumentMonitor;
import org.eclipse.jdt.ls.core.internal.jsh.JshDocumentManager;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokensVisitor;
import org.eclipse.jdt.ls.core.internal.semantictokens.TokenModifier;
import org.eclipse.jdt.ls.core.internal.semantictokens.TokenType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;

public class SemanticTokensHandler {

	public static SemanticTokens full(IProgressMonitor monitor, SemanticTokensParams params, DocumentMonitor documentMonitor) {
		String uri = params.getTextDocument().getUri();
		ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
		documentMonitor.checkChanged();
		if (typeRoot == null || monitor.isCanceled()) {
			return new SemanticTokens(Collections.emptyList());
		}

		JobHelpers.waitForJobs(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		documentMonitor.checkChanged();

		CompilationUnit root = JDTUtils.getAst(typeRoot, monitor);
		documentMonitor.checkChanged();
		if (root == null || monitor.isCanceled()) {
			return new SemanticTokens(Collections.emptyList());
		}

		SemanticTokensVisitor collector = new SemanticTokensVisitor(root);
		root.accept(collector);
		SemanticTokens tokens = collector.getSemanticTokens();

		if (JDTUtils.isJshFile(uri)) {
			JshDocumentManager.JshDocument document = JshDocumentManager.getInstance().get(uri);
			return document == null ? new SemanticTokens(Collections.emptyList()) : translateJshTokens(tokens, document);
		}
		return tokens;
	}

	/**
	 * Semantic tokens are computed against the AST of the synthetic wrapped source, whose positions
	 * don't correspond to the original .jsh document's - worse than a constant shift, since
	 * declarations get relocated to different parts of the synthetic source entirely. The LSP
	 * encoding is delta-compressed relative to the previous token's position, so this decodes to
	 * absolute (line, character) pairs, translates each one back to original .jsh coordinates
	 * (dropping any token that lands in generated scaffolding - imports, class/method declaration
	 * lines), re-sorts by position (synthetic traversal order doesn't match original order once
	 * declarations have been relocated), and re-encodes the deltas.
	 */
	private static SemanticTokens translateJshTokens(SemanticTokens tokens, JshDocumentManager.JshDocument document) {
		List<Integer> data = tokens.getData();
		List<int[]> originalTokens = new ArrayList<>(data.size() / 5);
		int line = 0;
		int character = 0;
		for (int i = 0; i + 4 < data.size(); i += 5) {
			int deltaLine = data.get(i);
			int deltaChar = data.get(i + 1);
			int length = data.get(i + 2);
			int tokenType = data.get(i + 3);
			int tokenModifiers = data.get(i + 4);
			character = deltaLine == 0 ? character + deltaChar : deltaChar;
			line += deltaLine;

			Position original = document.toOriginalPosition(new Position(line, character));
			if (original == null) {
				continue;
			}
			// toOriginalPosition inclusively maps the position one-past-the-last-character of the
			// original content (so cursor-position translation at EOF still works) - but a token
			// starting exactly there belongs to generated scaffolding (e.g. "public" of "public class
			// X {" immediately follows a single-line import with no trailing newline), not real
			// content, and its length would extend past the document. Require the whole span, not
			// just the start, to land back in original coordinates.
			Position originalEnd = document.toOriginalPosition(new Position(line, character + length));
			boolean spanFullyInOriginalContent = originalEnd != null && originalEnd.getLine() == original.getLine()
					&& originalEnd.getCharacter() == original.getCharacter() + length;
			if (spanFullyInOriginalContent) {
				originalTokens.add(new int[] { original.getLine(), original.getCharacter(), length, tokenType, tokenModifiers });
			}
		}
		originalTokens.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));

		List<Integer> result = new ArrayList<>(originalTokens.size() * 5);
		int prevLine = 0;
		int prevChar = 0;
		for (int[] token : originalTokens) {
			int deltaLine = token[0] - prevLine;
			result.add(deltaLine);
			result.add(deltaLine == 0 ? token[1] - prevChar : token[1]);
			result.add(token[2]);
			result.add(token[3]);
			result.add(token[4]);
			prevLine = token[0];
			prevChar = token[1];
		}
		return new SemanticTokens(result);
	}

	public static SemanticTokensLegend legend() {
		return new SemanticTokensLegend(
			Arrays.stream(TokenType.values()).map(TokenType::toString).collect(Collectors.toList()),
			Arrays.stream(TokenModifier.values()).map(TokenModifier::toString).collect(Collectors.toList())
		);
	}
}
