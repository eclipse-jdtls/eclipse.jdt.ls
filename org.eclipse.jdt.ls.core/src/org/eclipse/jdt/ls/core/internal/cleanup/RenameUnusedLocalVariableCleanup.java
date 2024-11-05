/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.RenameUnusedVariableFixCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

/**
 * Represents a clean up that renames unused lambda parameter identifier to an
 * underscore (_)
 */
public class RenameUnusedLocalVariableCleanup implements ISimpleCleanUp {

	private static final List<String> COMPILER_OPTS = Arrays.asList(JavaCore.COMPILER_PB_UNUSED_LAMBDA_PARAMETER, JavaCore.COMPILER_PB_UNUSED_LOCAL);

	@Override
	public Collection<String> getIdentifiers() {
		return List.of("removeUnusedLambdaParameters", CleanUpConstants.REMOVE_UNUSED_CODE_LOCAL_VARIABLES);
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		return RenameUnusedVariableFixCore.createCleanUp(context.getAST(), true);
	}

	@Override
	public List<String> getRequiredCompilerMarkers() {
		return COMPILER_OPTS;
	}

}
