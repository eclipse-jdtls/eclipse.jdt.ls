/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.Collection;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.ui.text.correction.SuppressWarningsBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.SuppressWarningsProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;

public class SuppressWarningsSubProcessor extends SuppressWarningsBaseSubProcessor<ProposalKindWrapper> {

	public static void addSuppressWarningsProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new SuppressWarningsSubProcessor().getSuppressWarningsProposals(context, problem, proposals);
	}

	public static void addUnknownSuppressWarningProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new SuppressWarningsSubProcessor().getUnknownSuppressWarningProposals(context, problem, proposals);
	}

	public static void addRemoveUnusedSuppressWarningProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new SuppressWarningsSubProcessor().getRemoveUnusedSuppressWarningProposals(context, problem, proposals);
	}

	@Override
	protected ProposalKindWrapper createSuppressWarningsProposal(String warningToken, String label, ICompilationUnit cu, ASTNode node, ChildListPropertyDescriptor property, int relevance) {
		return CodeActionHandler.wrap(new SuppressWarningsProposalCore(warningToken, label, cu, node, property, relevance), CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper createASTRewriteCorrectionProposal(String name, ICompilationUnit cu, ASTRewrite rewrite, int relevance) {
		return CodeActionHandler.wrap(new ASTRewriteCorrectionProposalCore(name, cu, rewrite, relevance), CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper createFixCorrectionProposal(IProposableFix fix, ICleanUp cleanUp, int relevance, IInvocationContext context) {
		FixCorrectionProposalCore proposal = new FixCorrectionProposalCore(fix, cleanUp, relevance, context);
		proposal.setCommandId("org.eclipse.jdt.ui.correction.addSuppressWarnings");
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	@Override
	protected boolean alreadyHasProposal(Collection<ProposalKindWrapper> proposals, String warningToken) {
		for (ProposalKindWrapper element : proposals) {
			if (element.getProposal() instanceof SuppressWarningsProposalCore swp && warningToken.equals(swp.getWarningToken())) {
				return true; // only one at a time
			}
		}
		return false;
	}

}
