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
package org.eclipse.jdt.ls.core.internal.contentassist.resourcebundle;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.lsp4j.Range;

/**
 * Processes document text for resource bundle completion.
 * Handles prefix extraction, quote position finding, and range calculation.
 */
public class ResourceBundleTextProcessor {

	/**
	 * Result of finding quote positions in a string literal.
	 */
	public static record QuotePositions(int openingQuote, int closingQuote) {
		public boolean isValid() {
			return openingQuote >= 0 && closingQuote > openingQuote;
		}
	}

	/**
	 * Result of finding parenthesis positions in a method invocation.
	 */
	public static record ParenthesisPositions(int openParenPos, int closeParenPos) {
	}

	/**
	 * Gets the prefix string at the current offset.
	 * This extracts the partial key that the user has typed so far.
	 * Handles both cases: inside quotes (bundle.getString("key|")) and outside quotes (bundle.getString(key|)).
	 */
	public String getPrefix(IDocument document, int offset, QuotePositions quotes) {
		try {
			if (offset < 0 || offset > document.getLength()) {
				return "";
			}
			boolean insideQuotes = quotes.openingQuote >= 0;

			int start = offset;
			// Find the start of the current word (backwards from offset)
			// Stop at the opening quote, opening parenthesis, comma, or whitespace
			while (start > 0) {
				char c = document.getChar(start - 1);
				if (c == '"' || c == '(' || c == ',' || Character.isWhitespace(c)) {
					break;
				}
				if (!isKeyChar(c)) {
					break;
				}
				start--;
			}

			// Find the end of the current word (forwards from offset)
			// When inside quotes, only look up to the cursor position (don't include text after cursor)
			// When outside quotes, stop at closing parenthesis, comma, or non-key character
			int end = offset;
			if (insideQuotes) {
				// When inside quotes, only extract prefix up to the cursor position
				// Don't include text that comes after the cursor
				end = offset;
			} else {
				// When outside quotes, stop at closing parenthesis, comma, or non-key character
				while (end < document.getLength()) {
					char c = document.getChar(end);
					if (c == ')' || c == ',' || Character.isWhitespace(c)) {
						break;
					}
					if (!isKeyChar(c)) {
						break;
					}
					end++;
				}
			}

			if (start < end) {
				return document.get(start, end - start);
			}
			return "";
		} catch (BadLocationException e) {
			return "";
		}
	}

