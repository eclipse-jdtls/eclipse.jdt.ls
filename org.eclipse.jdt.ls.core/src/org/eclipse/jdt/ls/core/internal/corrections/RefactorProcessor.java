package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeActionParams;

/**
 * RefactorProcessor
 */
public class RefactorProcessor {

	private PreferenceManager preferenceManager;

	public RefactorProcessor(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<ChangeCorrectionProposal> getProposals(CodeActionParams params, IInvocationContext context) throws CoreException {
		ASTNode coveringNode = context.getCoveringNode();
		if (coveringNode != null) {
			ArrayList<ChangeCorrectionProposal> proposals = new ArrayList<>();
			// TODO (Yan): Move refactor proposals here.
			return proposals;
		}
		return Collections.emptyList();
	}

}
