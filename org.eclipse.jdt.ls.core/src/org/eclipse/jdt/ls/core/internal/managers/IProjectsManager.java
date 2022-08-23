/*******************************************************************************
* Copyright (c) 2020-2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.managers;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.lsp4j.FileSystemWatcher;

public interface IProjectsManager {
	/**
	 * Intialize the workspace and import the projects under the rootPaths.
	 */
	void initializeProjects(final Collection<IPath> rootPaths, IProgressMonitor monitor) throws CoreException;

	/**
	 * Clean up the projects not belonged to the give rootPaths.
	 */
	void cleanInvalidProjects(final Collection<IPath> rootPaths, IProgressMonitor monitor);

	/**
	 * Update the workspace and projects according to the changed workspace folders.
	 */
	Job updateWorkspaceFolders(Collection<IPath> addedRootPaths, Collection<IPath> removedRootPaths);

	/**
	 * Update the project configuration.
	 */
	Job updateProject(IProject project, boolean force);

	/**
	 * Check whether the resource is a build file.
	 */
	boolean isBuildFile(IResource resource);

	/**
	 * Check whether the file name is like a build file.
	 */
	boolean isBuildLikeFileName(String fileName);

	/**
	 * Get the build support provided by the given project.
	 */
	Optional<IBuildSupport> getBuildSupport(IProject project);

	/**
	 * Update the watcher patterns.
	 * @param runInJob - whether to run the action in a job
	 */
	void registerWatchers(boolean runInJob);

	/**
	 * Update the watcher patterns.
	 */
	List<FileSystemWatcher> registerWatchers();

	/**
	 * Register listeners.
	 */
	default void registerListeners() {
		// do nothing
	};

	/**
	 * Handle the file change event.
	 */
	void fileChanged(String uriString, CHANGE_TYPE changeType);

	/**
	 * Unregister listeners.
	 */
	default void unregisterListeners() {
		// do nothing
	};

	/**
	 * Executed after the projects are imported.
	 */
	default void projectsImported(IProgressMonitor monitor) {
		// do nothing
	}
}
