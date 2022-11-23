/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.cleanup;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CleanUpContextCore;
import org.eclipse.jdt.core.manipulation.ICleanUpFixCore;
import org.eclipse.jdt.internal.corext.fix.SwitchExpressionsFixCore;

/**
 * Represents a cleanup that converts a switch statement to a switch expression
 */
public class SwitchExpressionCleanup implements ISimpleCleanUp {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.cleanup.ISimpleCleanUp#getIdentifier()
	 */
	@Override
	public String getIdentifier() {
		return "switchExpression";
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.cleanup.ISimpleCleanUp#createFix(org.eclipse.jdt.core.manipulation.CleanUpContextCore)
	 */
	@Override
	public ICleanUpFixCore createFix(CleanUpContextCore context) throws CoreException {
		CompilationUnit unit = context.getAST();
		if (unit == null) {
			return null;
		}
		return SwitchExpressionsFixCore.createCleanUp(unit);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.cleanup.ISimpleCleanUp#getRequiredCompilerMarkers()
	 */
	@Override
	public List<String> getRequiredCompilerMarkers() {
		return Collections.emptyList();
	}

}
