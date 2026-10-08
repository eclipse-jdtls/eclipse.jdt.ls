/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers.ondemand;

import java.nio.file.Path;

import org.eclipse.jdt.core.IClassFile;

/**
 * Strategy for locating the workspace source file corresponding to an {@link IClassFile}.
 *
 * <p>Multiple strategies are tried in cascade from fastest to slowest.
 * See {@code docs/classfile-to-project-strategies.md} for details.</p>
 */
public interface IClassFileSourceStrategy {

	/**
	 * Attempts to find the source file for the given class file.
	 *
	 * @param classFile the class file being navigated to
	 * @param relativePath the relative source path (e.g. {@code com/example/Foo.java})
	 * @param scanner the module scanner providing the list of discovered modules
	 * @return the absolute path to the source file, or {@code null} if this strategy cannot find it
	 */
	Path findSource(IClassFile classFile, String relativePath, ModuleScanner scanner);

	/**
	 * Returns the name of this strategy for logging purposes.
	 */
	String getName();
}
