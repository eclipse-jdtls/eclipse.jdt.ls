/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;

/**
 * @author Fred Bricon
 *
 */
public class InvisibleProjectBuildSupport extends EclipseBuildSupport implements IBuildSupport {

	static final String LIB_FOLDER = "lib";

	private UpdateClasspathJob updateClasspathJob;

	public InvisibleProjectBuildSupport() {
		this(UpdateClasspathJob.getInstance());
	}

	public InvisibleProjectBuildSupport(UpdateClasspathJob updateClasspathJob) {
		this.updateClasspathJob = updateClasspathJob;
	}

	@Override
	public boolean applies(IProject project) {
		return project != null && project.isAccessible() && !ProjectUtils.isVisibleProject(project);
	}

	@Override
	public boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor) throws CoreException {
		if (resource == null || !applies(resource.getProject())) {
			return false;
		}
		refresh(resource, changeType, monitor);
		IProject invisibleProject = resource.getProject();
		IPath realFolderPath = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK).getLocation();
		if (realFolderPath != null) {
			IPath libFolderPath = realFolderPath.append(LIB_FOLDER);
			if (libFolderPath.isPrefixOf(resource.getLocation())) {
				updateClasspathJob.updateClasspath(JavaCore.create(invisibleProject), libFolderPath);
			}
		}
		return false;
	}

}
