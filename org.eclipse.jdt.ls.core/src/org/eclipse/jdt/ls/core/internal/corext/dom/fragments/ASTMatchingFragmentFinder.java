/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from org.eclipse.jdt.internal.corext.dom.fragments.ASTMatchingFragmentFinder
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.dom.fragments;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.internal.corext.dom.GenericVisitor;

class ASTMatchingFragmentFinder extends GenericVisitor {

	public static IASTFragment[] findMatchingFragments(ASTNode scope, ASTFragment toMatch) {
		return new ASTMatchingFragmentFinder(toMatch).findMatches(scope);
	}

	private ASTFragment fFragmentToMatch;
	private Set<IASTFragment> fMatches = new HashSet<>();

	private ASTMatchingFragmentFinder(ASTFragment toMatch) {
		super(true);
		fFragmentToMatch = toMatch;
	}

	private IASTFragment[] findMatches(ASTNode scope) {
		fMatches.clear();
		scope.accept(this);
		return getMatches();
	}

	private IASTFragment[] getMatches() {
		return fMatches.toArray(new IASTFragment[fMatches.size()]);
	}

	@Override
	public boolean visit(Javadoc node) {
		return false;
	}

	@Override
	protected boolean visitNode(ASTNode node) {
		IASTFragment[] localMatches = fFragmentToMatch.getMatchingFragmentsWithNode(node);
		for (int i = 0; i < localMatches.length; i++) {
			fMatches.add(localMatches[i]);
		}
		return true;
	}

}
