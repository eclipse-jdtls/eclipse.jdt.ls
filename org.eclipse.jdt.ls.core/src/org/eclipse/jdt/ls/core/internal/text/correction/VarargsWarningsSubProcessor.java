/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.Collection;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.ui.text.correction.AddSafeVarargsProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.VarargsWarningsBaseSubProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;

/**
 * @author jjohnstn
 *
 */
public class VarargsWarningsSubProcessor extends VarargsWarningsBaseSubProcessor<ProposalKindWrapper> {

	public static void addAddSafeVarargsProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new VarargsWarningsSubProcessor().createAddSafeVarargsProposals(context, problem, proposals);
	}

	public static void addAddSafeVarargsToDeclarationProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new VarargsWarningsSubProcessor().createAddSafeVarargsToDeclarationProposals(context, problem, proposals);
	}

	public static void addRemoveSafeVarargsProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new VarargsWarningsSubProcessor().createRemoveSafeVarargsProposals(context, problem, proposals);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.VarargsWarningsBaseSubProcessor#createAddSafeVarargsProposal1(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.MethodDeclaration, org.eclipse.jdt.core.dom.IMethodBinding, int)
	 */
	@Override
	protected ProposalKindWrapper createAddSafeVarargsProposal1(String label, ICompilationUnit compilationUnit, MethodDeclaration methodDeclaration, IMethodBinding methodBinding, int relevance) {
		return CodeActionHandler.wrap(new AddSafeVarargsProposalCore(label, compilationUnit, methodDeclaration, methodBinding, relevance), CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.VarargsWarningsBaseSubProcessor#createAddSafeVarargsToDeclarationProposal1(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, java.lang.Object, org.eclipse.jdt.core.dom.IMethodBinding, int)
	 */
	@Override
	protected ProposalKindWrapper createAddSafeVarargsToDeclarationProposal1(String label, ICompilationUnit targetCu, Object object, IMethodBinding methodDeclaration, int relevance) {
		return CodeActionHandler.wrap(new AddSafeVarargsProposalCore(label, targetCu, null, methodDeclaration, relevance), CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.VarargsWarningsBaseSubProcessor#createRemoveSafeVarargsProposal1(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.rewrite.ASTRewrite, int)
	 */
	@Override
	protected ProposalKindWrapper createRemoveSafeVarargsProposal1(String label, ICompilationUnit compilationUnit, ASTRewrite rewrite, int relevance) {
		return CodeActionHandler.wrap(new ASTRewriteCorrectionProposalCore(label, compilationUnit, rewrite, relevance), CodeActionKind.QuickFix);
	}

}
