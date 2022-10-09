/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

/**
 * @author Fred Bricon
 *
 */
public interface IBuildSupport {

	boolean applies(IProject project);

	boolean isBuildFile(IResource resource);

	default boolean isBuildLikeFileName(String fileName) {
		return false;
	}

	/**
	 *
	 * @param resource
	 *            - a project to update
	 * @param force
	 *            - defines if the <code>project</code> must be updated despite of
	 *            no changes in the build descriptor are made
	 * @param monitor
	 * @throws CoreException
	 */
	default void update(IProject resource, boolean force, IProgressMonitor monitor) throws CoreException {
		//do nothing
	}

	/**
	 * Is equal to a non-forced update: {@code update(resource, false, monitor)}
	 */
	default void update(IProject resource, IProgressMonitor monitor) throws CoreException {
		update(resource, false, monitor);
	}

	/**
	 *	Handle resource changes.
	 * @param resource
	 * 				- the resource that changed
	 * @param changeType
	 * 				- the type of change
	 * @param monitor
	 * 				- a progress monitor
	 * @return <code>true</code> if a project configuration update is recommended next
	 *
	 * @throws CoreException
	 */
	default boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor) throws CoreException {
		refresh(resource, changeType, monitor);
		return false;
	}

	/**
	 * Check if the build support for the specified project depends on the default VM.
	 */
	default boolean useDefaultVM(IProject project, IVMInstall defaultVM) {
		return false;
	}

	default void refresh(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor) throws CoreException {
		if (resource == null) {
			return;
		}
		if (changeType == CHANGE_TYPE.DELETED) {
			if (IJavaProject.CLASSPATH_FILE_NAME.equals(resource.getName())) {
				IProject project = resource.getProject();
				if (ProjectUtils.isJavaProject(project)) {
					ProjectUtils.removeJavaNatureAndBuilder(project, monitor);
					update(project, true, monitor);
				}
			}
			resource = resource.getParent();
		}
		if (resource != null) {
			resource.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		}
	}

	/**
	 * Discover the source for classFile and attach it to the project it belongs to.
	 *
	 * @param classFile
	 *            - a class file
	 * @param monitor
	 *            - a progress monitor
	 * @throws CoreException
	 */
	default void discoverSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
	}

	default ILaunchConfiguration getLaunchConfiguration(IJavaProject javaProject, String scope) throws CoreException {
		return new JavaApplicationLaunchConfiguration(javaProject.getProject(), scope, null);
	}

	default List<String> getWatchPatterns() {
		return Collections.emptyList();
	}

	/**
	 * Register the listener(s) for notification of preferences changes. The given
	 * preferenceManager argument must not be <code>null</code>.
	 *
	 * @param preferenceManager
	 *            the preferences manager
	 */
	default void registerPreferencesChangeListener(PreferenceManager preferenceManager) throws CoreException {
	}

	/**
	 * Un-register the listener(s) from receiving notification of preferences
	 * changes. The given preferenceManager argument must not be <code>null</code>.
	 *
	 * @param preferenceManager
	 *            the preferences manager
	 */
	default void unregisterPreferencesChangeListener(PreferenceManager preferenceManager) throws CoreException {
	}

	default String buildToolName() {
		return "UnknownBuildTool";
	}

	default boolean hasSpecificDeleteProjectLogic() {
		return false;
	}

	default void deleteInvalidProjects(Collection<IPath> rootPaths, ArrayList<IProject> deleteProjectCandates, IProgressMonitor monitor) {}

	default String unsupportedOperationMessage() {
		return "Unsupported operation. Please use your build tool project file to manage the source directories of the project.";
	}

	/**
	 * Returns file patterns that will not be tracked for changes.
	 * Use Glob pattern rules to create a pattern.
	 */
	default List<String> getExcludedFilePatterns() {
		return Collections.emptyList();
	}

}
