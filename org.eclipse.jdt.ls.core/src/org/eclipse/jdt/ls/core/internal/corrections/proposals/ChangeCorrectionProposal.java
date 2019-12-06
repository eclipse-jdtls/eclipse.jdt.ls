/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/ui/text/java/correction/ChangeCorrectionProposal.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;


public class ChangeCorrectionProposal extends ChangeCorrectionProposalCore {
	// LSP: Code Action Kind
	private String fKind;

	/**
	 * Constructs a change correction proposal.
	 *
	 * @param name the name that is displayed in the proposal selection dialog
	 * @param change the change that is executed when the proposal is applied or <code>null</code>
	 *            if the change will be created by implementors of {@link #createChange()}
	 * @param relevance the relevance of this proposal
	 */
	public ChangeCorrectionProposal(String name, String kind, Change change, int relevance) {
		super(name, change, relevance);
		fKind = kind;
	}

	/**
	 * Performs the change associated with this proposal.
	 * <p>
	 * Subclasses may extend, but must call the super implementation.
	 *
	 * @throws CoreException
	 *             when the invocation of the change failed
	 */
	@Override
	protected void performChange() throws CoreException {

		Change change= null;
		try {
			change= getChange();
			if (change != null) {

				change.initializeValidationData(new NullProgressMonitor());
				RefactoringStatus valid= change.isValid(new NullProgressMonitor());
				if (valid.hasFatalError()) {
					IStatus status = new Status(IStatus.ERROR,  IConstants.PLUGIN_ID, IStatus.ERROR,
							valid.getMessageMatchingSeverity(RefactoringStatus.FATAL), null);
					throw new CoreException(status);
				} else {
					IUndoManager manager= RefactoringCore.getUndoManager();
					Change undoChange;
					boolean successful= false;
					try {
						manager.aboutToPerformChange(change);
						undoChange= change.perform(new NullProgressMonitor());
						successful= true;
					} finally {
						manager.changePerformed(change, successful);
					}
					if (undoChange != null) {
						undoChange.initializeValidationData(new NullProgressMonitor());
						manager.addUndo(getName(), undoChange);
					}
				}
			}
		} finally {

			if (change != null) {
				change.dispose();
			}
		}
	}

	/**
	 * Returns the kind of the proposal.
	 *
	 * @return the kind of the proposal
	 */
	public String getKind() {
		return fKind;
	}

	/**
	 * @param codeActionKind the Code Action Kind to set
	 */
	public void setKind(String codeActionKind) {
		this.fKind = codeActionKind;
	}
}
