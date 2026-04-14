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

import org.eclipse.jdt.internal.ui.text.correction.TypeAnnotationBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.lsp4j.CodeActionKind;

/**
 * @author jjohnstn
 *
 */
public class TypeAnnotationSubProcessor extends TypeAnnotationBaseSubProcessor<ProposalKindWrapper> {

	public static void addMoveTypeAnnotationToTypeProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new TypeAnnotationSubProcessor().getMoveTypeAnnotationToTypeProposal(context, problem, proposals);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeAnnotationBaseSubProcessor#fixCorrectionProposalCoreToT(org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper fixCorrectionProposalCoreToT(FixCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

}
