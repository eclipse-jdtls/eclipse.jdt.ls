/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.managers;

import java.util.Collections;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.workspace.CompositeGradleBuild;
import org.eclipse.buildship.core.workspace.NewProjectHandler;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.internal.ProjectUtils;

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
		CompositeGradleBuild build = CorePlugin.gradleWorkspaceManager().getCompositeBuild(Collections.singleton(project));
		build.synchronize(NewProjectHandler.IMPORT_AND_MERGE);
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		return resource != null
				&& resource.getType()== IResource.FILE
				&& resource.getName().endsWith(".gradle");
	}
}
