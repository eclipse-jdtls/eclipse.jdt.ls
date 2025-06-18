/*******************************************************************************
* Copyright (c) 2025 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.cleanup;

import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

public class RedundantComparisonStatementCleanUp implements ISimpleCleanUp {

	@Override
	public Collection<String> getIdentifiers() {
		return List.of("redundantComparisonStatement", CleanUpConstants.REMOVE_REDUNDANT_COMPARISON_STATEMENT);
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		Map<String, String> options = new Hashtable<>();
		options.put(CleanUpConstants.REMOVE_REDUNDANT_COMPARISON_STATEMENT, CleanUpOptions.TRUE);
		org.eclipse.jdt.internal.ui.fix.RedundantComparisonStatementCleanUp cleanup = new org.eclipse.jdt.internal.ui.fix.RedundantComparisonStatementCleanUp(options);
		return cleanup.createFix(context);
	}

	@Override
	public List<String> getRequiredCompilerMarkers() {
		return Collections.emptyList();
	}

}
