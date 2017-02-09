/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.MavenUpdateRequest;

/**
 * @author Fred Bricon
 *
 */
public class MavenBuildSupport implements IBuildSupport {

	@Override
	public boolean applies(IProject project) {
		return ProjectUtils.isMavenProject(project);
	}

	@Override
	public void update(IProject project, IProgressMonitor monitor) throws CoreException {
		if (!applies(project)) {
			return;
		}
		JavaLanguageServerPlugin.logInfo("Starting Maven update for "+project.getName());
		//TODO collect dependent projects and update them as well? i.e in case a parent project was modified
		IProjectConfigurationManager configurationManager = MavenPlugin.getProjectConfigurationManager();
		MavenUpdateRequest request = new MavenUpdateRequest(project, MavenPlugin.getMavenConfiguration().isOffline(), true);
		configurationManager.updateProjectConfiguration(request, monitor);
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		return resource != null
				&& resource.getType()== IResource.FILE
				&& resource.getName().equals("pom.xml");
	}
}
