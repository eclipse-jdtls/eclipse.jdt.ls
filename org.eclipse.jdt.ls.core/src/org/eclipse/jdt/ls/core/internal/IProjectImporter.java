/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

public interface IProjectImporter {

	void initialize(File rootFolder);

	boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	/**
	 * Check whether the importer applies to the given project configurations.
	 * @param projectConfigurations Collection of the project configurations.
	 * @param monitor progress monitor.
	 * @throws CoreException
	 * @throws OperationCanceledException
	 */
	default boolean applies(Collection<IPath> projectConfigurations, IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		return applies(monitor);
	}

	default boolean isResolved(File folder) throws OperationCanceledException, CoreException {
		return false;
	};

	void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	void reset();

	// ── On-demand mode ──────────────────────────────────────────────────

	/**
	 * Returns whether this importer supports on-demand project import.
	 *
	 * <p>When {@code true} and the user has configured
	 * {@link org.eclipse.jdt.ls.core.internal.preferences.ImportMode#ON_DEMAND},
	 * the importer will skip eager import at startup and instead import
	 * modules lazily when files are opened.</p>
	 *
	 * @return {@code true} if on-demand import is supported
	 */
	default boolean supportsOnDemand() {
		return false;
	}

	/**
	 * Imports the module containing the given file URI on-demand.
	 *
	 * <p>Called from {@link org.eclipse.jdt.ls.core.internal.managers.ondemand.OnDemandImportManager}
	 * when a {@code textDocument/didOpen} notification is received for a file
	 * that is not yet part of any imported project.</p>
	 *
	 * @param uri the file URI that triggered the import
	 * @param monitor progress monitor
	 * @return the list of imported projects (empty if nothing was imported)
	 * @throws CoreException if the import fails
	 */
	default List<IProject> importOnDemand(String uri, IProgressMonitor monitor) throws CoreException {
		return List.of();
	}
}
