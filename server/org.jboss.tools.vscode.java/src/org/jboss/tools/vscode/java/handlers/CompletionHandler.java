package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.vscode.java.CompletionProposalRequestor;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.model.CodeCompletionItem;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class CompletionHandler extends AbstractRequestHandler implements RequestHandler<TextDocumentPositionParams, CompletionList> {
	private static final String REQ_COMPLETION = "textDocument/completion";
	private final DocumentsManager dm;
	
	public CompletionHandler(DocumentsManager manager) {
		this.dm = manager;
	}
	
	@Override
	public boolean canHandle(String request) {
		return REQ_COMPLETION.equals(request); 
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		ICompilationUnit unit = this.resolveCompilationUnit(request);
		int[] position = JsonRpcHelpers.readTextDocumentPosition(request);
		
		List<CodeCompletionItem> proposals = this.computeContentAssist(unit, position[0], position[1]);
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
		for (CodeCompletionItem p : proposals) {
			Map<String,Object> completionItem = new HashMap<String,Object>();
			completionItem.put("label",p.getLabel());
			completionItem.put("kind", p.getKind());
			completionItem.put("insertText",p.getInsertText());
			result.add(completionItem);
		}
		response.setResult(result);
		JavaLanguageServerPlugin.logInfo("Completion request completed");
		return response;
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
	
	@Override
	public void process(JSONRPC2Notification request) {
		//not implemented
	}

	@Override
	public CompletionList handle(TextDocumentPositionParams param) {
		// TODO Auto-generated method stub
		return null;
	}
}
