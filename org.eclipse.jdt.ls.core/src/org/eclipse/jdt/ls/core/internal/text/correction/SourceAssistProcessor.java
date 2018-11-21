/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.text.edits.TextEdit;

public class SourceAssistProcessor {

	public List<CUCorrectionProposal> getAssists(IInvocationContext context, IProblemLocationCore[] locations) {
		ArrayList<CUCorrectionProposal> resultingCollections = new ArrayList<>();

		getOrganizeImportsProposal(context, resultingCollections);

		return resultingCollections;
	}

	private static void getOrganizeImportsProposal(IInvocationContext context, ArrayList<CUCorrectionProposal> resultingCollections) {
		ICompilationUnit unit = context.getCompilationUnit();

		CUCorrectionProposal proposal = new CUCorrectionProposal(CorrectionMessages.ReorgCorrectionsSubProcessor_organizeimports_description,
				CodeActionKind.SourceOrganizeImports,
				unit,
				null,
				IProposalRelevance.ORGANIZE_IMPORTS) {
			@Override
			protected void addEdits(IDocument document, TextEdit editRoot) throws CoreException {
				CompilationUnit astRoot = context.getASTRoot();
				OrganizeImportsOperation op = new OrganizeImportsOperation(unit, astRoot, true, false, true, null);
				editRoot.addChild(op.createTextEdit(null));
			}
		};

		resultingCollections.add(proposal);
	}
}
