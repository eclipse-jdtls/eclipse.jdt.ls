/*******************************************************************************
 * Copyright (c) 2018 TypeFox and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     TypeFox - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.highlighting;

import static com.google.common.collect.Lists.newArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.internal.ui.javaeditor.HighlightedPositionCore;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.highlighting.SemanticHighlightingService.HighlightedPositionDiffContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.lsp4j.SemanticHighlightingInformation;
import org.eclipse.lsp4j.util.SemanticHighlightingTokens;

import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

public class SemanticHighlightingDiffCalculator {

	public List<SemanticHighlightingInformation> getDiffInfos(HighlightedPositionDiffContext context) throws BadLocationException {

		IDocument newState = context.newState;
		IDocument oldState = context.oldState;

		// Can be negative or zero too.
		int lineShiftCount = this.getLineShift(oldState, context.event);

		int eventOffset = context.event.getOffset();
		int eventOldLength = context.event.getLength();
		int eventEnd = eventOffset + eventOldLength;

		Map<Integer, SemanticHighlightingInformation> infosPerLine = Maps.newHashMap();
		Multimap<Integer, SemanticHighlightingTokens.Token> tokensPerLine = HashMultimap.create();
		Multimap<Integer, HighlightedPositionCore> pendingPositions = HashMultimap.create();
		Map<LookupKey, HighlightedPositionCore> newPositions = Maps.newHashMap();
		for (HighlightedPositionCore newPosition : context.newPositions) {
			LookupKey key = createKey(newState, newPosition);
			newPositions.put(key, newPosition);
			pendingPositions.put(key.line, newPosition);
		}

		for (HighlightedPositionCore oldPosition : context.oldPositions) {
			int[] oldLineAndColumn = getLineAndColumn(oldState, oldPosition);
			int originalOldLine = oldLineAndColumn[0];
			int oldColumn = oldLineAndColumn[1];
			int oldOffset = oldPosition.getOffset();
			int oldLength = oldPosition.getLength();
			int oldEnd = oldOffset + oldLength;
			// If the position is before the change (event), no need to shift the line. Otherwise we consider the line shift.
			int adjustedOldLine = oldEnd < eventEnd ? originalOldLine : originalOldLine + lineShiftCount;

			@SuppressWarnings("unchecked")
			int scope = SemanticHighlightingService.getIndex((List<String>) oldPosition.getHighlighting());
			LookupKey key = createKey(adjustedOldLine, oldColumn, getTextAt(oldState, oldPosition), scope);
			HighlightedPositionCore newPosition = newPositions.remove(key);
			if (newPosition == null && !infosPerLine.containsKey(originalOldLine)) {
				infosPerLine.put(originalOldLine, new SemanticHighlightingInformation(originalOldLine, null));
			}
		}

		for (Entry<LookupKey, HighlightedPositionCore> entries : newPositions.entrySet()) {
			LookupKey lookupKey = entries.getKey();
			int line = lookupKey.line;
			int length = lookupKey.text.length();
			int character = lookupKey.column;
			int scope = lookupKey.scope;
			SemanticHighlightingInformation info = infosPerLine.get(line);
			if (info == null) {
				info = new SemanticHighlightingInformation(line, null);
				infosPerLine.put(line, info);
			}
			tokensPerLine.put(line, new SemanticHighlightingTokens.Token(character, length, scope));
			// If a line contains at least one change, we need to invalidate the entire line by consuming all pending positions.
			Collection<HighlightedPositionCore> pendings = pendingPositions.removeAll(line);
			if (pendings != null) {
				for (HighlightedPositionCore pendingPosition : pendings) {
					if (pendingPosition != entries.getValue()) {
						int[] lineAndColumn = getLineAndColumn(newState, pendingPosition);
						int pendingCharacter = lineAndColumn[1];
						int pendingLength = pendingPosition.length;
						@SuppressWarnings("unchecked")
						int pendingScope = SemanticHighlightingService.getIndex((List<String>) pendingPosition.getHighlighting());
						tokensPerLine.put(line, new SemanticHighlightingTokens.Token(pendingCharacter, pendingLength, pendingScope));
					}
				}
			}
		}

		for (Entry<Integer, Collection<SemanticHighlightingTokens.Token>> entry : tokensPerLine.asMap().entrySet()) {
			List<SemanticHighlightingTokens.Token> tokens = newArrayList(entry.getValue());
			Collections.sort(tokens);
			infosPerLine.get(entry.getKey()).setTokens(SemanticHighlightingTokens.encode(tokens));
		}

		return FluentIterable.from(infosPerLine.values()).toSortedList(HighlightingInformationComparator.INSTANCE);
	}

	protected int[] getLineAndColumn(IDocument document, HighlightedPositionCore position) {
		//@formatter:off
		int[] lineAndColumn = JsonRpcHelpers.toLine(document, position.offset);
		Assert.isNotNull(
				lineAndColumn,
				"Cannot retrieve the line and column information for document. Position was: " + position + " Document was:>" + document.get() + "<."
				);
		return lineAndColumn;
		//@formatter:off
	}

	protected int getLineShift(IDocument oldState, DocumentEvent event) throws BadLocationException {
		// Insert edit.
		if (event.fLength == 0) {
			Preconditions.checkNotNull(event.fText, "fText");
			int beforeEndLine = event.fDocument.getLineOfOffset(event.fOffset + event.fLength);
			int afterEndLine = event.fDocument.getLineOfOffset(event.fOffset + event.fText.length());
			return afterEndLine - beforeEndLine;
		// Delete edit.
		} else if (event.fText == null || event.fText.isEmpty()) {
			return event.fDocument.getLineOfOffset(event.fOffset) - oldState.getLineOfOffset(event.fOffset + event.fLength);
		// Replace edit.
		} else {
			int startLine = oldState.getLineOfOffset(event.fOffset + event.fLength);
			int lineShift = event.fDocument.getLineOfOffset(event.fOffset + event.fText.length()) - startLine;
			return lineShift;
		}
	}

	protected String getTextAt(IDocument document, Position position) throws BadLocationException {
		return document.get(position.offset, position.length);
	}

	protected LookupKey createKey(IDocument document, HighlightedPositionCore position) throws BadLocationException {
		int[] lineAndColumn = getLineAndColumn(document, position);
		@SuppressWarnings("unchecked")
		int scope = SemanticHighlightingService.getIndex((List<String>) position.getHighlighting());
		return createKey(lineAndColumn[0], lineAndColumn[1], getTextAt(document, position), scope);
	}

	protected LookupKey createKey(int line, int column, String text, int scope) {
		return new LookupKey(line, column, text, scope);
	}

	protected static class HighlightingInformationComparator implements Comparator<SemanticHighlightingInformation> {

		protected static final Comparator<SemanticHighlightingInformation> INSTANCE = new HighlightingInformationComparator();

		@Override
		public int compare(SemanticHighlightingInformation left, SemanticHighlightingInformation right) {
			//@formatter:off
			return ComparisonChain.start()
				.compare(left.getLine(), right.getLine())
				.result();
			//@formatter:on
		}
	}

	private static final class LookupKey {

		private final int line;
		private final int column;
		private final String text;
		private final int scope;

		private LookupKey(int line, int column, String text, int scope) {
			this.line = line;
			this.column = column;
			this.text = text;
			this.scope = scope;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + column;
			result = prime * result + line;
			result = prime * result + scope;
			result = prime * result + ((text == null) ? 0 : text.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			LookupKey other = (LookupKey) obj;
			if (column != other.column) {
				return false;
			}
			if (line != other.line) {
				return false;
			}
			if (scope != other.scope) {
				return false;
			}
			if (text == null) {
				if (other.text != null) {
					return false;
				}
			} else if (!text.equals(other.text)) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("Line: ");
			sb.append(line);
			sb.append("\nColumn: ");
			sb.append(column);
			sb.append("\nText: ");
			sb.append(text);
			sb.append("\nScopes: ");
			sb.append(Iterables.toString(SemanticHighlightingService.getScopes(scope)));
			return sb.toString();
		}

	}

}
