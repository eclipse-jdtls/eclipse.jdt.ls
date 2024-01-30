/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.correction.SerialVersionSubProcessor
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.Collection;

import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.ui.text.correction.IInvocationContextCore;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.internal.ui.text.correction.SerialVersionBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.SerialVersionProposalCore;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.lsp4j.CodeActionKind;

/**
 * Subprocessor for serial version quickfix proposals.
 *
 * @since 3.1
 */
public final class SerialVersionSubProcessor extends SerialVersionBaseSubProcessor<ProposalKindWrapper> {

	/**
	 * Determines the serial version quickfix proposals.
	 *
	 * @param context
	 *            the invocation context
	 * @param location
	 *            the problem location
	 * @param proposals
	 *            the proposal collection to extend
	 */
	public static final void getSerialVersionProposals(final IInvocationContextCore context, final IProblemLocationCore location, final Collection<ProposalKindWrapper> proposals) {
		new SerialVersionSubProcessor().addSerialVersionProposals(context, location, proposals);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.SerialVersionBaseSubProcessor#createSerialVersionProposal(org.eclipse.jdt.internal.corext.fix.IProposableFix, int, org.eclipse.jdt.internal.ui.text.correction.IInvocationContextCore, boolean)
	 */
	@Override
	protected ProposalKindWrapper createSerialVersionProposal(IProposableFix iProposableFix, int missingSerialVersion, IInvocationContextCore context, boolean b) {
		return CodeActionHandler.wrap(new SerialVersionProposalCore(iProposableFix, missingSerialVersion, context, b), CodeActionKind.QuickFix);
	}
}
