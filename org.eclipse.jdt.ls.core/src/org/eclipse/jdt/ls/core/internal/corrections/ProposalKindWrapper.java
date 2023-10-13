/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;

/**
 * Wrap a proposal and a String 'kind'
 */
public class ProposalKindWrapper {
	private ChangeCorrectionProposalCore proposal;
	private String kind;

	public ProposalKindWrapper(ChangeCorrectionProposalCore proposal, String kind) {
		this.proposal = proposal;
		this.kind = kind;
	}

	/**
	 * @return the proposal
	 */
	public ChangeCorrectionProposalCore getProposal() {
		return proposal;
	}

	/**
	 * @return the kind
	 */
	public String getKind() {
		return kind;
	}
}
