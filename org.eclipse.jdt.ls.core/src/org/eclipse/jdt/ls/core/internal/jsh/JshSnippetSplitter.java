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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

/**
 * Splits a raw .jsh buffer into top-level "chunks" - the unit {@link JshSnippetClassifier}
 * classifies and {@link JshSnippetWrapper} places - at the same granularity JShell's own snippet
 * splitter would: each top-level statement/declaration, up to (and including) the semicolon or
 * closing brace that ends it at nesting depth zero.
 * <p>
 * Deliberately reuses JDT's own {@link IScanner} for lexing (strings, text blocks, comments,
 * generics, lambdas) rather than hand-rolling a tokenizer - the only new logic here is tracking
 * brace/paren nesting depth to find top-level split points.
 * <p>
 * Chunks contiguously partition the entire input with no gaps (each chunk's end is the next
 * chunk's start) - trailing whitespace/comments after a chunk's terminator travel with that chunk,
 * and a final, possibly-incomplete trailing chunk (no terminator yet, e.g. what the user is
 * actively typing) always gets emitted so every offset in the original buffer is accounted for.
 */
public final class JshSnippetSplitter {

	public record Chunk(int start, int end) {
	}

	private JshSnippetSplitter() {
	}

	public static List<Chunk> split(String source) {
		List<Chunk> chunks = new ArrayList<>();
		if (source == null || source.isEmpty()) {
			chunks.add(new Chunk(0, 0));
			return chunks;
		}

		IScanner scanner = ToolFactory.createScanner(false, false, false, JavaCore.latestSupportedJavaVersion());
		scanner.setSource(source.toCharArray());

		int chunkStart = 0;
		int braceDepth = 0;
		int parenDepth = 0;
		int bracketDepth = 0;
		// For each currently-open top-level (parenDepth==0 at the time it was opened) brace,
		// whether it's a real block (split-eligible when it closes) or an array/object
		// initializer's braces (e.g. "int[] a = {1, 2, 3};", which must NOT be treated as ending
		// the chunk - the real terminator is the ';' that follows).
		List<Boolean> topLevelBraceIsBlock = new ArrayList<>();
		int lastSignificantToken = ITerminalSymbols.TokenNameEOF;

		while (true) {
			int token;
			try {
				token = scanner.getNextToken();
			} catch (InvalidInputException e) {
				// Malformed input (e.g. an unterminated string) - stop splitting here and let the
				// remainder become one trailing chunk, same treatment as an incomplete chunk below.
				break;
			}
			if (token == ITerminalSymbols.TokenNameEOF) {
				break;
			}
			int tokenEnd = scanner.getCurrentTokenEndPosition() + 1;

			switch (token) {
				case ITerminalSymbols.TokenNameLPAREN:
					parenDepth++;
					break;
				case ITerminalSymbols.TokenNameRPAREN:
					parenDepth = Math.max(0, parenDepth - 1);
					break;
				case ITerminalSymbols.TokenNameLBRACKET:
					bracketDepth++;
					break;
				case ITerminalSymbols.TokenNameRBRACKET:
					bracketDepth = Math.max(0, bracketDepth - 1);
					break;
				case ITerminalSymbols.TokenNameLBRACE:
					if (parenDepth == 0) {
						topLevelBraceIsBlock.add(!isInitializerContext(lastSignificantToken));
					}
					braceDepth++;
					break;
				case ITerminalSymbols.TokenNameRBRACE: {
					braceDepth = Math.max(0, braceDepth - 1);
					boolean closedTopLevelBlock = false;
					if (parenDepth == 0 && !topLevelBraceIsBlock.isEmpty()) {
						closedTopLevelBlock = topLevelBraceIsBlock.remove(topLevelBraceIsBlock.size() - 1);
					}
					if (braceDepth == 0 && parenDepth == 0 && closedTopLevelBlock) {
						chunks.add(new Chunk(chunkStart, tokenEnd));
						chunkStart = tokenEnd;
					}
					break;
				}
				case ITerminalSymbols.TokenNameSEMICOLON:
					if (braceDepth == 0 && parenDepth == 0 && bracketDepth == 0) {
						chunks.add(new Chunk(chunkStart, tokenEnd));
						chunkStart = tokenEnd;
					}
					break;
				default:
					break;
			}
			lastSignificantToken = token;
		}

		if (chunkStart < source.length()) {
			chunks.add(new Chunk(chunkStart, source.length()));
		}
		if (chunks.isEmpty()) {
			chunks.add(new Chunk(0, source.length()));
		}
		return chunks;
	}

	/**
	 * A '{' immediately following one of these is an array/object initializer (e.g. the '{' in
	 * "int[] a = {1, 2, 3}", "foo(new int[]{1, 2}, {3, 4})"), not a block - closing it must not end
	 * the chunk; the containing statement's own terminating ';' does that instead.
	 */
	private static boolean isInitializerContext(int precedingToken) {
		return switch (precedingToken) {
			case ITerminalSymbols.TokenNameEQUAL, ITerminalSymbols.TokenNameCOMMA, ITerminalSymbols.TokenNameLPAREN, ITerminalSymbols.TokenNameLBRACE -> true;
			default -> false;
		};
	}

}
