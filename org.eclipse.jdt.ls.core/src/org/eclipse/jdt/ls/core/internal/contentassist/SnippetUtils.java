/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore.PositionInformation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore.ProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.LinkedCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.extended.SnippetTextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

public class SnippetUtils {

	private static final String MARKDOWN_LANGUAGE = "java";

	private static final String TM_SELECTED_TEXT = "\\$TM_SELECTED_TEXT";
	private static final String TM_FILENAME_BASE = "\\$TM_FILENAME_BASE";

	public static final String SNIPPET_PREFIX = "${";
	public static final char SNIPPET_CHOICE_INDICATOR = '|';
	public static final String SNIPPET_CHOICE_POSTFIX = "|}";
	public static final String SNIPPET_CHOICE_SEPARATOR = ",";

	private SnippetUtils() {
	}


	/**
	 * Evaluate template without any context.
	 * @param pattern template pattern
	 * @return snippet string
	 */
	public static String templateToSnippet(String pattern) {
		// $${1:${variable}} -> ${1:variable}
		String evaluated = pattern.replaceAll("\\$\\$\\{(\\d):\\$\\{(.*?)\\}(.*?)\\}", "\\${$1:$2$3}");

		// escape $$.
		// E.g. $${0} -> ${0}, $$TM_SELECTED_TEXT -> $TM_SELECTED_TEXT
		String escaped = evaluated.replaceAll("\\$\\$", "\\$");
		return escaped;
	}

	public static Either<String, MarkupContent> beautifyDocument(String raw) {
		// remove the list of choices and replace with the first choice (e.g. |public,protected,private| -> public)
		String escapedString = raw.replaceAll("\\$\\{\\d\\|(.*?),.*?\\}", "$1");
		// remove the placeholder for the plain cursor like: ${0}, ${1:variable}
		escapedString = escapedString.replaceAll("\\$\\{\\d:?(.*?)\\}", "$1");
		// Replace the reserved variable with empty string.
		// See: https://github.com/eclipse/eclipse.jdt.ls/issues/1220
		escapedString = escapedString.replaceAll(TM_SELECTED_TEXT, "");
		escapedString = escapedString.replaceAll(TM_FILENAME_BASE, "");

		if (JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences() != null
				&& JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isSupportsCompletionDocumentationMarkdown()) {
			MarkupContent markupContent = new MarkupContent();
			markupContent.setKind(MarkupKind.MARKDOWN);
			markupContent.setValue(String.format("```%s\n%s\n```", MARKDOWN_LANGUAGE, escapedString));
			return Either.forRight(markupContent);
		} else {
			return Either.forLeft(escapedString);
		}
	}

