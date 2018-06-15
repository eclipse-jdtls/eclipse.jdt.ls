/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import org.eclipse.jdt.core.CompletionProposal;

/**
 * Helper class for creating sort texts from relevance
 *
 * @author Gorkem Ercan
 *
 */
public final class SortTextHelper {
	public static final int CEILING = 999_999_999;

	public static final int MAX_RELEVANCE_VALUE = 99_999_999;

	private SortTextHelper(){
		//No public instantiation
	}

	/**
	 * Converts the relevance to a 9-digit sort text, so that
	 * higher relevance would get a lower sort text.
	 *
	 * @param relevance, must be lower than 100,000,000
	 * @return a 9-digit sort text
	 * @throws IllegalArgumentException when relevance is greater or equal to 100,000,000")
	 */
	public static String convertRelevance(int relevance) {
		if (relevance > MAX_RELEVANCE_VALUE) {
			throw new IllegalArgumentException("Relevance must be lower than 100,000,000");
		}
		return String.valueOf(CEILING-Math.max(relevance, 0));
	}

	/**
	 * Computes the relevance for a given <code>CompletionProposal</code>.
	 *
	 * @param proposal the proposal to compute the relevance for
	 * @return the relevance for <code>proposal</code>
	 */
	public static String computeSortText(CompletionProposal proposal) {
		final int baseRelevance= proposal.getRelevance() * 16;
		switch (proposal.getKind()) {
		case CompletionProposal.LABEL_REF:
			return convertRelevance( baseRelevance + 1);
		case CompletionProposal.KEYWORD:
			return convertRelevance(baseRelevance + 2);
		case CompletionProposal.TYPE_REF:
		case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
		case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
			return convertRelevance(baseRelevance + 3);
		case CompletionProposal.METHOD_REF:
		case CompletionProposal.CONSTRUCTOR_INVOCATION:
		case CompletionProposal.METHOD_NAME_REFERENCE:
		case CompletionProposal.METHOD_DECLARATION:
		case CompletionProposal.ANNOTATION_ATTRIBUTE_REF:
		case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
			return convertRelevance(baseRelevance + 4);
		case CompletionProposal.FIELD_REF:
			return convertRelevance(baseRelevance + 5);
		case CompletionProposal.LOCAL_VARIABLE_REF:
		case CompletionProposal.VARIABLE_DECLARATION:
			return convertRelevance(baseRelevance + 6);
		case CompletionProposal.PACKAGE_REF://intentional fall-through
		default:
			return convertRelevance(baseRelevance);
		}
	}
}
