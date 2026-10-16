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
import org.eclipse.jdt.ls.core.internal.mbt.MbtInfo;

public interface IProjectImporter {

	void initialize(File rootFolder);

	boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	default boolean applies(Collection<IPath> projectConfigurations, IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		return applies(monitor);
	}

	default boolean isResolved(File folder) throws OperationCanceledException, CoreException {
		return false;
	};

	void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	void reset();

	default boolean supportsOnDemand() {
		return false;
	}

	/**
	 * Import the module containing the given file URI on-demand.
	 * Called from {@code DocumentLifeCycleHandler} on {@code didOpen}.
	 *
	 * @param uri the file URI that triggered the import
	 * @param monitor progress monitor
	 * @return the imported projects
	 */
	default List<IProject> importOnDemand(String uri, IProgressMonitor monitor) throws CoreException {
		return List.of();
	}

	/**
	 * Import reverse dependencies of the module containing the given file URI.
	 * Called from {@code ReferencesHandler} and {@code RenameHandler}.
	 *
	 * @param uri the file URI whose reverse deps to import
	 * @param forModification {@code true} when the caller needs to modify
	 *        source files (e.g. Rename) — forces full IProject creation;
	 *        {@code false} for read-only operations (e.g. Find References) —
	 *        may use lightweight CPE_LIBRARY entries instead
	 * @param monitor progress monitor
	 * @return {@code true} if any new projects were imported
	 */
	default boolean importReverseDependencies(String uri, boolean forModification, IProgressMonitor monitor) throws CoreException {
		return false;
	}

	/**
	 * Called when the background mbt.json generation completes.
	 * Importers can override to switch from per-module mode to
	 * full mbt.json mode.
	 */
	default void onMbtJsonReady(MbtInfo mbtInfo) {
	}
}
