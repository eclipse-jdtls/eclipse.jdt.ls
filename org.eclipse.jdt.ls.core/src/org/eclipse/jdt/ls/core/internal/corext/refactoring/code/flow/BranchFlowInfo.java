/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.code.flow.BranchFlowInfo
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.code.flow;

import java.util.HashSet;

import org.eclipse.jdt.core.dom.SimpleName;

class BranchFlowInfo extends FlowInfo {

	public BranchFlowInfo(SimpleName label, FlowContext context) {
		super(NO_RETURN);
		fBranches = new HashSet<>(2);
		fBranches.add(makeString(label));
	}
}

