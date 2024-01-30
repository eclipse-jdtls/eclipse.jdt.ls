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
import org.eclipse.jdt.internal.ui.text.correction.IInvocationContextCore;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.internal.ui.text.correction.SuppressWarningsBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.SuppressWarningsProposalCore;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;

public class SuppressWarningsSubProcessor extends SuppressWarningsBaseSubProcessor<ProposalKindWrapper> {

	public static void addSuppressWarningsProposals(IInvocationContextCore context, IProblemLocationCore problem, Collection<ProposalKindWrapper> proposals) {
		new SuppressWarningsSubProcessor().getSuppressWarningsProposals(context, problem, proposals);
	}

	public static void addUnknownSuppressWarningProposals(IInvocationContextCore context, IProblemLocationCore problem, Collection<ProposalKindWrapper> proposals) {
		new SuppressWarningsSubProcessor().getUnknownSuppressWarningProposals(context, problem, proposals);
	}

	public static void addRemoveUnusedSuppressWarningProposals(IInvocationContextCore context, IProblemLocationCore problem, Collection<ProposalKindWrapper> proposals) {
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

}
