/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import org.eclipse.jdt.core.CompletionProposal;

/**
 * Helper class for creating sort texts from relevance
 * @author Gorkem Ercan
 *
 */
public final class SortTextHelper {
	private final static char[] REVERSE_CHAR_MAP = {'j','i','h','g','f','e','d','c','b','a'};

	/**
	 * Converts the relevance to a sort text.
	 * Uses a reverse character map to convert
	 * digits to text.
	 *
	 * @param proposal
	 * @return
	 */
	public static String convertRelevance(int relevance) {
		StringBuilder sb = new StringBuilder();
		if (relevance < 1) {
			sb.append("z");
		}
		while (relevance > 0) {
			sb.insert(0,REVERSE_CHAR_MAP[relevance % 10]);
			relevance = relevance / 10;
		}
		while (sb.length() < 10) {
			sb.insert(0, "z");
		}
		return sb.toString();
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
