/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.correction.ModifierCorrectionSubProcessor
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla <bmuskalla@innoopract.com> - [quick fix] 'Remove invalid modifiers' does not appear for enums and annotations - https://bugs.eclipse.org/bugs/show_bug.cgi?id=110589
 *     Benjamin Muskalla <b.muskalla@gmx.net> - [quick fix] Quick fix for missing synchronized modifier - https://bugs.eclipse.org/bugs/show_bug.cgi?id=245250
 *     Rabea Gransberger <rgransberger@gmx.de> - [quick fix] Fix several visibility issues - https://bugs.eclipse.org/394692
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.ui.text.correction.CorrectionMessages;
import org.eclipse.jdt.internal.ui.text.correction.ModifierCorrectionSubProcessorCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ModifierChangeCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.UnresolvedElementsSubProcessor;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;

public class ModifierCorrectionSubProcessor extends ModifierCorrectionSubProcessorCore<ProposalKindWrapper> {

	public static void addNonAccessibleReferenceProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals, int kind, int relevance) throws CoreException {
		new ModifierCorrectionSubProcessor().getNonAccessibleReferenceProposal(context, problem, proposals, kind, relevance);
	}

	public static void addChangeOverriddenModifierProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals, int kind) throws JavaModelException {
		new ModifierCorrectionSubProcessor().getChangeOverriddenModifierProposal(context, problem, proposals, kind);
	}

	public static void addNonFinalLocalProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ModifierCorrectionSubProcessor().getNonFinalLocalProposal(context, problem, proposals);
	}

	public static void addRemoveInvalidModifiersProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals, int relevance) {
		new ModifierCorrectionSubProcessor().getRemoveInvalidModifiersProposal(context, problem, proposals, relevance);
	}

	public static void addMethodRequiresBodyProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ModifierCorrectionSubProcessor().getMethodRequiresBodyProposals(context, problem, proposals);
	}

	public static void addAbstractMethodProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ModifierCorrectionSubProcessor().getAbstractMethodProposals(context, problem, proposals);
	}

	public static void addAbstractTypeProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ModifierCorrectionSubProcessor().getAbstractTypeProposals(context, problem, proposals);
	}

	public static void addNativeMethodProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ModifierCorrectionSubProcessor().getNativeMethodProposals(context, problem, proposals);
	}

	public static void addNeedToEmulateProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ModifierCorrectionSubProcessor().getNeedToEmulateProposal(context, problem, null);
	}

	public static void addOverridingDeprecatedMethodProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ModifierCorrectionSubProcessor().getOverridingDeprecatedMethodProposal(context, problem, proposals);
	}

	public static void addSynchronizedMethodProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		addAddMethodModifierProposal(context, problem, proposals, Modifier.SYNCHRONIZED, CorrectionMessages.ModifierCorrectionSubProcessor_addsynchronized_description);
	}

	public static void addStaticMethodProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		addAddMethodModifierProposal(context, problem, proposals, Modifier.STATIC, CorrectionMessages.ModifierCorrectionSubProcessor_addstatic_description);
	}

	private static void addAddMethodModifierProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals, int modifier, String label) {
		new ModifierCorrectionSubProcessor().getAddMethodModifierProposal(context, problem, proposals, modifier, label);
	}

	public static void addSealedMissingModifierProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new ModifierCorrectionSubProcessor().getSealedMissingModifierProposal(context, problem, proposals);
	}

	@Override
	protected ProposalKindWrapper astRewriteCorrectionProposalToT(ASTRewriteCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper modifierChangeCorrectionProposalCoreToT(ModifierChangeCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper fixCorrectionProposalCoreToT(FixCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected void collectConstructorProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		try {
			new UnresolvedElementsSubProcessor().collectConstructorProposals(context, problem, proposals);
		} catch (CoreException e) {
		}
	}

	@Override
	protected void getVariableProposals(IInvocationContext context, IProblemLocation problem, IVariableBinding bindingDecl, Collection<ProposalKindWrapper> proposals) {
		try {
			new UnresolvedElementsSubProcessor().collectVariableProposals(context, problem, bindingDecl, proposals);
		} catch (CoreException e) {
		}
	}

}
