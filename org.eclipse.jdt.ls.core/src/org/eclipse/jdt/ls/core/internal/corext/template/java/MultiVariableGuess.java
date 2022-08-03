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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.internal.ui.text.template.contentassist.MultiVariable;

/**
 * Global state for templates. Selecting a proposal for the main template variable
 * will cause the value (and the proposals) for the dependent variables to change.
 *
 * Copied from org.eclipse.jdt.internal.ui.text.template.contentassist.MultiVariableGuess
 * UI related code is removed.
 *
 * @see MultiVariable
 */
public class MultiVariableGuess {

	private final Map<MultiVariable, Set<MultiVariable>> fDependencies= new HashMap<>();
	private final Map<MultiVariable, MultiVariable> fBackwardDeps= new HashMap<>();
	private final Map<MultiVariable, VariablePosition> fPositions= new HashMap<>();

	public MultiVariableGuess() {
	}

	/**
	 * @param position
	 */
	public void addDependent(VariablePosition position) {
		fPositions.put(position.getVariable(), position);
	}

	/**
	 * @param main
	 * @param dependent
	 * @since 3.3
	 */
	public void addDependency(MultiVariable main, MultiVariable dependent) {
		// check for cycles and multi-dependents
		if (fBackwardDeps.containsKey(dependent))
			throw new IllegalArgumentException("dependent can only have a single main variable"); //$NON-NLS-1$
		Object parent= main;
		while (parent != null) {
			parent= fBackwardDeps.get(parent);
			if (parent == dependent)
				throw new IllegalArgumentException("cycle detected"); //$NON-NLS-1$
		}

		Set<MultiVariable> dependents= fDependencies.get(main);
		if (dependents == null) {
			dependents= new HashSet<>();
			fDependencies.put(main, dependents);
		}
		fBackwardDeps.put(dependent, main);
		dependents.add(dependent);
	}
}
