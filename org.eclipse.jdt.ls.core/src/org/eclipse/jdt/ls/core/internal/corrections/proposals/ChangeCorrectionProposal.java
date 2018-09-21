/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/ui/text/java/correction/ChangeCorrectionProposal.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;


public class ChangeCorrectionProposal {
	private Change fChange;
	private String fName;
	private String fKind;
	private int fRelevance;

	/**
	 * Constructs a change correction proposal.
	 *
	 * @param name the name that is displayed in the proposal selection dialog
	 * @param change the change that is executed when the proposal is applied or <code>null</code>
	 *            if the change will be created by implementors of {@link #createChange()}
	 * @param relevance the relevance of this proposal
	 */
	public ChangeCorrectionProposal(String name, String kind, Change change, int relevance) {
		if (name == null) {
			throw new IllegalArgumentException("Name must not be null"); //$NON-NLS-1$
		}
		fName= name;
		fKind = kind;
		fChange= change;
		fRelevance= relevance;
	}

	public void apply() throws CoreException {
		performChange();
	}

	/**
	 * Performs the change associated with this proposal.
	 * <p>
	 * Subclasses may extend, but must call the super implementation.
	 *
	 * @throws CoreException
	 *             when the invocation of the change failed
	 */
	protected void performChange() throws CoreException {

		Change change= null;
		try {
			change= getChange();
			if (change != null) {

				change.initializeValidationData(new NullProgressMonitor());
				RefactoringStatus valid= change.isValid(new NullProgressMonitor());
				if (valid.hasFatalError()) {
					IStatus status = new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, IStatus.ERROR,
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

	public String getAdditionalProposalInfo(IProgressMonitor monitor) throws CoreException {
		StringBuffer buf= new StringBuffer();
		buf.append("<p>"); //$NON-NLS-1$
		try {
			Change change= getChange();
			if (change != null) {
				String name= change.getName();
				if (name.length() == 0) {
					return null;
				}
				buf.append(name);
			} else {
				return null;
			}
		} catch (CoreException e) {
			buf.append("Unexpected error when accessing this proposal:<p><pre>"); //$NON-NLS-1$
			buf.append(e.getLocalizedMessage());
			buf.append("</pre>"); //$NON-NLS-1$
		}
		buf.append("</p>"); //$NON-NLS-1$
		return buf.toString();
	}

	/**
	 * Returns the name of the proposal.
	 *
	 * @return the name of the proposal
	 */
	public String getName() {
		return fName;
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
	 * Returns the change that will be executed when the proposal is applied.
	 * This method calls {@link #createChange()} to compute the change.
	 *
	 * @return the change for this proposal, can be <code>null</code> in rare cases if creation of
	 *         the change failed
	 * @throws CoreException when the change could not be created
	 */
	public final Change getChange() throws CoreException {
		synchronized (this) {
			if (fChange == null) {
				fChange = createChange();
			}
		}
		return fChange;
	}

	/**
	 * Creates the change for this proposal.
	 * This method is only called once and only when no change has been passed in
	 * {@link #ChangeCorrectionProposal(String, Change, int, Image)}.
	 *
	 * Subclasses may override.
	 *
	 * @return the created change
	 * @throws CoreException if the creation of the change failed
	 */
	protected Change createChange() throws CoreException {
		return new NullChange();
	}

	/**
	 * Sets the display name.
	 *
	 * @param name the name to set
	 */
	public void setDisplayName(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Name must not be null"); //$NON-NLS-1$
		}
		fName= name;
	}

	public int getRelevance() {
		return fRelevance;
	}

	/**
	 * Sets the relevance.
	 *
	 * @param relevance the relevance to set
	 *
	 * @see #getRelevance()
	 */
	public void setRelevance(int relevance) {
		fRelevance= relevance;
	}

}
