/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Red Hat Inc. - modified for jdt.ls
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.Collection;

import org.eclipse.jdt.internal.ui.text.correction.TypeArgumentMismatchBaseSubProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;

public class TypeArgumentMismatchSubProcessor extends TypeArgumentMismatchBaseSubProcessor<ProposalKindWrapper> {

	public static void removeMismatchedArguments(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new TypeArgumentMismatchSubProcessor().addRemoveMismatchedArgumentProposals(context, problem, proposals);
	}

	private TypeArgumentMismatchSubProcessor() {
	}

	@Override
	public ProposalKindWrapper astRewriteCorrectionProposalToT(ASTRewriteCorrectionProposalCore core) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

}
