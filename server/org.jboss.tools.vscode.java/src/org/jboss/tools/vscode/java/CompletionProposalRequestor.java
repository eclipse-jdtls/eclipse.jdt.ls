package org.jboss.tools.vscode.java;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.jboss.tools.vscode.java.model.CodeCompletionItem;

public final class CompletionProposalRequestor extends CompletionRequestor {
	private final List<CodeCompletionItem> proposals;
	private final ICompilationUnit unit;
	private CompletionProposalReplacementProvider proposalProvider;
	private CompletionProposalDescriptionProvider descriptionProvider;
	

	public CompletionProposalRequestor( ICompilationUnit aUnit, List<CodeCompletionItem> proposals) {
		this.proposals = proposals;
		this.unit = aUnit;
	}

	@Override
	public void accept(CompletionProposal proposal) {
		if(isIgnored(proposal.getKind())) return;
		final CodeCompletionItem $ = new CodeCompletionItem(proposal);
		StringBuilder description = this.descriptionProvider.createDescription(proposal);
		$.setLabel(description.toString());
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
}