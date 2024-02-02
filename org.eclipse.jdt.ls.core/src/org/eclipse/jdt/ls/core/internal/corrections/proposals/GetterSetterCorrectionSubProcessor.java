/*******************************************************************************
 * Copyright (c) 2007, 2016 IBM Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.refactoring.sef.SelfEncapsulateFieldRefactoring;
import org.eclipse.jdt.internal.ui.text.correction.GetterSetterCorrectionBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.IInvocationContextCore;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.ltk.core.refactoring.Change;

public class GetterSetterCorrectionSubProcessor extends GetterSetterCorrectionBaseSubProcessor<ProposalKindWrapper> {

	public static class SelfEncapsulateFieldProposal extends SelfEncapsulateFieldProposalCore { // public for tests

		public SelfEncapsulateFieldProposal(int relevance, IField field) {
			super(relevance, getRefactoringChange(field), field);
		}

		public static Change getRefactoringChange(IField field) {
			Preferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
			try {
				SelfEncapsulateFieldRefactoring refactoring = SelfEncapsulateFieldProposalCore.getChangeRefactoring(field);
				refactoring.setGenerateJavadoc(preferences.isCodeGenerationTemplateGenerateComments());
				return refactoring.createChange(new NullProgressMonitor());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
			return null;
		}
	}

	/**
	 * Used by quick assist
	 *
	 * @param context
	 *            the invocation context
	 * @param coveringNode
	 *            the covering node
	 * @param locations
	 *            the problems at the corrent location
	 * @param resultingCollections
	 *            the resulting proposals
	 * @return <code>true</code> if the quick assist is applicable at this offset
	 */
	public static boolean addGetterSetterProposal(IInvocationContextCore context, ASTNode coveringNode, IProblemLocationCore[] locations, ArrayList<ProposalKindWrapper> resultingCollections) {
		return new GetterSetterCorrectionSubProcessor().addGetterSetterProposals(context, coveringNode, locations, resultingCollections);
	}

	public static void addGetterSetterProposal(IInvocationContextCore context, IProblemLocationCore location, Collection<ProposalKindWrapper> proposals, int relevance) {
		new GetterSetterCorrectionSubProcessor().addGetterSetterProposals(context, location.getCoveringNode(context.getASTRoot()), proposals, relevance);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.GetterSetterCorrectionBaseSubProcessor#createNonNullMethodGetterProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.rewrite.ASTRewrite, int)
	 */
	@Override
	protected ProposalKindWrapper createNonNullMethodGetterProposal(String label, ICompilationUnit compilationUnit, ASTRewrite astRewrite, int relevance) {
		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, compilationUnit, astRewrite, relevance);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.GetterSetterCorrectionBaseSubProcessor#createFieldGetterProposal(int, org.eclipse.jdt.core.IField)
	 */
	@Override
	protected ProposalKindWrapper createFieldGetterProposal(int relevance, IField field) {
		return CodeActionHandler.wrap(new SelfEncapsulateFieldProposal(relevance, field), CodeActionKind.Refactor);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.GetterSetterCorrectionBaseSubProcessor#createMethodSetterProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.rewrite.ASTRewrite, int)
	 */
	@Override
	protected ProposalKindWrapper createMethodSetterProposal(String label, ICompilationUnit compilationUnit, ASTRewrite astRewrite, int relevance) {
		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, compilationUnit, astRewrite, relevance);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.GetterSetterCorrectionBaseSubProcessor#createFieldSetterProposal(int, org.eclipse.jdt.core.IField)
	 */
	@Override
	protected ProposalKindWrapper createFieldSetterProposal(int relevance, IField field) {
		return CodeActionHandler.wrap(new SelfEncapsulateFieldProposal(relevance, field), CodeActionKind.Refactor);
	}

}
