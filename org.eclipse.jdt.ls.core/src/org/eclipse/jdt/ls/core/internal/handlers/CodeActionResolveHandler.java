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

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeCorrectionProposal;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class CodeActionResolveHandler {
	public static final String DATA_FIELD_REQUEST_ID = "rid";
	public static final String DATA_FIELD_PROPOSAL_ID = "pid";

	public CodeAction resolve(CodeAction params, IProgressMonitor monitor) {
		Map<String, String> data = JSONUtility.toModel(params.getData(), Map.class);
		// clean resolve data
		params.setData(null);
		if (CodeActionHandler.codeActionStore.isEmpty()) {
			return params;
		}

		int proposalId = Integer.parseInt(data.get(DATA_FIELD_PROPOSAL_ID));
		long requestId = Long.parseLong(data.get(DATA_FIELD_REQUEST_ID));
		ResponseStore.ResponseItem<Either<ChangeCorrectionProposal, CodeActionProposal>> response = CodeActionHandler.codeActionStore.get(requestId);
		if (response == null || response.getProposals().size() <= proposalId) {
			throw new IllegalStateException("Invalid codeAction proposal");
		}

		try {
			Either<ChangeCorrectionProposal, CodeActionProposal> proposal = response.getProposals().get(proposalId);
			WorkspaceEdit edit = proposal.isLeft() ? ChangeUtil.convertToWorkspaceEdit(proposal.getLeft().getChange())
				: proposal.getRight().resolveEdit(monitor);
			if (ChangeUtil.hasChanges(edit)) {
				params.setEdit(edit);
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem converting code action proposal to workspace edit", e);
		}

		return params;
	}
}
