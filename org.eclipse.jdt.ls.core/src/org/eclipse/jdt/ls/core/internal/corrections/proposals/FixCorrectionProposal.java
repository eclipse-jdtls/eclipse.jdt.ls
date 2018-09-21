/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposal
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.ls.core.internal.corext.fix.ICleanUp;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;

/**
 * A correction proposal which uses an {@link ICleanUpFix} to fix a problem. A
 * fix correction proposal may have an {@link ICleanUp} attached which can be
 * executed instead of the provided IFix.
 */
public class FixCorrectionProposal extends LinkedCorrectionProposal {

	private final IProposableFix fFix;
	private final ICleanUp fCleanUp;
	private CompilationUnit fCompilationUnit;

	public FixCorrectionProposal(IProposableFix fix, ICleanUp cleanUp, int relevance, IInvocationContext context) {
		super(fix.getDisplayString(), CodeActionKind.QuickFix, context.getCompilationUnit(), null, relevance);
		fFix = fix;
		fCleanUp = cleanUp;
		fCompilationUnit = context.getASTRoot();
	}

	public IStatus getFixStatus() {
		return fFix.getStatus();
	}

	public ICleanUp getCleanUp() {
		return fCleanUp;
	}

	@Override
	public String getAdditionalProposalInfo(IProgressMonitor monitor) throws CoreException {
		StringBuffer result = new StringBuffer();

		IStatus status = getFixStatus();
		if (status != null && !status.isOK()) {
			result.append("<b>"); //$NON-NLS-1$
			if (status.getSeverity() == IStatus.WARNING) {
				result.append(CorrectionMessages.FixCorrectionProposal_WarningAdditionalProposalInfo);
			} else if (status.getSeverity() == IStatus.ERROR) {
				result.append(CorrectionMessages.FixCorrectionProposal_ErrorAdditionalProposalInfo);
			}
			result.append("</b>"); //$NON-NLS-1$
			result.append(status.getMessage());
			result.append("<br><br>"); //$NON-NLS-1$
		}

		String info = fFix.getAdditionalProposalInfo();
		if (info != null) {
			result.append(info);
		} else {
			result.append(super.getAdditionalProposalInfo(monitor));
		}

		return result.toString();
	}

	@Override
	public int getRelevance() {
		IStatus status = getFixStatus();
		if (status != null && !status.isOK()) {
			return super.getRelevance() - 100;
		} else {
			return super.getRelevance();
		}
	}

	@Override
	protected TextChange createTextChange() throws CoreException {
		CompilationUnitChange createChange = fFix.createChange(null);
		createChange.setSaveMode(TextFileChange.LEAVE_DIRTY);

		//			if (fFix instanceof ILinkedFix) {
		//				setLinkedProposalModel(((ILinkedFix) fFix).getLinkedPositions());
		//			}

		return createChange;
	}

}
