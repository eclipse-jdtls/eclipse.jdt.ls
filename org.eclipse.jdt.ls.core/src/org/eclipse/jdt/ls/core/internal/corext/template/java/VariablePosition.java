/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.template.java;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.internal.ui.text.template.contentassist.MultiVariable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.LinkedPositionGroup;

/**
 * Copied from org.eclipse.jdt.internal.ui.text.template.contentassist.VariablePosition
 * UI related code is removed.
 */
public class VariablePosition extends LinkedPosition {

	private MultiVariableGuess fGuess;
	private MultiVariable fVariable;

	public VariablePosition(IDocument document, int offset, int length, MultiVariableGuess guess, MultiVariable variable) {
		this(document, offset, length, LinkedPositionGroup.NO_STOP, guess, variable);
	}

	public VariablePosition(IDocument document, int offset, int length, int sequence, MultiVariableGuess guess, MultiVariable variable) {
		super(document, offset, length, sequence);
		Assert.isNotNull(guess);
		Assert.isNotNull(variable);
		fVariable = variable;
		fGuess = guess;
	}


	/*
	 * @see org.eclipse.jface.text.link.ProposalPosition#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object o) {
		if (o instanceof VariablePosition && super.equals(o)) {
			return fGuess.equals(((VariablePosition) o).fGuess);
		}
		return false;
	}

	/*
	 * @see org.eclipse.jface.text.link.ProposalPosition#hashCode()
	 */
	@Override
	public int hashCode() {
		return super.hashCode() | fGuess.hashCode();
	}

	/**
	 * Returns the variable.
	 *
	 * @return the variable.
	 */
	public MultiVariable getVariable() {
		return fVariable;
	}
}