	/***
	 * Supports linked correction proposals by converting them to snippets.
	 * Represents a
	 * {@link org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore}
	 * with snippet choice syntax if the group has multiple proposals, otherwise
	 * represents it as a placeholder.
	 *
	 * @param proposal
	 *            the proposal to add support for
	 * @param edit
	 *            the current edit to be returned with the code action
	 * @throws CoreException
	 */
	public static final void addSnippetsIfApplicable(LinkedCorrectionProposalCore proposal, WorkspaceEdit edit) throws CoreException {
		ICompilationUnit compilationUnit = proposal.getCompilationUnit();
		String proposalUri = JDTUtils.toURI(compilationUnit);
		IBuffer buffer = compilationUnit.getBuffer();
		LinkedProposalModelCore linkedProposals = proposal.getLinkedProposalModel();
		List<Triple> snippets = new ArrayList<>();
		Iterator<LinkedProposalPositionGroupCore> it = linkedProposals.getPositionGroupCoreIterator();

		while (it.hasNext()) {
			LinkedProposalPositionGroupCore group = it.next();
			ProposalCore[] proposalList = group.getProposals();
			PositionInformation[] positionList = group.getPositions();
			// Sorts in ascending order to ensure first position in list has the smallest offset
			Arrays.sort(positionList, (p1, p2) -> {
				return p1.getOffset() - p2.getOffset();
			});
			StringBuilder snippet = new StringBuilder();
			snippet.append(SNIPPET_CHOICE_INDICATOR);

			for (int i = 0; i < positionList.length; i++) {
				int offset = positionList[i].getOffset();
				int length = positionList[i].getLength();

				// Create snippet on first iteration
				if (i == 0) {
					LinkedPosition linkedPosition = new LinkedPosition(JsonRpcHelpers.toDocument(buffer), positionList[i].getOffset(), positionList[i].getLength(), positionList[i].getSequenceRank());

					// Groups with no proposals will have the snippet text added while amending the WorkspaceEdit
					for (int j = 0; j < proposalList.length; j++) {
						org.eclipse.text.edits.TextEdit editWithText = findReplaceEdit(proposalList[j].computeEdits(0, linkedPosition, '\u0000', 0, new LinkedModeModel()));
						if (editWithText != null) {
							snippet.append(((ReplaceEdit) editWithText).getText());
							snippet.append(SNIPPET_CHOICE_SEPARATOR);
						}
					}
					if (String.valueOf(snippet.charAt(snippet.length() - 1)).equals(SNIPPET_CHOICE_SEPARATOR)) {
						snippet.deleteCharAt(snippet.length() - 1);
					}
					snippet.append(SNIPPET_CHOICE_POSTFIX);
					// If snippet only has one choice, remove choice indicators
					if (snippet.indexOf(SNIPPET_CHOICE_SEPARATOR) == -1) {
						snippet.setCharAt(0, ':');
						snippet.deleteCharAt(snippet.length() - 2);
					}
					// Snippet is added with smallest offset as 0th element
					snippets.add(new Triple(snippet.toString(), offset, length));
				} else {
					// Add offset/length values from additional positions in group to previously created snippet
					Triple currentSnippet = snippets.get(snippets.size() - 1);
					currentSnippet.offset.add(offset);
					currentSnippet.length.add(length);
				}
			}
		}
		if (!snippets.isEmpty()) {
			// Sort snippets based on offset of earliest occurrence to enable correct numbering
			snippets.sort(null);
			int snippetNumber = 1;
			for (int i = snippets.size() - 1; i >= 0; i--) {
				Triple element = snippets.get(i);
				element.snippet = SNIPPET_PREFIX + snippetNumber + element.snippet;
				snippetNumber++;
				// Separate snippets with multiple positions into individual instances in list
				for (int j = 1; j < element.offset.size(); j++) {
					snippets.add(new Triple(element.snippet.toString(), element.offset.get(j), element.length.get(j)));
					element.offset.remove(j);
					element.length.remove(j);
				}
			}
			// Re-sort snippets (with the added individual instances) by offset in descending order,
			// so that the amendments to the text edit are applied in an order that does not alter the offset of later amendments
			snippets.sort(null);
			for (int i = 0; i < edit.getDocumentChanges().size(); i++) {
				if (edit.getDocumentChanges().get(i).isLeft()) {
					List<TextEdit> edits = edit.getDocumentChanges().get(i).getLeft().getEdits();
					String editUri = edit.getDocumentChanges().get(i).getLeft().getTextDocument().getUri();
					for (int j = 0; j < edits.size(); j++) {
						Range editRange = edits.get(j).getRange();
						StringBuilder replacementText = new StringBuilder(edits.get(j).getNewText());
						int rangeStart = JsonRpcHelpers.toOffset(buffer, editRange.getStart().getLine(), editRange.getStart().getCharacter());
						int rangeEnd = rangeStart + replacementText.length();

						for (int k = 0; k < snippets.size(); k++) {
							Triple currentSnippet = snippets.get(k);
							if (proposalUri.equals(editUri) && currentSnippet.offset.get(0) >= rangeStart && currentSnippet.offset.get(0) <= rangeEnd) {
								int replaceStart = currentSnippet.offset.get(0) - rangeStart;
								int replaceEnd = replaceStart + currentSnippet.length.get(0);
								// If snippet text has not been added due to no elements in the proposal list, create snippet based on the text in the given position range
								if (currentSnippet.snippet.endsWith(":}")) {
									currentSnippet.snippet = currentSnippet.snippet.replaceFirst(":", ":" + replacementText.substring(replaceStart, replaceEnd));
								}
								replacementText.replace(replaceStart, replaceEnd, currentSnippet.snippet);
							}
						}

						SnippetTextEdit newEdit = new SnippetTextEdit(editRange, replacementText.toString());
						edits.set(j, newEdit);
					}
				}
			}
		}
	}

	private static final org.eclipse.text.edits.TextEdit findReplaceEdit(org.eclipse.text.edits.TextEdit edit) {
		if (edit instanceof ReplaceEdit && !((ReplaceEdit) edit).getText().isBlank()) {
			return edit;
		}

		if (edit instanceof MultiTextEdit) {
			for (org.eclipse.text.edits.TextEdit child : edit.getChildren()) {
				org.eclipse.text.edits.TextEdit replaceEdit = findReplaceEdit(child);
				if (replaceEdit != null) {
					return replaceEdit;
				}
			}
		}
		return null;
	}

	private static final class Triple implements Comparable<Triple> {
		public String snippet;
		public List<Integer> offset = new ArrayList<>();
		public List<Integer> length = new ArrayList<>();

		Triple(String snippet, int offset, int length) {
			this.snippet = snippet;
			this.offset.add(offset);
			this.length.add(length);
		}

		// Sorts in descending order based on 0th (smallest) element of offset list
		@Override
		public int compareTo(Triple other) {
			return other.offset.get(0) - this.offset.get(0);
		}
	}
}
