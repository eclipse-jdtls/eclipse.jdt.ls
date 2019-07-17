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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.LocalGradleDistribution;
import org.eclipse.buildship.core.SynchronizationResult;
import org.eclipse.buildship.core.WrapperGradleDistribution;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.DefaultGradleBuild;
import org.eclipse.buildship.core.internal.configuration.ConfigurationManager;
import org.eclipse.buildship.core.internal.configuration.WorkspaceConfiguration;
import org.eclipse.buildship.core.internal.operation.ToolingApiJobResultHandler;
import org.eclipse.buildship.core.internal.operation.ToolingApiStatus;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.buildship.core.internal.workspace.SynchronizationJob;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

/**
 * @author Fred Bricon
 *
 */
public class GradleProjectImporter extends AbstractProjectImporter {

	private static final String CHECK_GRADLE_PROJECTS = "Check Gradle project(s)...";

	private static final String DOT_SETTINGS = ".settings";

	private static final String PREF_FILE = CorePlugin.PLUGIN_ID + ".prefs";

	public static final String GRADLE_HOME = "GRADLE_HOME";

	private static final String BUILD_GRADLE_DESCRIPTOR = "build.gradle";

	public static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.forVersion(GradleVersion.current().getVersion());

	public static final String IMPORTING_GRADLE_PROJECTS = "Importing Gradle project(s)";

	public static final String JAVALS_GRADLE_FAMILY = IConstants.PLUGIN_ID + ".gradle.jobs";

