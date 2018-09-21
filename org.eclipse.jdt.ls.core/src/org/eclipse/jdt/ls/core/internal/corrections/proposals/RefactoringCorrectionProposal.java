/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Originally copied from org.eclipse.jdt.internal.corrections.proposals.RefactoringCorrectionProposal;
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;

public class RefactoringCorrectionProposal extends LinkedCorrectionProposal {
	private final Refactoring fRefactoring;
	private RefactoringStatus fRefactoringStatus;

	public RefactoringCorrectionProposal(String name, String kind, ICompilationUnit cu, Refactoring refactoring, int relevance) {
		super(name, kind, cu, null, relevance);
		fRefactoring = refactoring;
	}

	/**
	 * Can be overridden by clients to perform expensive initializations of the
	 * refactoring
	 *
	 * @param refactoring
	 *            the refactoring
	 * @throws CoreException
	 *             if something goes wrong during init
	 */
	protected void init(Refactoring refactoring) throws CoreException {
		// empty default implementation
	}

	@Override
	protected TextChange createTextChange() throws CoreException {
		init(fRefactoring);
		fRefactoringStatus = fRefactoring.checkFinalConditions(new NullProgressMonitor());
		if (fRefactoringStatus.hasFatalError()) {
			TextFileChange dummyChange = new TextFileChange("fatal error", (IFile) getCompilationUnit().getResource()); //$NON-NLS-1$
			dummyChange.setEdit(new InsertEdit(0, "")); //$NON-NLS-1$
			return dummyChange;
		}
		return (TextChange) fRefactoring.createChange(new NullProgressMonitor());
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.correction.proposals.CUCorrectionProposal#getAdditionalProposalInfo(org.eclipse.core.runtime.IProgressMonitor)
	 * @since 3.6
	 */
	@Override
	public String getAdditionalProposalInfo(IProgressMonitor monitor) throws CoreException {
		if (fRefactoringStatus != null && fRefactoringStatus.hasFatalError()) {
			return fRefactoringStatus.getEntryWithHighestSeverity().getMessage();
		}
		return super.getAdditionalProposalInfo(monitor);
	}
}