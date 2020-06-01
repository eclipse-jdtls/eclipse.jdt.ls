/*******************************************************************************
 * Copyright (c) 2016-2020 Red Hat Inc. and others.
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

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.launch.GradleClasspathProvider;
import org.eclipse.buildship.core.internal.util.file.FileUtils;
import org.eclipse.buildship.core.internal.workspace.WorkbenchShutdownEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;

/**
 * @author Fred Bricon
 *
 */
public class GradleBuildSupport implements IBuildSupport {

	public static final String GRADLE_SUFFIX = ".gradle";
	public static final String GRADLE_PROPERTIES = "gradle.properties";
	public static final List<String> WATCH_FILE_PATTERNS = Arrays.asList("**/*.gradle", "**/gradle.properties");

	@Override
	public boolean applies(IProject project) {
		return ProjectUtils.isGradleProject(project);
	}

	@Override
	public void update(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {
		if (!applies(project)) {
			return;
		}
		JavaLanguageServerPlugin.logInfo("Starting Gradle update for " + project.getName());
		if (force) {
			String projectPath = project.getLocation().toFile().getAbsolutePath();
			BuildConfiguration buildConfiguration = GradleProjectImporter.getBuildConfiguration(Paths.get(projectPath));
			GradleBuild build = GradleCore.getWorkspace().createBuild(buildConfiguration);
			build.synchronize(monitor);
		} else {
			Optional<GradleBuild> build = GradleCore.getWorkspace().getBuild(project);
			if (build.isPresent()) {
				build.get().synchronize(monitor);
			}
		}
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		if (resource != null && resource.getType() == IResource.FILE && (resource.getName().endsWith(GRADLE_SUFFIX) || resource.getName().equals(GRADLE_PROPERTIES))
				&& ProjectUtils.isGradleProject(resource.getProject())) {
			try {
				if (!ProjectUtils.isJavaProject(resource.getProject())) {
					return true;
				}
				IJavaProject javaProject = JavaCore.create(resource.getProject());
				IPath outputLocation = javaProject.getOutputLocation();
				return outputLocation == null || !outputLocation.isPrefixOf(resource.getFullPath());
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		return false;
	}

	@Override
	public boolean isBuildLikeFileName(String fileName) {
		return fileName.endsWith(GRADLE_SUFFIX) || fileName.equals(GRADLE_PROPERTIES);
	}

	/**
	 * delete stale gradle project preferences
	 *
	 * @param monitor
	 */
	public static void cleanGradleModels(IProgressMonitor monitor) {
		File projectPreferences = CorePlugin.getInstance().getStateLocation().append("project-preferences").toFile();
		if (projectPreferences.isDirectory()) {
			File[] projectFiles = projectPreferences.listFiles();
			for (File projectFile : projectFiles) {
				String projectName = projectFile.getName();
				if (!ResourcesPlugin.getWorkspace().getRoot().getProject(projectName).exists()) {
					FileUtils.deleteRecursively(projectFile);
				}
			}
		}
	}

	@Override
	public boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor) throws CoreException {
		if (resource == null || !applies(resource.getProject())) {
			return false;
		}
		return IBuildSupport.super.fileChanged(resource, changeType, monitor) || isBuildFile(resource);
	}

	/**
	 * save gradle project preferences
	 *
	 */
	public static void saveModels() {
		CorePlugin.listenerRegistry().dispatch(new WorkbenchShutdownEvent());
	}

	@Override
	public ILaunchConfiguration getLaunchConfiguration(IJavaProject javaProject, String scope) throws CoreException {
		return new JavaApplicationLaunchConfiguration(javaProject.getProject(), scope, GradleClasspathProvider.ID);
	}

	@Override
	public List<String> getWatchPatterns() {
		return WATCH_FILE_PATTERNS;
	}

}
