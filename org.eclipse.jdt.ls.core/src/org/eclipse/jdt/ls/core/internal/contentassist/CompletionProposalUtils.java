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
import java.util.List;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

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

	public static void addStaticImportsAsFavoriteImports(ICompilationUnit unit) {
		try {
			List<String> staticImports = Arrays.stream(unit.getImports())
				.filter(t -> {
					try {
						return Flags.isStatic(t.getFlags());
					} catch (JavaModelException e) {
						// ignore
					}
					return false;})
				.map(t -> t.getElementName())
				.map(t -> {
					int lastDot = t.lastIndexOf(".");
					if (lastDot > -1) {
						return t.substring(0, lastDot) + ".*";
					}
					return t;
				}).toList();
			if (!staticImports.isEmpty()) {
				Preferences.DISCOVERED_STATIC_IMPORTS.addAll(staticImports);
			}
		} catch (JavaModelException e) {
			// ignore
		}
	}
}
