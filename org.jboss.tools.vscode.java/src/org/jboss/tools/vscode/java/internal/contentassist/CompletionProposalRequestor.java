/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.contentassist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.jboss.tools.vscode.java.internal.handlers.CompletionResolveHandler;

public final class CompletionProposalRequestor extends CompletionRequestor {

	private final List<CompletionItem> proposals;
	private final ICompilationUnit unit;
	private final int triggerOffset;
	private CompletionProposalReplacementProvider proposalProvider;
	private CompletionProposalDescriptionProvider descriptionProvider;


	public CompletionProposalRequestor( ICompilationUnit aUnit, List<CompletionItem> proposals, int offset) {
		this.proposals = proposals;
		this.unit = aUnit;
		this.triggerOffset = offset;
		setRequireExtendedContext(true);
	}

	@Override
	public void accept(CompletionProposal proposal) {
		if(isIgnored(proposal.getKind())) return;
		final CompletionItem $ = new CompletionItem();
		$.setKind(mapKind(proposal.getKind()));
		Map<String, String> data = new HashMap<>();
		// append data field so that resolve request can use it.
		data.put(CompletionResolveHandler.DATA_FIELD_URI,unit.getResource().getLocationURI().toString());
		$.setData(data);
		this.descriptionProvider.updateDescription(proposal, $);
		this.proposalProvider.updateReplacement(proposal,$, '\0',new ArrayList<Integer>());
		$.setSortText(SortTextHelper.computeSortText(proposal));
		proposals.add($);
	}

	@Override
	public void acceptContext(CompletionContext context) {
		super.acceptContext(context);
		this.proposalProvider = new CompletionProposalReplacementProvider(unit,context, triggerOffset);
		this.descriptionProvider = new CompletionProposalDescriptionProvider(context);
	}


	private CompletionItemKind mapKind(final int kind) {
		switch (kind) {
		case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
		case CompletionProposal.CONSTRUCTOR_INVOCATION:
			return CompletionItemKind.Constructor;
		case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
		case CompletionProposal.TYPE_REF:
			return CompletionItemKind.Class;
		case CompletionProposal.FIELD_IMPORT:
		case CompletionProposal.METHOD_IMPORT:
		case CompletionProposal.METHOD_NAME_REFERENCE:
		case CompletionProposal.PACKAGE_REF:
		case CompletionProposal.TYPE_IMPORT:
			return CompletionItemKind.Module;
		case CompletionProposal.FIELD_REF:
		case CompletionProposal.FIELD_REF_WITH_CASTED_RECEIVER:
			return CompletionItemKind.Field;
		case CompletionProposal.KEYWORD:
			return CompletionItemKind.Keyword;
		case CompletionProposal.LABEL_REF:
			return CompletionItemKind.Reference;
		case CompletionProposal.LOCAL_VARIABLE_REF:
		case CompletionProposal.VARIABLE_DECLARATION:
			return CompletionItemKind.Variable;
		case CompletionProposal.METHOD_DECLARATION:
		case CompletionProposal.METHOD_REF:
		case CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER:
		case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
			return CompletionItemKind.Function;
			//text
		case CompletionProposal.ANNOTATION_ATTRIBUTE_REF:
		case CompletionProposal.JAVADOC_BLOCK_TAG:
		case CompletionProposal.JAVADOC_FIELD_REF:
		case CompletionProposal.JAVADOC_INLINE_TAG:
		case CompletionProposal.JAVADOC_METHOD_REF:
		case CompletionProposal.JAVADOC_PARAM_REF:
		case CompletionProposal.JAVADOC_TYPE_REF:
		case CompletionProposal.JAVADOC_VALUE_REF:
		default:
			return CompletionItemKind.Text;
		}
	}

	@Override
	public void setIgnored(int completionProposalKind, boolean ignore) {
		super.setIgnored(completionProposalKind, ignore);
		if (completionProposalKind == CompletionProposal.METHOD_DECLARATION && !ignore) {
			setRequireExtendedContext(true);
		}
	}
}