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

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.workspace.GradleBuild;
import org.eclipse.buildship.core.workspace.NewProjectHandler;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

import com.google.common.base.Optional;

/**
 * @author Fred Bricon
 *
 */
public class GradleBuildSupport implements IBuildSupport {

	@Override
	public boolean applies(IProject project) {
		return ProjectUtils.isGradleProject(project);
	}

	@Override
	public void update(IProject project, IProgressMonitor monitor) throws CoreException {
		if (!applies(project)) {
			return;
		}
		JavaLanguageServerPlugin.logInfo("Starting Gradle update for "+project.getName());
		Optional<GradleBuild> build = CorePlugin.gradleWorkspaceManager().getGradleBuild(project);
		if (build.isPresent()){
			build.get().synchronize(NewProjectHandler.IMPORT_AND_MERGE);
		}
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		return resource != null
				&& resource.getType()== IResource.FILE
				&& resource.getName().endsWith(".gradle");
	}
}
