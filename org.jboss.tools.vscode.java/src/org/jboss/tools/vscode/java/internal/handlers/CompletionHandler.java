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
package org.jboss.tools.vscode.java.internal.handlers;

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
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.internal.contentassist.CompletionProposalRequestor;

public class CompletionHandler implements RequestHandler<TextDocumentPositionParams, CompletionList> {

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_COMPLETION.getMethod().equals(request);
	}

	@Override
	public CompletionList handle(TextDocumentPositionParams param, CancelMonitor cm) {
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(param.getTextDocument().getUri());
		List<CompletionItem> completionItems = this.computeContentAssist(unit,
				param.getPosition().getLine().intValue(),
				param.getPosition().getCharacter().intValue());
		JavaLanguageServerPlugin.logInfo("Completion request completed");
		return new CompletionList().withItems(completionItems);
	}

	private List<CompletionItem> computeContentAssist(ICompilationUnit unit, int line, int column) {
		if (unit == null) return Collections.emptyList();
		final List<CompletionItem> proposals = new ArrayList<>();
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
