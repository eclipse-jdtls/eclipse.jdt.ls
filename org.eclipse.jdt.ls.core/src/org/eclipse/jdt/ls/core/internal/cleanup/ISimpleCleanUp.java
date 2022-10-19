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

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.manipulation.CleanUpContextCore;
import org.eclipse.jdt.core.manipulation.ICleanUpFixCore;

/**
 * Represents a cleanup change that doesn't need any further configuration (eg.
 * UI interaction), and can simply be enabled or disabled.
 */
public interface ISimpleCleanUp {

	/**
	 * Returns the unique identifier for this clean up.
	 *
	 * @return the unique identifier for this clean up
	 */
	String getIdentifier();

	/**
	 * Returns the cleanup fix for the given source file.
	 *
	 * @param context
	 *            the context for the clean up (the compilation unit and the AST)
	 * @return the cleanup fix for the given source file
	 */
	ICleanUpFixCore createFix(CleanUpContextCore context) throws CoreException;

	/**
	 * Returns a list of all compiler markers (i.e. info, warning, error) that are
	 * needed in order to perform this clean up.
	 *
	 * @return a list of all compiler markers (i.e. info, warning, error) that are
	 *         needed in order to perform this clean up
	 */
	List<String> getRequiredCompilerMarkers();

}
