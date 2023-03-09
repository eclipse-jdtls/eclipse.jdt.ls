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
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.preferences.MavenConfigurationImpl;
import org.eclipse.m2e.core.lifecyclemapping.model.PluginExecutionAction;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

@SuppressWarnings("restriction")
public class MavenProjectImporter extends AbstractProjectImporter {

	private static final int MAX_PROJECTS_TO_IMPORT = 50;

	private static final long MAX_MEMORY = 1536 * 1024 * 1024; // 1.5g

	public static final String IMPORTING_MAVEN_PROJECTS = "Importing Maven project(s)";

	public static final String POM_FILE = "pom.xml";

	private static final String DUPLICATE_ARTIFACT_TEMPLATE = "[groupId]-[artifactId]";
	private static final String STATE_FILENAME = "workspaceState.ser";

	private Set<MavenProjectInfo> projectInfos = null;

	private IProjectConfigurationManager configurationManager;
	private DigestStore digestStore;

	public MavenProjectImporter() {
		this.configurationManager = MavenPlugin.getProjectConfigurationManager();
		this.digestStore = JavaLanguageServerPlugin.getDigestStore();
	}

	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager != null && !preferencesManager.getPreferences().isImportMavenEnabled()) {
			return false;
		}
		if (directories == null) {
			//@formatter:off
			BasicFileDetector mavenDetector = new BasicFileDetector(rootFolder.toPath(), IMavenConstants.POM_FILE_NAME)
					.includeNested(false)
					.addExclusions("**/target"); //default maven build dir
			//@formatter:on
			for (IProject project : ProjectUtils.getAllProjects()) {
				if (!ProjectUtils.isMavenProject(project)) {
					String path = project.getLocation().toOSString();
					mavenDetector.addExclusions(path);
				}
			}
			directories = mavenDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}

	@Override
	public boolean applies(Collection<IPath> buildFiles, IProgressMonitor monitor) {
		if (!getPreferences().isImportMavenEnabled()) {
			return false;
		}

		Collection<java.nio.file.Path> configurationDirs = findProjectPathByConfigurationName(buildFiles, Arrays.asList(POM_FILE), true /*includeNested*/);
		if (configurationDirs == null || configurationDirs.isEmpty()) {
			return false;
		}

		Set<java.nio.file.Path> noneMavenProjectPaths = new HashSet<>();
		for (IProject project : ProjectUtils.getAllProjects()) {
			if (!ProjectUtils.isMavenProject(project)) {
				noneMavenProjectPaths.add(project.getLocation().toFile().toPath());
			}
		}

		this.directories = configurationDirs.stream()
			.filter(d -> {
				boolean folderIsImported = noneMavenProjectPaths.stream().anyMatch(path -> {
					return path.compareTo(d) == 0;
				});
				return !folderIsImported;
			})
			.collect(Collectors.toList());

		return !this.directories.isEmpty();
	}


	synchronized Set<MavenProjectInfo> getMavenProjectInfo(IProgressMonitor monitor) throws OperationCanceledException {
		if (projectInfos == null) {
			projectInfos = collectMavenProjectInfo(monitor);
		}
		return projectInfos;
	}

	Set<MavenProjectInfo> collectMavenProjectInfo(IProgressMonitor monitor) throws OperationCanceledException {
		MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
		return getMavenProjects(getProjectDirectory(), modelManager, monitor);
	}

	@Override
	public void reset() {
		projectInfos = null;
	}

	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		JavaLanguageServerPlugin.logInfo(IMPORTING_MAVEN_PROJECTS);
		MavenConfigurationImpl configurationImpl = (MavenConfigurationImpl)MavenPlugin.getMavenConfiguration();
		configurationImpl.setDownloadSources(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isMavenDownloadSources());
		configurationImpl.setNotCoveredMojoExecutionSeverity(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getMavenNotCoveredPluginExecutionSeverity());
		PluginExecutionAction action = PluginExecutionAction.valueOf(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getMavenDefaultMojoExecutionAction());
		configurationImpl.setDefaultMojoExecutionAction(action);
		SubMonitor subMonitor = SubMonitor.convert(monitor, 105);
		subMonitor.setTaskName(IMPORTING_MAVEN_PROJECTS);
		Set<MavenProjectInfo> files = getMavenProjectInfo(subMonitor.split(5));
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		Collection<IProject> projects = new LinkedHashSet<>();
		Collection<MavenProjectInfo> toImport = new LinkedHashSet<>();
		long lastWorkspaceStateSaved = getLastWorkspaceStateModified();
		Set<String> artifactIds = new LinkedHashSet<>();
		//Separate existing projects from new ones
		for (MavenProjectInfo projectInfo : files) {
			File pom = projectInfo.getPomFile();
			IContainer container = root.getContainerForLocation(new Path(pom.getAbsolutePath()));
			// getContainerForLocation() will return the nearest container for the given path,
			// if the project has been imported, container.getProject() will return the imported IProject
			// otherwise, container.getProject() will return the root level project
			if (container != null && projectInfo.getParent() != null) {
				MavenProjectInfo parentInfo = projectInfo.getParent();
				File parentPom = parentInfo.getPomFile();
				IContainer parentContainer = root.getContainerForLocation(new Path(parentPom.getAbsolutePath()));
				if (parentContainer != null && Objects.equals(container.getProject(), parentContainer.getProject())) {
					container = null;
				}
			}
			if (container == null) {
				digestStore.updateDigest(pom.toPath());
				toImport.add(projectInfo);
				artifactIds.add(projectInfo.getModel().getArtifactId());
			} else {
				IProject project = container.getProject();
				boolean valid = !ProjectUtils.isJavaProject(project) || project.getFile(IJavaProject.CLASSPATH_FILE_NAME).exists();
				if (ProjectUtils.isMavenProject(project) && valid) {
					projects.add(container.getProject());
				} else if (project != null) {
					//Project doesn't have the Maven nature, so we (re)import it
					digestStore.updateDigest(pom.toPath());
					// need to delete project due to m2e failing to create if linked and not the same name
					project.delete(IProject.FORCE | IProject.NEVER_DELETE_PROJECT_CONTENT, subMonitor.split(5));
					toImport.add(projectInfo);
					artifactIds.add(projectInfo.getModel().getArtifactId());
				}
			}

		}
		if (!toImport.isEmpty()) {
			ProjectImportConfiguration importConfig = new ProjectImportConfiguration();
			if (toImport.size() > artifactIds.size()) {
				// Ensure project name is unique when same artifactId
				importConfig.setProjectNameTemplate(DUPLICATE_ARTIFACT_TEMPLATE);
			}
			if (toImport.size() > MAX_PROJECTS_TO_IMPORT && Runtime.getRuntime().maxMemory() <= MAX_MEMORY) {
				JavaLanguageServerPlugin.logInfo("Projects size:" + toImport.size());
				Iterator<MavenProjectInfo> iter = toImport.iterator();
				List<IMavenProjectImportResult> results = new ArrayList<>(MAX_PROJECTS_TO_IMPORT);
				SubMonitor monitor2 = SubMonitor.convert(monitor, toImport.size() * 2);
				int it = 1;
				while (iter.hasNext()) {
					int percent = Math.min(100, it++ * 100 * MAX_PROJECTS_TO_IMPORT / (toImport.size() + 1));
					monitor2.setTaskName(percent + "% " + IMPORTING_MAVEN_PROJECTS);
					List<MavenProjectInfo> importPartial = new ArrayList<>();
					int i = 0;
					while (i++ < MAX_PROJECTS_TO_IMPORT && iter.hasNext()) {
						importPartial.add(iter.next());
					}
					List<IMavenProjectImportResult> result = configurationManager.importProjects(importPartial, importConfig, monitor2.split(MAX_PROJECTS_TO_IMPORT));
					results.addAll(result);
					monitor2.setWorkRemaining(toImport.size() * 2 - it * MAX_PROJECTS_TO_IMPORT);
				}
				List<IProject> imported = new ArrayList<>(results.size());
				for (IMavenProjectImportResult result : results) {
					imported.add(result.getProject());
				}
				monitor2.setTaskName("Updating Maven project(s)");
				updateProjects(imported, lastWorkspaceStateSaved, monitor2.split(projects.size()));
				monitor2.done();
			} else {
				configurationManager.importProjects(toImport, importConfig, subMonitor.split(75));
			}
		}
		subMonitor.setWorkRemaining(20);
		updateProjects(projects, lastWorkspaceStateSaved, subMonitor.split(20));
		subMonitor.done();
	}

	private long getLastWorkspaceStateModified() {
		Bundle bundle = FrameworkUtil.getBundle(IMaven.class);
		if (bundle != null) {
			IPath result = Platform.getStateLocation(bundle);
			File bundleStateLocation = result.toFile();
			File workspaceStateFile = new File(bundleStateLocation, STATE_FILENAME);
			return workspaceStateFile.lastModified();
		}
		return 0l;
	}

	private File getProjectDirectory() {
		return rootFolder;
	}

	private void updateProjects(Collection<IProject> projects, long lastWorkspaceStateSaved, IProgressMonitor monitor) throws CoreException {
		if (projects.isEmpty()) {
			return;
		}
		Iterator<IProject> iterator = projects.iterator();
		while (iterator.hasNext()) {
			IProject project = iterator.next();
			project.open(monitor);
			if (Platform.OS_WIN32.equals(Platform.getOS())) {
				project.refreshLocal(IResource.DEPTH_ONE, monitor);
				((Workspace) ResourcesPlugin.getWorkspace()).getRefreshManager().refresh(project);
			} else {
				project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			}
			if (!needsMavenUpdate(project, lastWorkspaceStateSaved)) {
				iterator.remove();
			}
		}
		if (projects.isEmpty()) {
			return;
		}

		new WorkspaceJob("Update Maven project configuration") {
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
				MavenBuildSupport mavenBuildSupport = new MavenBuildSupport();
				mavenBuildSupport.setShouldCollectProjects(false);
				for (IProject project : projects) {
					if (monitor.isCanceled()) {
						return Status.CANCEL_STATUS;
					}
					mavenBuildSupport.update(project, false, monitor);
				}
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	private boolean needsMavenUpdate(IProject project, long lastWorkspaceStateSaved) {
		return project.getFile(POM_FILE).getLocalTimeStamp() > lastWorkspaceStateSaved;
	}

	private Set<MavenProjectInfo> getMavenProjects(File directory, MavenModelManager modelManager, IProgressMonitor monitor) throws OperationCanceledException {
		if (directory == null || directories == null || directories.isEmpty()) {
			return Collections.emptySet();
		}
		try {
			List<String> folders = directories.stream().map(java.nio.file.Path::toAbsolutePath).map(Object::toString).collect(Collectors.toList());
			LocalProjectScanner scanner = new LocalProjectScanner(folders, false, modelManager);
			scanner.run(monitor);
			return collectProjects(scanner.getProjects());
		} catch (InterruptedException e) {
			throw new OperationCanceledException();
		}
	}

	public boolean isMavenProject() {
		return  isMavenProject(getProjectDirectory());
	}

	private boolean isMavenProject(File dir) {
		if (!isReadable(dir)
				|| !dir.isDirectory()) {
			return false;
		}
		return isReadable(new File(dir, POM_FILE));
	}

	private boolean isReadable(File destination) {
		return destination != null
				&& destination.canRead();
	}

	public Set<MavenProjectInfo> collectProjects(
			Collection<MavenProjectInfo> projects) {
		return new LinkedHashSet<MavenProjectInfo>() {
			private static final long serialVersionUID = 1L;

			public Set<MavenProjectInfo> collectProjects(
					Collection<MavenProjectInfo> projects) {
				for (MavenProjectInfo projectInfo : projects) {
					add(projectInfo);
					collectProjects(projectInfo.getProjects());
				}
				return this;
			}
		}.collectProjects(projects);
	}
}
