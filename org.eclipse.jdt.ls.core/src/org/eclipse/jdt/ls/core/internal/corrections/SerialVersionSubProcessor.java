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

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.PotentialProgrammingProblemsFixCore;
import org.eclipse.jdt.internal.ui.text.correction.IInvocationContextCore;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.SerialVersionProposalCore;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.CodeActionKind;

/**
 * Subprocessor for serial version quickfix proposals.
 *
 * @since 3.1
 */
public final class SerialVersionSubProcessor {

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

		Assert.isNotNull(context);
		Assert.isNotNull(location);
		Assert.isNotNull(proposals);

		IProposableFix[] fixes = PotentialProgrammingProblemsFixCore.createMissingSerialVersionFixes(context.getASTRoot(), location);
		if (fixes != null) {
			proposals.add(CodeActionHandler.wrap(new SerialVersionProposalCore(fixes[0], IProposalRelevance.MISSING_SERIAL_VERSION_DEFAULT, context, true), CodeActionKind.QuickFix));
			ICompilationUnit unit = context.getCompilationUnit();
			if (unit != null && unit.getJavaProject() != null && !ProjectsManager.DEFAULT_PROJECT_NAME.equals(unit.getJavaProject().getProject().getName())) {
				proposals.add(CodeActionHandler.wrap(new SerialVersionProposalCore(fixes[1], IProposalRelevance.MISSING_SERIAL_VERSION, context, false), CodeActionKind.QuickFix));
			}
		}
	}
}
