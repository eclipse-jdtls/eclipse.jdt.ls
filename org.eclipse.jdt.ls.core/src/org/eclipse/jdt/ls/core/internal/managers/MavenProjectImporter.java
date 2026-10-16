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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
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
import org.eclipse.jdt.ls.core.internal.managers.ondemand.IModuleIndex;
import org.eclipse.jdt.ls.core.internal.managers.ondemand.MavenModuleIndex;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.MessageType;
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
					mavenDetector.addExclusions(path.replace("\\", "\\\\"));
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
		super.reset();
	}

	@Override
	public boolean supportsOnDemand() {
		return true;
	}

	@Override
	protected IModuleIndex createModuleIndex() {
		return new MavenModuleIndex(getWorkspacePath());
	}

	/**
	 * Imports a single Maven module via M2E.
	 *
	 * <p>Uses M2E's {@link LocalProjectScanner} to discover the module and
	 * {@link IProjectConfigurationManager#importProjects} to create the
	 * Eclipse project with proper Maven nature and classpath.</p>
	 *
	 * <h3>Duplicate artifactId handling</h3>
	 * <p>In on-demand mode, modules are imported one at a time. When two modules
	 * share the same artifactId (e.g. {@code org.a:common} and {@code org.b:common}),
	 * the second import would fail silently because an Eclipse project named
	 * "common" already exists. To handle this:</p>
	 * <ol>
	 *   <li>The existing project "common" is renamed to "org.a-common"</li>
	 *   <li>The new module is imported as "org.b-common"</li>
	 * </ol>
	 * <p>This matches the behavior of full import mode, where all projects
	 * use the {@code [groupId]-[artifactId]} template when duplicates exist.</p>
	 *
	 * <h3>Use cases</h3>
	 * <ul>
	 *   <li><b>No conflict</b> — user opens {@code a/core/src/Foo.java},
	 *       artifactId "core" is unique → project "core" created.</li>
	 *   <li><b>Duplicate artifactId</b> — user opens {@code b/common/src/Bar.java},
	 *       project "common" (from {@code a/common}) already exists
	 *       → "common" renamed to "org.a-common", new project "org.b-common" created.</li>
	 * </ul>
	 */
	@Override
	protected List<IProject> importModule(java.nio.file.Path modulePath, IProgressMonitor monitor) throws CoreException {
		File pomFile = modulePath.resolve(POM_FILE).toFile();
		if (!pomFile.isFile()) {
			return List.of();
		}
		configureMavenPreferences();
		try {
			MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
			ProjectImportConfiguration importConfig = new ProjectImportConfiguration();
			LocalProjectScanner scanner = new LocalProjectScanner(
					List.of(modulePath.toString()), false, modelManager);
			scanner.run(monitor);
			Set<MavenProjectInfo> infos = collectProjects(scanner.getProjects());
			if (infos.isEmpty()) {
				return List.of();
			}
			// ex: importing org.b:common, project "common" (org.a:common) already exists
			//   → rename "common" to "org.a-common"
			//   → import org.b:common as "org.b-common"
			for (MavenProjectInfo info : infos) {
				String artifactId = info.getModel().getArtifactId();
				IProject existing = ResourcesPlugin.getWorkspace().getRoot().getProject(artifactId);
				if (existing.exists()) {
					importConfig.setProjectNameTemplate(DUPLICATE_ARTIFACT_TEMPLATE);
					renameWithGroupId(existing, monitor);
					break;
				}
			}
			List<IMavenProjectImportResult> importResults = configurationManager.importProjects(
					infos, importConfig, monitor);
			return importResults.stream()
					.map(IMavenProjectImportResult::getProject)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
		} catch (InterruptedException e) {
			throw new OperationCanceledException();
		}
	}

	/**
	 * Renames a project from {@code [artifactId]} to {@code [groupId]-[artifactId]}.
	 *
	 * <p>Called when a second module with the same artifactId is being imported.
	 * Reads the groupId from the project's {@code pom.xml} (or its parent) to
	 * build the qualified name.</p>
	 *
	 * <p>Example: project "common" at {@code a/common/} with groupId {@code org.a}
	 * is renamed to "org.a-common".</p>
	 *
	 * @param project the existing project to rename
	 * @param monitor progress monitor
	 */
	private void renameWithGroupId(IProject project, IProgressMonitor monitor) {
		try {
			File pom = project.getLocation().toFile().toPath().resolve(POM_FILE).toFile();
			MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
			org.apache.maven.model.Model model = modelManager.readMavenModel(pom);
			String groupId = model.getGroupId();
			if (groupId == null && model.getParent() != null) {
				groupId = model.getParent().getGroupId();
			}
			if (groupId != null) {
				String newName = groupId + "-" + project.getName();
				IProjectDescription desc = project.getDescription();
				desc.setName(newName);
				project.move(desc, true, monitor);
				JavaLanguageServerPlugin.logInfo("On-demand rename: " + project.getName() + " → " + newName);
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Failed to rename project " + project.getName(), e);
		}
	}

	private void configureMavenPreferences() {
		MavenConfigurationImpl configurationImpl = (MavenConfigurationImpl)MavenPlugin.getMavenConfiguration();
		configurationImpl.setDownloadSources(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isMavenDownloadSources());
		try {
			configurationImpl.setNotCoveredMojoExecutionSeverity(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getMavenNotCoveredPluginExecutionSeverity());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e);
		}
		PluginExecutionAction action = PluginExecutionAction.valueOf(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getMavenDefaultMojoExecutionAction());
		configurationImpl.setDefaultMojoExecutionAction(action);
	}

	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		JavaLanguageServerPlugin.logInfo(IMPORTING_MAVEN_PROJECTS);
		configureMavenPreferences();
		SubMonitor subMonitor = SubMonitor.convert(monitor, 105);
		subMonitor.setTaskName(IMPORTING_MAVEN_PROJECTS);
		Set<MavenProjectInfo> files = getMavenProjectInfo(subMonitor.split(5));
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		Collection<IProject> projects = new LinkedHashSet<>();
		Collection<MavenProjectInfo> toImport = new LinkedHashSet<>();
		Collection<java.nio.file.Path> toUpdateDigest = new LinkedHashSet<>();
		long lastWorkspaceStateSaved = getLastWorkspaceStateModified();
		Set<String> artifactIds = new LinkedHashSet<>();
		//Separate existing projects from new ones
		for (MavenProjectInfo projectInfo : files) {
			File pom = projectInfo.getPomFile();
			IContainer container = root.getContainerForLocation(new Path(pom.getAbsolutePath()));
			// getContainerForLocation() will return the nearest container for the given path,
			// if the project has been imported, container.getProject() will return the imported IProject
			// otherwise, container.getProject() will return the root level project
			File parentPom = getParentPomFile(projectInfo);
			if (container != null && parentPom != null) {
				IContainer parentContainer = root.getContainerForLocation(new Path(parentPom.getAbsolutePath()));
				if (parentContainer != null && Objects.equals(container.getProject(), parentContainer.getProject())) {
					container = null;
				}
			}
			if (container == null) {
				toUpdateDigest.add(pom.toPath());
				toImport.add(projectInfo);
				artifactIds.add(projectInfo.getModel().getArtifactId());
			} else {
				IProject project = container.getProject();
				boolean valid = !ProjectUtils.isJavaProject(project) || project.getFile(IJavaProject.CLASSPATH_FILE_NAME).exists();
				if (ProjectUtils.isMavenProject(project) && valid) {
					projects.add(container.getProject());
				} else if (project != null) {
					//Project doesn't have the Maven nature, so we (re)import it
					toUpdateDigest.add(pom.toPath());
					// need to delete project due to m2e failing to create if linked and not the same name
					project.delete(IProject.FORCE | IProject.NEVER_DELETE_PROJECT_CONTENT, subMonitor.split(5));
					toImport.add(projectInfo);
					artifactIds.add(projectInfo.getModel().getArtifactId());
				}
			}

		}
		if (!toUpdateDigest.isEmpty()) {
			digestStore.updateDigests(toUpdateDigest);
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
					try {
						List<IMavenProjectImportResult> result = configurationManager.importProjects(importPartial, importConfig, monitor2.split(MAX_PROJECTS_TO_IMPORT));
						results.addAll(result);
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException("Failed to import a batch of Maven projects", e);
					}
					monitor2.setWorkRemaining(toImport.size() * 2 - it * MAX_PROJECTS_TO_IMPORT);
				}
				logFailedImports(results);
				List<IProject> imported = new ArrayList<>(results.size());
				for (IMavenProjectImportResult result : results) {
					if (result.getProject() != null) {
						imported.add(result.getProject());
					}
				}
				monitor2.setTaskName("Updating Maven project(s)");
				updateProjects(imported, lastWorkspaceStateSaved, monitor2.split(projects.size()));
				monitor2.done();
			} else {
				try {
					List<IMavenProjectImportResult> result = configurationManager.importProjects(toImport, importConfig, subMonitor.split(75));
					logFailedImports(result);
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Failed to configure some Maven project(s)", e);
				}
			}
		}
		subMonitor.setWorkRemaining(20);
		updateProjects(projects, lastWorkspaceStateSaved, subMonitor.split(20));
		subMonitor.done();
	}

	private void logFailedImports(List<IMavenProjectImportResult> results) {
		List<String> failedProjects = new ArrayList<>();
		for (IMavenProjectImportResult result : results) {
			if (result.getProject() == null) {
				File pomFile = result.getMavenProjectInfo().getPomFile();
				JavaLanguageServerPlugin.logError("Failed to import Maven project at " + pomFile + " - a project with the same name already exists (check for duplicate groupId/artifactId)");
				failedProjects.add(pomFile.getAbsolutePath());
			}
		}
		if (!failedProjects.isEmpty()) {
			String message = failedProjects.size() + " Maven project(s) could not be imported due to duplicate groupId/artifactId. See the language server log for details.";
			JavaLanguageServerPlugin.getInstance().getClientConnection().showNotificationMessage(MessageType.Error, message);
		}
	}

	private File getParentPomFile(MavenProjectInfo projectInfo) {
		if (projectInfo.getParent() != null) {
			return projectInfo.getParent().getPomFile();
		}

		if (projectInfo.getModel() != null) {
			String parentPomRelativePath = projectInfo.getModel().getParent() == null ? null : projectInfo.getModel().getParent().getRelativePath();
			if (StringUtils.isNotBlank(parentPomRelativePath)) {
				File currentProject = projectInfo.getPomFile().getParentFile();
				File parentPomFile = currentProject.toPath().resolve(parentPomRelativePath).normalize().toFile();
				if (parentPomFile.exists()) {
					return parentPomFile;
				}
			}
		}

		return null;
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
			IFile pomFile = project.getFile(POM_FILE);
			pomFile.refreshLocal(IResource.DEPTH_ZERO, monitor);
			if (!needsMavenUpdate(pomFile, lastWorkspaceStateSaved)) {
				iterator.remove();
				continue;
			}
			if (Platform.OS_WIN32.equals(Platform.getOS())) {
				project.refreshLocal(IResource.DEPTH_ONE, monitor);
				((Workspace) ResourcesPlugin.getWorkspace()).getRefreshManager().refresh(project);
			} else {
				project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
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

	private boolean needsMavenUpdate(IResource pomFile, long lastWorkspaceStateSaved) {
		return pomFile.getLocalTimeStamp() > lastWorkspaceStateSaved;
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
