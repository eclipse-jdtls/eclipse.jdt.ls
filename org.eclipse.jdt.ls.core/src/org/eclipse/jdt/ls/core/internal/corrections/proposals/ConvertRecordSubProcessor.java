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
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.Collection;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.ConvertRecordBaseSubProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.lsp4j.CodeActionKind;

/**
 * @author jjohnstn
 *
 */
public class ConvertRecordSubProcessor extends ConvertRecordBaseSubProcessor<ProposalKindWrapper> {

	/**
	 * Converts a type to a record proposal.
	 *
	 * @param context
	 *            the invocation context
	 * @param proposals
	 *            the proposal collection to extend
	 */
	public static boolean getConvertToRecordProposals(final IInvocationContext context, final ASTNode node, final Collection<ProposalKindWrapper> proposals) {
		return new ConvertRecordSubProcessor().addConvertToRecordProposals(context, node, proposals);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ConvertRecordBaseSubProcessor#changeCorrectionProposalToT(org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper changeCorrectionProposalToT(ChangeCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

}
