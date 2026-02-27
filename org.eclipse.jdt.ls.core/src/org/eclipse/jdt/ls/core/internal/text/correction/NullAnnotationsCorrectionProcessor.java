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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.corext.fix.NullAnnotationsRewriteOperations.ChangeKind;
import org.eclipse.jdt.internal.ui.text.correction.NullAnnotationsCorrectionProcessorCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CreatePackageInfoWithDefaultNullnessProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ExtractToNullCheckedLocalProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.MakeLocalVariableNonNullProposalCore;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.lsp4j.CodeActionKind;

/**
 * @author jjohnstn
 *
 */
public class NullAnnotationsCorrectionProcessor extends NullAnnotationsCorrectionProcessorCore<ProposalKindWrapper> {

	// pre: changeKind != OVERRIDDEN
	public static void addReturnAndArgumentTypeProposal(IInvocationContext context, IProblemLocation problem, ChangeKind changeKind, Collection<ProposalKindWrapper> proposals) {
		new NullAnnotationsCorrectionProcessor().getReturnAndArgumentTypeProposal(context, problem, changeKind, proposals);
	}

	public static void addNullAnnotationInSignatureProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals, ChangeKind changeKind, boolean isArgumentProblem) {
		new NullAnnotationsCorrectionProcessor().getNullAnnotationInSignatureProposal(context, problem, proposals, changeKind, isArgumentProblem);
	}

	public static void addRemoveRedundantAnnotationProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new NullAnnotationsCorrectionProcessor().getRemoveRedundantAnnotationProposal(context, problem, proposals);
	}

	public static void addAddMissingDefaultNullnessProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		new NullAnnotationsCorrectionProcessor().getAddMissingDefaultNullnessProposal(context, problem, proposals);
	}

	/**
	 * Fix for {@link IProblem#NullableFieldReference}
	 *
	 * @param context
	 *            context
	 * @param problem
	 *            problem to be fixed
	 * @param proposals
	 *            accumulator for computed proposals
	 */
	public static void addExtractCheckedLocalProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new NullAnnotationsCorrectionProcessor().getExtractCheckedLocalProposal(context, problem, proposals);
	}

	public static void addLocalVariableAnnotationProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new NullAnnotationsCorrectionProcessor().getLocalVariableAnnotationProposal(context, problem, proposals);
	}

	private NullAnnotationsCorrectionProcessor() {
	}
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.NullAnnotationsCorrectionProcessorCore#makeLocalVariableNonNullProposalCoreToT(org.eclipse.jdt.internal.ui.text.correction.proposals.MakeLocalVariableNonNullProposalCore)
	 */
	@Override
	protected ProposalKindWrapper makeLocalVariableNonNullProposalCoreToT(MakeLocalVariableNonNullProposalCore core) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.NullAnnotationsCorrectionProcessorCore#extractToNullCheckedLocalProposalCoreToT(org.eclipse.jdt.internal.ui.text.correction.proposals.ExtractToNullCheckedLocalProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper extractToNullCheckedLocalProposalCoreToT(ExtractToNullCheckedLocalProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.NullAnnotationsCorrectionProcessorCore#createPackageInfoWithDefaultNullnessProposalCoreToT(org.eclipse.jdt.internal.ui.text.correction.proposals.CreatePackageInfoWithDefaultNullnessProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper createPackageInfoWithDefaultNullnessProposalCoreToT(CreatePackageInfoWithDefaultNullnessProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.NullAnnotationsCorrectionProcessorCore#fixCorrectionProposalCoreToT(org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper fixCorrectionProposalCoreToT(FixCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

}
