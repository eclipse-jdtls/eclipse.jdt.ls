package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.langs.CompletionItem;
import org.jboss.tools.langs.CompletionList;
import org.jboss.tools.langs.TextDocumentPositionParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.CompletionProposalRequestor;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.model.CodeCompletionItem;

public class CompletionHandler extends AbstractRequestHandler implements RequestHandler<TextDocumentPositionParams, CompletionList> {
	
	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_COMPLETION.getMethod().equals(request);
	}

	@Override
	public CompletionList handle(TextDocumentPositionParams param) {
		ICompilationUnit unit = resolveCompilationUnit(param.getTextDocument().getUri());
		List<CodeCompletionItem> proposals = this.computeContentAssist(unit, 
				param.getPosition().getLine().intValue(), 
				param.getPosition().getCharacter().intValue());
		List<CompletionItem> completionItems = new ArrayList<CompletionItem>();
		for (CodeCompletionItem p : proposals) {
			completionItems.add(new CompletionItem()
					.withLabel(p.getLabel())
					.withKind(Double.valueOf(p.getKind()))
					.withInsertText(p.getInsertText()));
		}
		JavaLanguageServerPlugin.logInfo("Completion request completed");
		return new CompletionList().withItems(completionItems);
	}
	
	private List<CodeCompletionItem> computeContentAssist(ICompilationUnit unit, int line, int column) {
		if (unit == null) return Collections.emptyList();
		final List<CodeCompletionItem> proposals = new ArrayList<CodeCompletionItem>();
		final CompletionContext[] completionContextParam = new CompletionContext[] { null };
		try {
			CompletionRequestor collector = new CompletionProposalRequestor(unit, proposals);
			// Allow completions for unresolved types - since 3.3
			collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.TYPE_REF, true);
			collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.TYPE_IMPORT, true);
			collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.FIELD_IMPORT, true);

			collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.TYPE_REF, true);
			collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.TYPE_IMPORT, true);
			collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.METHOD_IMPORT, true);

			collector.setAllowsRequiredProposals(CompletionProposal.CONSTRUCTOR_INVOCATION, CompletionProposal.TYPE_REF, true);

			collector.setAllowsRequiredProposals(CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION, CompletionProposal.TYPE_REF, true);
			collector.setAllowsRequiredProposals(CompletionProposal.ANONYMOUS_CLASS_DECLARATION, CompletionProposal.TYPE_REF, true);

			collector.setAllowsRequiredProposals(CompletionProposal.TYPE_REF, CompletionProposal.TYPE_REF, true);
			
			unit.codeComplete(JsonRpcHelpers.toOffset(unit.getBuffer(), line, column), collector, new NullProgressMonitor());
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem with codeComplete for" +  unit.getElementName(), e);
		}
		return proposals;
	}	
}
