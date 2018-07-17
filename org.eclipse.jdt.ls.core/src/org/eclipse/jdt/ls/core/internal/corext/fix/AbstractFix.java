/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/corext/fix/AbstractFix.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.fix;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;


public abstract class AbstractFix implements IProposableFix {

	private final String fDisplayString;

	protected AbstractFix(String displayString) {
		fDisplayString= displayString;
	}

	@Override
	public String getAdditionalProposalInfo() {
		return null;
	}

	@Override
	public String getDisplayString() {
		return fDisplayString;
	}

	public LinkedProposalModelCore getLinkedPositions() {
		return null;
	}

	@Override
	public IStatus getStatus() {
		return null;
	}
}
