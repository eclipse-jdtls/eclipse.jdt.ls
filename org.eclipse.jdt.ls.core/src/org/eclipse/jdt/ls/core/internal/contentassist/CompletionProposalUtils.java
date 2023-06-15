/*******************************************************************************
 * Copyright (c) 2023 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.Arrays;

import org.eclipse.jdt.core.CompletionProposal;

public class CompletionProposalUtils {

	private static final char SEMICOLON = ';';

	private CompletionProposalUtils() {}

	public static boolean isImportCompletion(CompletionProposal proposal) {
		char[] completion = proposal.getCompletion();
		if (completion.length == 0) {
			return false;
		}

		char last = completion[completion.length - 1];
		/*
		 * Proposals end in a semicolon when completing types in normal imports
		 * or when completing static members, in a period when completing types
		 * in static imports.
		 */
		return last == SEMICOLON || last == '.';
	}

	/**
	 * Get required type completion proposal when the given proposal is a
	 * constructor. <code>null</code> will returned if the given proposal is
	 * not a constructor or no type completion proposal is available from the
	 * required proposals.
	 */
	public static CompletionProposal getRequiredTypeProposal(CompletionProposal proposal) {
		if (proposal.getKind() != CompletionProposal.CONSTRUCTOR_INVOCATION
				&& proposal.getKind() != CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION
				&& proposal.getKind() != CompletionProposal.ANONYMOUS_CLASS_DECLARATION) {
			return null;
		}

		CompletionProposal requiredProposal = null;
		CompletionProposal[] requiredProposals = proposal.getRequiredProposals();
		if (requiredProposals != null) {
			requiredProposal = Arrays.stream(requiredProposals)
				.filter(p -> p.getKind() == CompletionProposal.TYPE_REF)
				.findFirst()
				.orElse(null);
		}

		return requiredProposal;
	}
}
