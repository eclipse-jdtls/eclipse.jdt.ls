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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.MavenPluginActivator;
import org.eclipse.m2e.core.internal.project.ProjectConfigurationManager;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.MavenUpdateRequest;
import org.eclipse.m2e.jdt.IClasspathManager;
import org.eclipse.m2e.jdt.MavenJdtPlugin;
import org.eclipse.m2e.jdt.internal.launch.MavenRuntimeClasspathProvider;

/**
 * @author Fred Bricon
 *
 */
public class MavenBuildSupport implements IBuildSupport {

	public static final String POM_FILE = "pom.xml";

	private static final int MAX_TIME_MILLIS = 3000;
	private static final List<String> WATCH_FILE_PATTERNS = Collections.singletonList("**/pom.xml");
	private static Cache<String, Boolean> downloadRequestsCache = CacheBuilder.newBuilder().maximumSize(100).expireAfterWrite(1, TimeUnit.HOURS).build();

	private IProjectConfigurationManager configurationManager;
	private DigestStore digestStore;
	private IMavenProjectRegistry registry;

	public MavenBuildSupport() {
		this.configurationManager = MavenPlugin.getProjectConfigurationManager();
		this.digestStore = JavaLanguageServerPlugin.getDigestStore();
		this.registry = MavenPlugin.getMavenProjectRegistry();
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

		if (!needsMavenUpdate(project) && !force) {
			return;
		}

		JavaLanguageServerPlugin.logInfo("Starting Maven update for " + project.getName());
		boolean updateSnapshots = JavaLanguageServerPlugin.getPreferencesManager() == null ? false : JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isMavenUpdateSnapshots();
		if (JavaLanguageServerPlugin.getProjectsManager().isProjectsInitialized()) {
			Set<IProject> projectSet = new LinkedHashSet<>();
			collectProjects(projectSet, project, monitor);
			IProject[] projects = projectSet.toArray(new IProject[0]);
			MavenUpdateRequest request = new MavenUpdateRequest(projects, MavenPlugin.getMavenConfiguration().isOffline(), updateSnapshots);
			((ProjectConfigurationManager) configurationManager).updateProjectConfiguration(request, true, true, monitor);
		} else {
			// Every Maven project will be updated during initialization, no need to collect them.
			MavenUpdateRequest request = new MavenUpdateRequest(project, MavenPlugin.getMavenConfiguration().isOffline(), updateSnapshots);
			configurationManager.updateProjectConfiguration(request, monitor);
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

	@Override
	public boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor) throws CoreException {
		if (resource == null || !applies(resource.getProject())) {
			return false;
		}
		return IBuildSupport.super.fileChanged(resource, changeType, monitor) || isBuildFile(resource);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IBuildSupport#getSource(org.eclipse.jdt.core.IClassFile, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void discoverSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		if (classFile == null) {
			return;
		}
		IJavaElement element = classFile;
		while (element.getParent() != null) {
			element = element.getParent();
			if (element instanceof IPackageFragmentRoot) {
				final IPackageFragmentRoot fragment = (IPackageFragmentRoot) element;
				IPath attachmentPath = fragment.getSourceAttachmentPath();
				if (attachmentPath != null && !attachmentPath.isEmpty() && attachmentPath.toFile().exists()) {
					break;
				}
				if (fragment.isArchive()) {
					IPath path = fragment.getPath();
					Boolean downloaded = downloadRequestsCache.getIfPresent(path.toString());
					if (downloaded == null) {
						downloadRequestsCache.put(path.toString(), true);
						IClasspathManager buildpathManager = MavenJdtPlugin.getDefault().getBuildpathManager();
						buildpathManager.scheduleDownload(fragment, true, true);
						JobHelpers.waitForDownloadSourcesJobs(MAX_TIME_MILLIS);
					}
					break;
				}
			}
		}
	}

	@Override
	public ILaunchConfiguration getLaunchConfiguration(IJavaProject javaProject, String scope) throws CoreException {
		return new JavaApplicationLaunchConfiguration(javaProject.getProject(), scope, MavenRuntimeClasspathProvider.MAVEN_CLASSPATH_PROVIDER);
	}

	@Override
	public List<String> getWatchPatterns() {
		return WATCH_FILE_PATTERNS;
	}

	public boolean needsMavenUpdate(IProject project) throws CoreException {
		File pomFile = project.getFile("pom.xml").getLocation().toFile();
		return pomFile.lastModified() > getLastWorkspaceStateModified() || digestStore.updateDigest(pomFile.toPath()) ||
			!project.getFile(IJavaProject.CLASSPATH_FILE_NAME).exists();
	}

	private static long getLastWorkspaceStateModified() {
		File workspaceStateFile = MavenPluginActivator.getDefault().getMavenProjectManager().getWorkspaceStateFile();
		return workspaceStateFile.lastModified();
	}
}
