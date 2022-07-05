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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.MavenPluginActivator;
import org.eclipse.m2e.core.internal.project.ProjectConfigurationManager;
import org.eclipse.m2e.core.internal.project.registry.ProjectRegistryManager;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.MavenUpdateRequest;
import org.eclipse.m2e.jdt.internal.launch.MavenRuntimeClasspathProvider;

/**
 * @author Fred Bricon
 *
 */
public class MavenBuildSupport implements IBuildSupport {

	public static final String UNSUPPORTED_ON_MAVEN = "Unsupported operation. Please use pom.xml file to manage the source directories of maven project.";
	private static final List<String> WATCH_FILE_PATTERNS = Collections.singletonList("**/pom.xml");

	private IProjectConfigurationManager configurationManager;
	private ProjectRegistryManager projectManager;
	private DigestStore digestStore;
	private IMavenProjectRegistry registry;
	private boolean shouldCollectProjects;

	public MavenBuildSupport() {
		this.configurationManager = MavenPlugin.getProjectConfigurationManager();
		this.projectManager = MavenPluginActivator.getDefault().getMavenProjectManagerImpl();
		this.digestStore = JavaLanguageServerPlugin.getDigestStore();
		this.registry = MavenPlugin.getMavenProjectRegistry();
		this.shouldCollectProjects = true;
	}

	@Override
	public boolean applies(IProject project) {
		return ProjectUtils.isMavenProject(project);
	}

	@Override
	public void update(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {
		if (!applies(project)) {
			return;
		}
		Path pomPath = project.getFile("pom.xml").getLocation().toFile().toPath();
		if (digestStore.updateDigest(pomPath) || force) {
			JavaLanguageServerPlugin.logInfo("Starting Maven update for " + project.getName());
			boolean updateSnapshots = JavaLanguageServerPlugin.getPreferencesManager() == null ? false : JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isMavenUpdateSnapshots();
			if (shouldCollectProjects()) {
				Set<IProject> projectSet = new LinkedHashSet<>();
				collectProjects(projectSet, project, monitor);
				MavenUpdateRequest request = new MavenUpdateRequest(projectSet, MavenPlugin.getMavenConfiguration().isOffline(), updateSnapshots);
				((ProjectConfigurationManager) configurationManager).updateProjectConfiguration(request, true, true, monitor);
			} else {
				MavenUpdateRequest request = new MavenUpdateRequest(project, MavenPlugin.getMavenConfiguration().isOffline(), updateSnapshots);
				configurationManager.updateProjectConfiguration(request, monitor);
			}
		}
	}

	public void collectProjects(Collection<IProject> projects, IProject project, IProgressMonitor monitor) {
		if (!project.isOpen() || !ProjectUtils.isMavenProject(project)) {
			return;
		}
		projects.add(project);
		IMavenProjectFacade projectFacade = registry.create(project, monitor);
		if (projectFacade != null && "pom".equals(projectFacade.getPackaging())) {
			List<String> modules = projectFacade.getMavenProjectModules();
			for (String module : modules) {
				IPath pomPath = ResourcesPlugin.getWorkspace().getRoot().getFullPath().append(module).append("pom.xml");
				IFile pom = ResourcesPlugin.getWorkspace().getRoot().getFile(pomPath);
				if (pom.exists()) {
					IProject p = pom.getProject();
					if (p.isOpen()) {
						collectProjects(projects, p, monitor);
					}
				}
			}
		}
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		return resource != null && resource.getProject() != null && resource.getType() == IResource.FILE && resource.getName().equals("pom.xml")
		//Check pom.xml is at the root of the project
				&& resource.getProject().equals(resource.getParent());
	}

	@Override
	public boolean isBuildLikeFileName(String fileName) {
		return fileName.equals("pom.xml");
	}

	public boolean shouldCollectProjects() {
		return shouldCollectProjects;
	}

	public void setShouldCollectProjects(boolean shouldCollectProjects) {
		this.shouldCollectProjects = shouldCollectProjects;
	}

	@Override
	public boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor) throws CoreException {
		if (resource == null || !applies(resource.getProject())) {
			return false;
		}
		return IBuildSupport.super.fileChanged(resource, changeType, monitor) || isBuildFile(resource);
	}

	@Override
	public void discoverSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		JavaLanguageServerPlugin.getDefaultSourceDownloader().discoverSource(classFile, monitor);
	}

	@Override
	public ILaunchConfiguration getLaunchConfiguration(IJavaProject javaProject, String scope) throws CoreException {
		return new JavaApplicationLaunchConfiguration(javaProject.getProject(), scope, MavenRuntimeClasspathProvider.MAVEN_CLASSPATH_PROVIDER);
	}

	@Override
	public List<String> getWatchPatterns() {
		return WATCH_FILE_PATTERNS;
	}

	@Override
	public String buildToolName() {
		return "Maven";
	}

	@Override
	public String unsupportedOperationMessage() {
		return UNSUPPORTED_ON_MAVEN;
	}

}
