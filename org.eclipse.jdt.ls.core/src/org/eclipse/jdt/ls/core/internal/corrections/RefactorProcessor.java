/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
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
			InvertBooleanUtility.getInverseConditionProposals(params, context, coveringNode, proposals);
			getInverseLocalVariableProposals(params, context, coveringNode, proposals);

			return proposals;
		}
		return Collections.emptyList();
	}

	private boolean getInverseLocalVariableProposals(CodeActionParams params, IInvocationContext context, ASTNode covering, Collection<ChangeCorrectionProposal> proposals) {
		if (proposals == null) {
			return false;
		}

		ChangeCorrectionProposal proposal = null;
		if (this.preferenceManager.getClientPreferences().isAdvancedExtractRefactoringSupported()) {
			proposal = InvertBooleanUtility.getInvertVariableProposal(params, context, covering, true /*returnAsCommand*/);
		} else {
			proposal = InvertBooleanUtility.getInvertVariableProposal(params, context, covering, false /*returnAsCommand*/);
		}

		if (proposal == null) {
			return false;
		}

		proposals.add(proposal);
		return true;
	}

}
