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
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.SynchronizationResult;
import org.eclipse.buildship.core.WrapperGradleDistribution;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

/**
 * @author Fred Bricon
 *
 */
public class GradleProjectImporter extends AbstractProjectImporter {

	public static final String GRADLE_HOME = "GRADLE_HOME";

	public static final String BUILD_GRADLE_DESCRIPTOR = "build.gradle";

	public static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.forVersion(GradleVersion.current().getVersion());

	public static final String IMPORTING_GRADLE_PROJECTS = "Importing Gradle project(s)";

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
		List<IProject> projects = ProjectUtils.getGradleProjects();
		for (IProject project : projects) {
			File projectDir = project.getLocation() == null ? null : project.getLocation().toFile();
			if (location.equals(projectDir)) {
				shouldSynchronize = checkGradlePersistence(shouldSynchronize, project, projectDir);
				break;
			}
		}
		if (shouldSynchronize) {
			BuildConfiguration build = getBuildConfiguration(rootFolder);
			SynchronizationResult result = GradleCore.getWorkspace().createBuild(build).synchronize(monitor);
			if (!result.getStatus().isOK()) {
				JavaLanguageServerPlugin.log(result.getStatus());
			}
		}
	}

	public static BuildConfiguration getBuildConfiguration(Path rootFolder) {
		GradleDistribution distribution = getGradleDistribution(rootFolder);
		boolean overrideWorkspaceConfiguration = !(distribution instanceof WrapperGradleDistribution);
		String javaHomeStr = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaHome();
		File javaHome = javaHomeStr == null ? null : new File(javaHomeStr);
		List<String> gradleArguments = JavaLanguageServerPlugin.getPreferencesManager() != null ? JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleArguments() : new ArrayList<>();
		List<String> gradleJvmArguments = JavaLanguageServerPlugin.getPreferencesManager() != null ? JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleJvmArguments() : new ArrayList<>();
		boolean offlineMode = JavaLanguageServerPlugin.getPreferencesManager() != null ? JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isImportGradleOfflineEnabled() : false;
		// @formatter:off
		BuildConfiguration build = BuildConfiguration.forRootProjectDirectory(rootFolder.toFile())
				.overrideWorkspaceConfiguration(overrideWorkspaceConfiguration)
				.gradleDistribution(distribution)
				.javaHome(javaHome)
				.arguments(gradleArguments)
				.jvmArguments(gradleJvmArguments)
				.offlineMode(offlineMode)
				.build();
		// @formatter:on
		return build;
	}

	public static boolean shouldSynchronize(File location) {
		boolean shouldSynchronize = true;
		List<IProject> projects = ProjectUtils.getGradleProjects();
		for (IProject project : projects) {
			File projectDir = project.getLocation() == null ? null : project.getLocation().toFile();
			if (location.equals(projectDir)) {
				shouldSynchronize = checkGradlePersistence(shouldSynchronize, project, projectDir);
				break;
			}
		}
		return shouldSynchronize;
	}

	private static boolean checkGradlePersistence(boolean shouldSynchronize, IProject project, File projectDir) {
		if (!ProjectUtils.isJavaProject(project) || !project.getFile(IJavaProject.CLASSPATH_FILE_NAME).exists()) {
			return true;
		}
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

}
