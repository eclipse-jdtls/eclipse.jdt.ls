/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.Collection;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.text.correction.ReturnTypeBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.proposals.LinkedCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.MissingReturnTypeCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.MissingReturnTypeInLambdaCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ReplaceCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.TypeMismatchSubProcessor;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;


public class ReturnTypeSubProcessor extends ReturnTypeBaseSubProcessor<ProposalKindWrapper> {

	public static void replaceReturnWithYieldStatementProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ReturnTypeSubProcessor().collectReplaceReturnWithYieldStatementProposals(context, problem, proposals);
	}

	public static void addVoidMethodReturnsProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ReturnTypeSubProcessor().collectVoidMethodReturnsProposals(context, problem, proposals);
	}

	public static void addMethodReturnsVoidProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws JavaModelException {
		new ReturnTypeSubProcessor().collectMethodReturnsVoidProposals(context, problem, proposals);
	}

	public static void addMissingReturnTypeProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ReturnTypeSubProcessor().collectMissingReturnTypeProposals(context, problem, proposals);
	}

	public static void addMissingReturnStatementProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ReturnTypeSubProcessor().collectMissingReturnStatementProposals(context, problem, proposals);
	}

	public static void addMethodWithConstrNameProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ReturnTypeSubProcessor().collectMethodWithConstrNameProposals(context, problem, proposals);
	}

	@Override
	protected TypeMismatchBaseSubProcessor<ProposalKindWrapper> getTypeMismatchSubProcessor() {
		return new TypeMismatchSubProcessor();
	}

	@Override
	protected ProposalKindWrapper linkedCorrectionProposal1ToT(LinkedCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper rewriteCorrectionProposalToT(ASTRewriteCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper replaceCorrectionProposalToT(ReplaceCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper missingReturnTypeProposalToT(MissingReturnTypeCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper missingReturnTypeInLambdaProposalToT(MissingReturnTypeInLambdaCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}
}