	private Collection<Path> directories;

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IProjectImporter#applies(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws CoreException {
		if (rootFolder == null) {
			return false;
		}
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager != null && !preferencesManager.getPreferences().isImportGradleEnabled()) {
			return false;
		}
		if (directories == null) {
			BasicFileDetector gradleDetector = new BasicFileDetector(rootFolder.toPath(), BUILD_GRADLE_DESCRIPTOR)
					.includeNested(false)
					.addExclusions("**/build");//default gradle build dir
			directories = gradleDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IProjectImporter#importToWorkspace(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException {
		if (!applies(monitor)) {
			return;
		}
		int projectSize = directories.size();
		SubMonitor subMonitor = SubMonitor.convert(monitor, projectSize + 1);
		subMonitor.setTaskName(IMPORTING_GRADLE_PROJECTS);
		JavaLanguageServerPlugin.logInfo(IMPORTING_GRADLE_PROJECTS);
		subMonitor.worked(1);
		directories.forEach(d -> importDir(d, subMonitor.newChild(1)));
		subMonitor.done();
	}

	private void importDir(Path rootFolder, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return;
		}
		startSynchronization(rootFolder, monitor);
	}

	public static GradleDistribution getGradleDistribution(Path rootFolder) {
		if (JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isGradleWrapperEnabled() && Files.exists(rootFolder.resolve("gradlew"))) {
			return GradleDistribution.fromBuild();
		}
		if (JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleVersion() != null) {
			List<GradleVersion> versions = CorePlugin.publishedGradleVersions().getVersions();
			GradleVersion gradleVersion = null;
			String versionString = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleVersion();
			GradleVersion requiredVersion = GradleVersion.version(versionString);
			for (GradleVersion version : versions) {
				if (version.compareTo(requiredVersion) == 0) {
					gradleVersion = version;
					break;
				}
			}
			if (gradleVersion != null) {
				return GradleDistribution.forVersion(gradleVersion.getVersion());
			} else {
				JavaLanguageServerPlugin.logInfo("Invalid gradle version" + versionString);
			}
		}
		File gradleHomeFile = getGradleHomeFile();
		if (gradleHomeFile != null) {
			return GradleDistribution.forLocalInstallation(gradleHomeFile);
		}
		return DEFAULT_DISTRIBUTION;
	}

	public static File getGradleHomeFile() {
		if (JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleHome() != null) {
			return new File(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleHome());
		}
		Map<String, String> env = System.getenv();
		Properties sysprops = System.getProperties();
		return getGradleHomeFile(env, sysprops);
	}

	public static File getGradleHomeFile(Map<String, String> env, Properties sysprops) {
		if (JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleHome() != null) {
			return new File(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleHome());
		}
		String gradleHome = env.get(GRADLE_HOME);
		if (gradleHome == null || !new File(gradleHome).isDirectory()) {
			gradleHome = sysprops.getProperty(GRADLE_HOME);
		}
		if (gradleHome != null) {
			File gradleHomeFile = new File(gradleHome);
			if (gradleHomeFile.isDirectory()) {
				return gradleHomeFile;
			}
		}
		return null;
	}

	protected void startSynchronization(Path rootFolder, IProgressMonitor monitor) {
		File location = rootFolder.toFile();
		boolean shouldSynchronize = shouldSynchronize(location);
		if (shouldSynchronize) {
			List<IProject> projects = getGradleProjects(rootFolder);
			if (projects.size() == 0) {
				String content = getGradleSettings(rootFolder);
				synchronizeWorkspace(rootFolder, monitor);
				if (content != null) {
					Job job = new Job(CHECK_GRADLE_PROJECTS) {

						@Override
						protected IStatus run(IProgressMonitor monitor) {
							try {
								getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, monitor);
							} catch (OperationCanceledException | InterruptedException e) {
								JavaLanguageServerPlugin.logException(e.getMessage(), e);
							}
							List<IProject> projects = getGradleProjects(rootFolder);
							IProject project = null;
							for (IProject p : projects) {
								File projectRoot = p.getRawLocation().toFile();
								if (projectRoot.isDirectory() && projectRoot.toPath().equals(rootFolder)) {
									project = p;
									break;
								}
							}
							setGradleSettings(project, content, monitor);
							return Status.OK_STATUS;
						}

						/* (non-Javadoc)
						 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
						 */
						@Override
						public boolean belongsTo(Object family) {
							return JAVALS_GRADLE_FAMILY.equals(family);
						}
					};
					job.schedule();
				}
			} else {
				for (IProject project : projects) {
					if (checkGradlePersistence(true, project, getProjectDirectory(project))) {
						Optional<GradleBuild> gradleBuild = GradleCore.getWorkspace().getBuild(project);
						GradleBuild build;
						if (!gradleBuild.isPresent()) {
							BuildConfiguration buildConfiguration = getBuildConfiguration(rootFolder);
							build = GradleCore.getWorkspace().createBuild(buildConfiguration);
						} else {
							build = gradleBuild.get();
						}
						if (!((DefaultGradleBuild) build).isSynchronizing()) {
							SynchronizationJob job = new SynchronizationJob(build);
							job.setResultHandler(new ToolingApiJobResultHandler<Void>() {

								@Override
								public void onSuccess(Void result) {
								}

								@Override
								public void onFailure(ToolingApiStatus status) {
									JavaLanguageServerPlugin.log(status);
								}
							});
							job.setUser(false);
							job.schedule();
						}
					}
				}
			}
		}
	}

	private String getGradleSettings(Path dir) {
		Path settings = dir.resolve(DOT_SETTINGS);
		if (settings.toFile().isDirectory()) {
			File prefsFile = settings.resolve(PREF_FILE).toFile();
			if (prefsFile.isFile()) {
				try {
					return ResourceUtils.getContent(prefsFile);
				} catch (CoreException e) {
					// ignore
				}
			}
		}
		return null;
	}

	private List<IProject> getGradleProjects(Path rootFolder) {
		List<IProject> projects = ProjectUtils.getGradleProjects();
		Iterator<IProject> iter = projects.iterator();
		while (iter.hasNext()) {
			IProject project = iter.next();
			Path projectPath = project.getLocation().toFile().toPath();
			if (!projectPath.startsWith(rootFolder)) {
				iter.remove();
			}
		}
		return projects;
	}

	private void synchronizeWorkspace(Path rootFolder, IProgressMonitor monitor) {
		BuildConfiguration build = getBuildConfiguration(rootFolder);
		if (build.isOverrideWorkspaceConfiguration()) {
			File gradleUserHome = build.getGradleUserHome().isPresent() ? build.getGradleUserHome().get() : null;
			File javaHome = build.getJavaHome().isPresent() ? build.getJavaHome().get() : null;
			// @formatter:off
			WorkspaceConfiguration workspaceConfig = new WorkspaceConfiguration(build.getGradleDistribution(),
				gradleUserHome,
				javaHome,
				build.isOfflineMode(),
				build.isBuildScansEnabled(),
				build.isAutoSync(),
				build.getArguments(),
				build.getJvmArguments(),
				build.isShowConsoleView(),
				build.isShowExecutionsView());
			// @formatter:on
			CorePlugin.configurationManager().saveWorkspaceConfiguration(workspaceConfig);
		}
		// @formatter:on
		SynchronizationResult result = GradleCore.getWorkspace().createBuild(build).synchronize(monitor);
		if (!result.getStatus().isOK()) {
			JavaLanguageServerPlugin.log(result.getStatus());
		}
		if (build.isOverrideWorkspaceConfiguration()) {
			Job job = new Job(CHECK_GRADLE_PROJECTS) {

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, monitor);
					} catch (OperationCanceledException | InterruptedException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
					List<IProject> gradleProjects = ProjectUtils.getGradleProjects();
					ConfigurationManager manager = CorePlugin.configurationManager();
					for (IProject project : gradleProjects) {
						org.eclipse.buildship.core.internal.configuration.BuildConfiguration currentConfig = manager.loadProjectConfiguration(project).getBuildConfiguration();
						if (currentConfig.isOverrideWorkspaceSettings() != build.isOverrideWorkspaceConfiguration()) {
							File gradleUserHome = build.getGradleUserHome().isPresent() ? build.getGradleUserHome().get() : null;
							File javaHome = build.getJavaHome().isPresent() ? build.getJavaHome().get() : null;
							// @formatter:off
							org.eclipse.buildship.core.internal.configuration.BuildConfiguration updatedConfig = manager.createBuildConfiguration(currentConfig.getRootProjectDirectory(),
								build.isOverrideWorkspaceConfiguration(),
								build.getGradleDistribution(),
								gradleUserHome,
								javaHome,
								build.isBuildScansEnabled(),
								build.isOfflineMode(),
								build.isAutoSync(),
								build.getArguments(),
								build.getJvmArguments(),
								build.isShowConsoleView(),
								build.isShowExecutionsView());
							// @formatter:on
							manager.saveBuildConfiguration(updatedConfig);
						}
					}
					return Status.OK_STATUS;
				}

				/* (non-Javadoc)
				 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
				 */
				@Override
				public boolean belongsTo(Object family) {
					return JAVALS_GRADLE_FAMILY.equals(family);
				}
			};
			job.schedule();
		}
	}

	public static BuildConfiguration getBuildConfiguration(Path rootFolder) {
		File location = rootFolder.toFile();
		GradleDistribution distribution = getGradleDistribution(rootFolder);
		File gradleUserHome = distribution instanceof LocalGradleDistribution ? getGradleHomeFile() : null;
		boolean overrideWorkspaceConfiguration = gradleUserHome != null || !(distribution instanceof WrapperGradleDistribution);
		String javaHomeStr = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaHome();
		File javaHome = javaHomeStr == null ? null : new File(javaHomeStr);
		List<String> gradleArguments = JavaLanguageServerPlugin.getPreferencesManager() != null ? JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleArguments() : new ArrayList<>();
		List<String> gradleJvmArguments = JavaLanguageServerPlugin.getPreferencesManager() != null ? JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleJvmArguments() : new ArrayList<>();
		// @formatter:off
		BuildConfiguration build = BuildConfiguration.forRootProjectDirectory(location)
			.overrideWorkspaceConfiguration(overrideWorkspaceConfiguration)
			.gradleDistribution(distribution)
			.javaHome(javaHome)
			.arguments(gradleArguments)
			.jvmArguments(gradleJvmArguments)
			.gradleUserHome(gradleUserHome)
			.build();
		return build;
	}

	public static boolean shouldSynchronize(File location) {
		boolean shouldSynchronize = true;
		List<IProject> projects = ProjectUtils.getGradleProjects();
		for (IProject project : projects) {
			File projectDir = getProjectDirectory(project);
			if (location.equals(projectDir)) {
				shouldSynchronize = checkGradlePersistence(shouldSynchronize, project, projectDir);
				break;
			}
		}
		return shouldSynchronize;
	}

	private static File getProjectDirectory(IProject project) {
		return project.getLocation() == null ? null : project.getLocation().toFile();
	}

	private static boolean checkGradlePersistence(boolean shouldSynchronize, IProject project, File projectDir) {
		PersistentModel model = CorePlugin.modelPersistence().loadModel(project);
		if (model.isPresent()) {
			File persistentFile = CorePlugin.getInstance().getStateLocation().append("project-preferences").append(project.getName()).toFile();
			if (persistentFile.exists()) {
				long modified = persistentFile.lastModified();
				if (projectDir.exists()) {
					File[] files = projectDir.listFiles(new FilenameFilter() {

						@Override
						public boolean accept(File dir, String name) {
							if (name != null && name.endsWith(GradleBuildSupport.GRADLE_SUFFIX)) {
								return new File(dir, name).lastModified() > modified;
							}
							return false;
						}
					});
					shouldSynchronize = files != null && files.length > 0;
				}
			}
		}
		return shouldSynchronize;
	}

	@Override
	public void reset() {
	}

	private void setGradleSettings(IProject project, String content, IProgressMonitor monitor) {
		if (content == null || project == null) {
			return;
		}
		IPath prefsPath = new org.eclipse.core.runtime.Path(DOT_SETTINGS).append(PREF_FILE);
		IFile prefsFile = project.getFile(prefsPath);
		try (InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
			prefsFile.setContents(stream, true, false, monitor);
		} catch (IOException | CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

}