	/**
	 * Finds the positions of opening and closing parentheses in a method invocation.
	 *
	 * @param source the source code string
	 * @param nameEnd the end position of the method name
	 * @param invocationEnd the end position of the method invocation
	 * @return ParenthesisPositions with the parenthesis positions, or null if not found
	 */
	public static ParenthesisPositions findParenthesisPositions(String source, int nameEnd, int invocationEnd) {
		// Find opening parenthesis after method name
		int openParenPos = -1;
		for (int i = nameEnd; i < invocationEnd && i < source.length(); i++) {
			if (source.charAt(i) == '(') {
				openParenPos = i;
				break;
			}
		}
		if (openParenPos < 0) {
			return null;
		}

		// Find closing parenthesis
		int closeParenPos = -1;
		int depth = 1;
		for (int i = openParenPos + 1; i < invocationEnd && i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					closeParenPos = i;
					break;
				}
			}
		}
		if (closeParenPos < 0) {
			return null;
		}

		return new ParenthesisPositions(openParenPos, closeParenPos);
	}

	/**
	 * Finds the positions of opening and closing quotes around the given offset.
	 * Verifies that quotes are part of the method invocation's argument list if invocation is provided.
	 * @param document the document
	 * @param offset the offset
	 * @param invocation optional method invocation to verify quotes are within its argument list
	 * @return QuotePositions with the quote positions, or invalid positions if not found or not valid
	 */
	public QuotePositions findQuotePositions(IDocument document, int offset, MethodInvocation invocation) {
		try {
			if (offset < 0 || offset > document.getLength()) {
				return new QuotePositions(-1, -1);
			}

			// Find the opening quote (backwards from offset)
			int openingQuote = -1;
			for (int i = offset - 1; i >= 0; i--) {
				char c = document.getChar(i);
				if (c == '"') {
					openingQuote = i;
					break;
				}
				if (c == '(' || c == ',' || Character.isWhitespace(c)) {
					break;
				}
			}

			// Find the closing quote (forwards from offset)
			int closingQuote = -1;
			for (int i = offset; i < document.getLength(); i++) {
				char c = document.getChar(i);
				if (c == '"') {
					closingQuote = i;
					break;
				}
			}

			QuotePositions quotes = new QuotePositions(openingQuote, closingQuote);

			// Verify quotes are within the method invocation's argument list if invocation is provided
			if (quotes.isValid() && invocation != null) {
				if (!areQuotesInArgumentList(document, quotes, invocation)) {
					return new QuotePositions(-1, -1);
				}
			}

			return quotes;
		} catch (BadLocationException e) {
			return new QuotePositions(-1, -1);
		}
	}

	/**
	 * Verifies that the quote positions are within the method invocation's argument list.
	 * This prevents matching quotes from comments or other string literals outside the invocation.
	 */
	private boolean areQuotesInArgumentList(IDocument document, QuotePositions quotes, MethodInvocation invocation) {
		try {
			ASTNode root = invocation.getRoot();
			if (!(root instanceof CompilationUnit rootCU)) {
				return false;
			}
			ICompilationUnit cu = (ICompilationUnit) rootCU.getJavaElement();
			if (cu == null) {
				return false;
			}
			String source = cu.getSource();
			if (source == null) {
				return false;
			}

			ASTNode nameNode = invocation.getName();
			if (nameNode == null) {
				return false;
			}
			int nameEnd = nameNode.getStartPosition() + nameNode.getLength();
			int invocationEnd = invocation.getStartPosition() + invocation.getLength();

			// Find opening and closing parentheses
			ParenthesisPositions parens = findParenthesisPositions(source, nameEnd, invocationEnd);
			if (parens == null) {
				return false;
			}
			int openParenPos = parens.openParenPos();
			int closeParenPos = parens.closeParenPos();

			// Verify quotes are within the parentheses (argument list)
			return quotes.openingQuote > openParenPos && quotes.closingQuote < closeParenPos;
		} catch (Exception e) {
			// If we can't verify, assume quotes are valid (fallback to original behavior)
			return true;
		}
	}

	/**
	 * Calculates the range to replace based on the prefix.
	 * When inside quotes, replaces the entire string content (from opening quote to closing quote).
	 * @param quotes the quote positions (can be invalid if not inside quotes)
	 */
	public Range calculateRange(IDocument document, int offset, String prefix, QuotePositions quotes) {
		try {
			if (quotes.isValid()) {
				// When inside quotes, replace the entire string content
				// Replace everything between the quotes (excluding the quotes themselves)
				int start = quotes.openingQuote + 1;
				int length = quotes.closingQuote - start;
				return JDTUtils.toRange(document, start, length);
			}

			// Fallback: replace just the prefix
			if (prefix.isEmpty()) {
				// If no prefix, just insert at the current position
				return JDTUtils.toRange(document, offset, 0);
			}
			// Calculate the start position of the prefix
			int start = offset - prefix.length();
			int length = prefix.length();
			return JDTUtils.toRange(document, start, length);
		} catch (Exception e) {
			// Fallback: create a simple range at the offset
			return createFallbackRange(document, offset);
		}
	}

	/**
	 * Creates a fallback range at the given offset when normal range calculation fails.
	 */
	private Range createFallbackRange(IDocument document, int offset) {
		try {
			return JDTUtils.toRange(document, offset, 0);
		} catch (Exception e) {
			try {
				int[] loc = org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers.toLine(document, offset);
				org.eclipse.lsp4j.Position pos = new org.eclipse.lsp4j.Position(loc[0], loc[1]);
				return new Range(pos, pos);
			} catch (Exception e2) {
				org.eclipse.lsp4j.Position pos = new org.eclipse.lsp4j.Position(0, 0);
				return new Range(pos, pos);
			}
		}
	}

	/**
	 * Checks if a character is valid in a resource bundle key.
	 */
	private boolean isKeyChar(char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-';
	}
}
