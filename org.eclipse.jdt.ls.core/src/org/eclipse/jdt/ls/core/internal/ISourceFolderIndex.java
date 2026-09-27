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
package org.eclipse.jdt.ls.core.internal;

import java.nio.file.Path;
import java.util.List;

/**
 * Maps source folder paths to module/target IDs for on-demand import.
 *
 * <p>Each build tool (MBT, Maven, Gradle) provides its own implementation
 * that can build this index cheaply at startup without full dependency
 * resolution.</p>
 */
public interface ISourceFolderIndex {

	/**
	 * Finds the module/target ID for the given file path.
	 *
	 * @param filePath the absolute path to a source file
	 * @return the module ID, or {@code null} if the file is not under any
	 *         known source folder
	 */
	String findTarget(Path filePath);

	/**
	 * Collects the given target and all its transitive dependencies that
	 * have not yet been imported.
	 *
	 * @param targetId the starting target/module ID
	 * @return the list of target IDs to import, in dependency order;
	 *         empty if all are already imported
	 */
	List<String> collectTargetsToImport(String targetId);

	/**
	 * Collects the direct reverse dependencies of the given target that
	 * have not yet been imported.
	 *
	 * @param targetId the target whose dependents to find
	 * @return the list of not-yet-imported dependent target IDs
	 */
	List<String> collectReverseDependencies(String targetId);

	/**
	 * Returns whether the given target has already been imported.
	 */
	boolean isImported(String targetId);

	/**
	 * Marks the given targets as imported so they are not imported again.
	 */
	void markImported(List<String> targetIds);
}
