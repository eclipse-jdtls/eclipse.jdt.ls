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
package org.jboss.tools.vscode.java.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.jboss.tools.langs.CompletionItem;
import org.jboss.tools.vscode.java.internal.handlers.CompletionResolveHandler;

public final class CompletionProposalRequestor extends CompletionRequestor {
	private final List<CompletionItem> proposals;
	private final ICompilationUnit unit;
	private CompletionProposalReplacementProvider proposalProvider;
	private CompletionProposalDescriptionProvider descriptionProvider;


	public CompletionProposalRequestor( ICompilationUnit aUnit, List<CompletionItem> proposals) {
		this.proposals = proposals;
		this.unit = aUnit;
	}

	@Override
	public void accept(CompletionProposal proposal) {
		if(isIgnored(proposal.getKind())) return;
		final CompletionItem $ = new CompletionItem();
		$.setKind(mapKind(proposal.getKind()));
		Map<String, String> data = new HashMap<>();
		data.put(CompletionResolveHandler.DATA_FIELD_URI,unit.getResource().getLocationURI().toString());
		$.setData(data);
		this.descriptionProvider.updateDescription(proposal, $);
		StringBuilder replacement = this.proposalProvider.createReplacement(proposal,' ',new ArrayList<Integer>());
		$.setInsertText(replacement.toString());
		proposals.add($);
	}

	@Override
	public void acceptContext(CompletionContext context) {
		super.acceptContext(context);
		this.proposalProvider = new CompletionProposalReplacementProvider(unit,context);
		this.descriptionProvider = new CompletionProposalDescriptionProvider(context);
	}


	private int mapKind(final int kind) {
		switch (kind) {
		case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
		case CompletionProposal.CONSTRUCTOR_INVOCATION:
			return 4;//Constructor
		case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
		case CompletionProposal.TYPE_REF:
			return 7;// Class
		case CompletionProposal.FIELD_IMPORT:
		case CompletionProposal.METHOD_IMPORT:
		case CompletionProposal.METHOD_NAME_REFERENCE:
		case CompletionProposal.PACKAGE_REF:
		case CompletionProposal.TYPE_IMPORT:
			return 9;//Module
		case CompletionProposal.FIELD_REF:
		case CompletionProposal.FIELD_REF_WITH_CASTED_RECEIVER:
			return 5;//Field
		case CompletionProposal.KEYWORD:
			return 14;//Keyword
		case CompletionProposal.LABEL_REF:
			return 18;//Reference
		case CompletionProposal.LOCAL_VARIABLE_REF:
		case CompletionProposal.VARIABLE_DECLARATION:
			return 6; //Variable
		case CompletionProposal.METHOD_DECLARATION:
		case CompletionProposal.METHOD_REF:
		case CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER:
		case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
			return 3;//Function
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
			return 1; //Text
		}
		// vscode kinds
		//			Text = 1,
		//				  Method = 2,
		//				  Function = 3,
		//				  Constructor = 4,
		//				  Field = 5,
		//				  Variable = 6,
		//				  Class = 7,
		//				  Interface = 8,
		//				  Module = 9,
		//				  Property = 10,
		//				  Unit = 11,
		//				  Value = 12,
		//				  Enum = 13,
		//				  Keyword = 14,
		//				  Snippet = 15,
		//				  Color = 16,
		//				  File = 17,
		//				  Reference = 18
	}
}