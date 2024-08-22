/*******************************************************************************
* Copyright (c) 2021 Microsoft Corporation and others.
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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore.PositionInformation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore.ProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.LinkedCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.extended.SnippetTextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

public class CodeActionResolveHandler {
	public static final String DATA_FIELD_REQUEST_ID = "rid";
	public static final String DATA_FIELD_PROPOSAL_ID = "pid";
	public static final String SNIPPET_PREFIX = "${";
	public static final char SNIPPET_CHOICE_INDICATOR = '|';
	public static final String SNIPPET_CHOICE_POSTFIX = "|}";
	public static final String SNIPPET_CHOICE_DELIMITER = ",";

	public CodeAction resolve(CodeAction params, IProgressMonitor monitor) {
		Map<String, String> data = JSONUtility.toModel(params.getData(), Map.class);
		// clean resolve data
		params.setData(null);
		if (CodeActionHandler.codeActionStore.isEmpty()) {
			return params;
		}

		int proposalId = Integer.parseInt(data.get(DATA_FIELD_PROPOSAL_ID));
		long requestId = Long.parseLong(data.get(DATA_FIELD_REQUEST_ID));
		ResponseStore.ResponseItem<Either<ChangeCorrectionProposalCore, CodeActionProposal>> response = CodeActionHandler.codeActionStore.get(requestId);
		if (response == null || response.getProposals().size() <= proposalId) {
			throw new IllegalStateException("Invalid codeAction proposal");
		}

		try {
			Either<ChangeCorrectionProposalCore, CodeActionProposal> proposal = response.getProposals().get(proposalId);
			WorkspaceEdit edit = proposal.isLeft() ? ChangeUtil.convertToWorkspaceEdit(proposal.getLeft().getChange()) : proposal.getRight().resolveEdit(monitor);
			if (JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isWorkspaceSnippetEditSupported() && proposal.isLeft() && proposal.getLeft() instanceof LinkedCorrectionProposalCore) {
				addSnippetsIfApplicable((LinkedCorrectionProposalCore) proposal.getLeft(), edit);
			}
			if (ChangeUtil.hasChanges(edit)) {
				params.setEdit(edit);
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem converting code action proposal to workspace edit", e);
		}

		return params;
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
	private static final void addSnippetsIfApplicable(LinkedCorrectionProposalCore proposal, WorkspaceEdit edit) throws CoreException {
		ICompilationUnit compilationUnit = proposal.getCompilationUnit();
		IBuffer buffer = compilationUnit.getBuffer();
		LinkedProposalModelCore linkedProposals = proposal.getLinkedProposalModel();
		List<Triple> snippets = new ArrayList<>();
		Iterator<LinkedProposalPositionGroupCore> it = linkedProposals.getPositionGroupCoreIterator();

		while (it.hasNext()) {
			LinkedProposalPositionGroupCore group = it.next();
			ProposalCore[] proposalList = group.getProposals();
			PositionInformation[] positionList = group.getPositions();
			// Sorts in ascending order to ensure first position in list has the smallest offset
			Arrays.sort(positionList, new Comparator<PositionInformation>() {
				@Override
				public int compare(PositionInformation p1, PositionInformation p2) {
					return p1.getOffset() - p2.getOffset();
				}
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
						org.eclipse.text.edits.TextEdit editWithText = findReplaceOrInsertEdit(proposalList[j].computeEdits(0, linkedPosition, '\u0000', 0, new LinkedModeModel()));
						if (editWithText != null) {
							if (snippet.charAt(snippet.length() - 1) != SNIPPET_CHOICE_INDICATOR) {
								snippet.append(SNIPPET_CHOICE_DELIMITER);
							}
							snippet.append(((ReplaceEdit) editWithText).getText());
						}
					}

					snippet.append(SNIPPET_CHOICE_POSTFIX);
					// If snippet only has one choice, remove choice indicators
					if (snippet.indexOf(SNIPPET_CHOICE_DELIMITER) == -1) {
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
					for (int j = 0; j < edits.size(); j++) {
						Range editRange = edits.get(j).getRange();
						StringBuilder replacementText = new StringBuilder(edits.get(j).getNewText());
						int rangeStart = JsonRpcHelpers.toOffset(buffer, editRange.getStart().getLine(), editRange.getStart().getCharacter());
						int rangeEnd = rangeStart + replacementText.length();

						for (int k = 0; k < snippets.size(); k++) {
							Triple currentSnippet = snippets.get(k);
							if (currentSnippet.offset.get(0) >= rangeStart && currentSnippet.offset.get(0) <= rangeEnd) {
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

	private static final org.eclipse.text.edits.TextEdit findReplaceOrInsertEdit(org.eclipse.text.edits.TextEdit edit) {
		if (edit instanceof ReplaceEdit && !((ReplaceEdit) edit).getText().isBlank()) {
			return edit;
		}

		if (edit instanceof MultiTextEdit) {
			org.eclipse.text.edits.TextEdit[] children = edit.getChildren();
			for (int i = 0; i < children.length; i++) {
				org.eclipse.text.edits.TextEdit child = findReplaceOrInsertEdit(children[i]);
				if (child != null) {
					return child;
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
