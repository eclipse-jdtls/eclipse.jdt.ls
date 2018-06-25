/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.correction.SerialVersionSubProcessor
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpOptions;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.corext.fix.ICleanUp;
import org.eclipse.jdt.ls.core.internal.corext.fix.PotentialProgrammingProblemsCleanUp;
import org.eclipse.jdt.ls.core.internal.corext.fix.PotentialProgrammingProblemsFix;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.FixCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;

/**
 * Subprocessor for serial version quickfix proposals.
 *
 * @since 3.1
 */
public final class SerialVersionSubProcessor {

	public static final class SerialVersionProposal extends FixCorrectionProposal {
		private boolean fIsDefaultProposal;

		public SerialVersionProposal(IProposableFix fix, int relevance, IInvocationContext context, boolean isDefault) {
			super(fix, createCleanUp(isDefault), relevance, context);
			fIsDefaultProposal = isDefault;
		}

		public boolean isDefaultProposal() {
			return fIsDefaultProposal;
		}

		private static ICleanUp createCleanUp(boolean isDefault) {
			Map<String, String> options = new Hashtable<>();
			options.put(CleanUpConstants.ADD_MISSING_SERIAL_VERSION_ID, CleanUpOptions.TRUE);
			if (isDefault) {
				options.put(CleanUpConstants.ADD_MISSING_SERIAL_VERSION_ID_DEFAULT, CleanUpOptions.TRUE);
			} else {
				options.put(CleanUpConstants.ADD_MISSING_SERIAL_VERSION_ID_GENERATED, CleanUpOptions.TRUE);
			}
			return new PotentialProgrammingProblemsCleanUp(options);
		}

		@Override
		public String getAdditionalProposalInfo(IProgressMonitor monitor) {
			if (fIsDefaultProposal) {
				return CorrectionMessages.SerialVersionDefaultProposal_message_default_info;
			} else {
				return CorrectionMessages.SerialVersionHashProposal_message_generated_info;
			}
		}
	}

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
	public static final void getSerialVersionProposals(final IInvocationContext context, final IProblemLocationCore location, final Collection<CUCorrectionProposal> proposals) {

		Assert.isNotNull(context);
		Assert.isNotNull(location);
		Assert.isNotNull(proposals);

		IProposableFix[] fixes = PotentialProgrammingProblemsFix.createMissingSerialVersionFixes(context.getASTRoot(), location);
		if (fixes != null) {
			proposals.add(new SerialVersionProposal(fixes[0], IProposalRelevance.MISSING_SERIAL_VERSION_DEFAULT, context, true));
			ICompilationUnit unit = context.getCompilationUnit();
			if (unit != null && unit.getJavaProject() != null && !ProjectsManager.DEFAULT_PROJECT_NAME.equals(unit.getJavaProject().getProject().getName())) {
				proposals.add(new SerialVersionProposal(fixes[1], IProposalRelevance.MISSING_SERIAL_VERSION, context, false));
			}
		}
	}
}
