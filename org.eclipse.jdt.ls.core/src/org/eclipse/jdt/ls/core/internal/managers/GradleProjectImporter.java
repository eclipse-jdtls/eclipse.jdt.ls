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

import static java.util.Arrays.asList;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
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
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.internal.gradle.checksums.ValidationResult;
import org.eclipse.jdt.ls.internal.gradle.checksums.WrapperValidator;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

/**
 * @author Fred Bricon
 *
 */
@SuppressWarnings("restriction")
public class GradleProjectImporter extends AbstractProjectImporter {

	public static final String GRADLE_HOME = "GRADLE_HOME";

	public static final String GRADLE_USER_HOME = "GRADLE_USER_HOME";

	public static final String BUILD_GRADLE_DESCRIPTOR = "build.gradle";
	public static final String BUILD_GRADLE_KTS_DESCRIPTOR = "build.gradle.kts";
	public static final String SETTINGS_GRADLE_DESCRIPTOR = "settings.gradle";
	public static final String SETTINGS_GRADLE_KTS_DESCRIPTOR = "settings.gradle.kts";

	public static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.forVersion(GradleVersion.current().getVersion());

	public static final String IMPORTING_GRADLE_PROJECTS = "Importing Gradle project(s)";

	//@formatter:off
	public static final String GRADLE_WRAPPER_CHEKSUM_WARNING_TEMPLATE =
			"Security Warning! The gradle wrapper '@wrapper@' could be malicious. "
			+ "If you trust it, please add \n"
			+ "`{\"sha256\": \"@checksum@\","
			+ "\n\"allowed\": true}`"
			+ "\n to the `java.import.gradle.wrapper.checksums` preference."
			+ ""
			.replaceAll("\n", System.lineSeparator());
	//@formatter:on

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IProjectImporter#applies(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws CoreException {
		if (rootFolder == null) {
			return false;
		}
		Preferences preferences = getPreferences();
		if (!preferences.isImportGradleEnabled()) {
			return false;
		}
		if (directories == null) {
			BasicFileDetector gradleDetector = new BasicFileDetector(rootFolder.toPath(), BUILD_GRADLE_DESCRIPTOR,
					SETTINGS_GRADLE_DESCRIPTOR, BUILD_GRADLE_KTS_DESCRIPTOR, SETTINGS_GRADLE_KTS_DESCRIPTOR)
					.includeNested(false)
					.addExclusions("**/build")//default gradle build dir
					.addExclusions("**/bin");
			for (IProject project : ProjectUtils.getAllProjects()) {
				if (!ProjectUtils.isGradleProject(project)) {
					String path = project.getLocation().toOSString();
					gradleDetector.addExclusions(path);
				}
			}
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
		// store the digest for the imported gradle projects.
		ProjectUtils.getGradleProjects().forEach(project -> {
			File buildFile = project.getFile(BUILD_GRADLE_DESCRIPTOR).getLocation().toFile();
			File settingsFile = project.getFile(SETTINGS_GRADLE_DESCRIPTOR).getLocation().toFile();
			File buildKtsFile = project.getFile(BUILD_GRADLE_KTS_DESCRIPTOR).getLocation().toFile();
			File settingsKtsFile = project.getFile(SETTINGS_GRADLE_KTS_DESCRIPTOR).getLocation().toFile();
			try {
				if (buildFile.exists()) {
					JavaLanguageServerPlugin.getDigestStore().updateDigest(buildFile.toPath());
				} else if (buildKtsFile.exists()) {
					JavaLanguageServerPlugin.getDigestStore().updateDigest(buildKtsFile.toPath());
				}
				if (settingsFile.exists()) {
					JavaLanguageServerPlugin.getDigestStore().updateDigest(settingsFile.toPath());
				} else if (settingsKtsFile.exists()) {
					JavaLanguageServerPlugin.getDigestStore().updateDigest(settingsKtsFile.toPath());
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to update digest for gradle build file", e);
			}
		});
		subMonitor.done();
	}

	private void importDir(Path projectFolder, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return;
		}
		startSynchronization(projectFolder, monitor);
	}


	public static GradleDistribution getGradleDistribution(Path rootFolder) {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		Preferences preferences = getPreferences();
		if (preferencesManager != null && preferences.isGradleWrapperEnabled() && Files.exists(rootFolder.resolve("gradlew"))) {
			WrapperValidator validator = new WrapperValidator();
			try {
				ValidationResult result = validator.checkWrapper(rootFolder.toFile().getAbsolutePath());
				if (result.isValid()) {
					WrapperGradleDistribution gradleDistribution = GradleDistribution.fromBuild();
					return gradleDistribution;
				} else {
					if (!WrapperValidator.contains(result.getChecksum())) {
						ProjectsManager pm = JavaLanguageServerPlugin.getProjectsManager();
						if (pm != null && pm.getConnection() != null) {
							if (preferencesManager.getClientPreferences().isGradleChecksumWrapperPromptSupport()) {
								String id = "gradle/checksum/prompt";
								ExecuteCommandParams params = new ExecuteCommandParams(id, asList(result.getWrapperJar(), result.getChecksum()));
								pm.getConnection().sendNotification(params);
							} else {
								//@formatter:off
								String message = GRADLE_WRAPPER_CHEKSUM_WARNING_TEMPLATE.replaceAll("@wrapper@", result.getWrapperJar())
																						.replaceAll("@checksum@", result.getChecksum());
								//@formatter:on
								pm.getConnection().showMessage(new MessageParams(MessageType.Error, message));
							}
						}
					}
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		if (StringUtils.isNotBlank(preferences.getGradleVersion())) {
			List<GradleVersion> versions = CorePlugin.publishedGradleVersions().getVersions();
			GradleVersion gradleVersion = null;
			String versionString = preferences.getGradleVersion();
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
		Preferences preferences = getPreferences();
		if (StringUtils.isNotBlank(preferences.getGradleHome())) {
			return new File(preferences.getGradleHome());
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

	public static File getGradleUserHomeFile() {
		Preferences preferences = getPreferences();
		if (StringUtils.isNotBlank(preferences.getGradleUserHome())) {
			return new File(preferences.getGradleUserHome());
		}
		String gradleUserHome = System.getenv().get(GRADLE_USER_HOME);
		if (gradleUserHome == null) {
			gradleUserHome = System.getProperties().getProperty(GRADLE_USER_HOME);
		}
		return (gradleUserHome == null || gradleUserHome.isEmpty()) ? null : new File(gradleUserHome);
	}

	public static File getGradleJavaHomeFile() {
		Preferences preferences = getPreferences();
		if (StringUtils.isNotBlank(preferences.getGradleJavaHome())) {
			File file = new File(preferences.getGradleJavaHome());
			if (file.isDirectory()) {
				return file;
			}
		}
		return null;
	}

	protected void startSynchronization(Path projectFolder, IProgressMonitor monitor) {
		File location = projectFolder.toFile();
		boolean shouldSynchronize = shouldSynchronize(location);
		if (shouldSynchronize) {
			BuildConfiguration build = getBuildConfiguration(projectFolder);
			SynchronizationResult result = GradleCore.getWorkspace().createBuild(build).synchronize(monitor);
			if (!result.getStatus().isOK()) {
				JavaLanguageServerPlugin.log(result.getStatus());
			}
		}
	}

	public static BuildConfiguration getBuildConfiguration(Path rootFolder) {
		GradleDistribution distribution = getGradleDistribution(rootFolder);
		File javaHome = getGradleJavaHomeFile();
		Preferences preferences = getPreferences();
		if (javaHome == null) {
			IVMInstall javaDefaultRuntime = JavaRuntime.getDefaultVMInstall();
			if (javaDefaultRuntime != null && javaDefaultRuntime.getVMRunner(ILaunchManager.RUN_MODE) != null) {
				javaHome = javaDefaultRuntime.getInstallLocation();
			} else {
				String javaHomeStr = preferences.getJavaHome();
				javaHome = javaHomeStr == null ? null : new File(javaHomeStr);
			}
		}
		File gradleUserHome = getGradleUserHomeFile();
		List<String> gradleArguments = preferences.getGradleArguments();
		List<String> gradleJvmArguments = preferences.getGradleJvmArguments();
		boolean offlineMode = preferences.isImportGradleOfflineEnabled();
		boolean overrideWorkspaceConfiguration = !(distribution instanceof WrapperGradleDistribution) || offlineMode || (gradleArguments != null && !gradleArguments.isEmpty()) || (gradleJvmArguments != null && !gradleJvmArguments.isEmpty())
				|| gradleUserHome != null || javaHome != null;
		// @formatter:off
		BuildConfiguration build = BuildConfiguration.forRootProjectDirectory(rootFolder.toFile())
				.overrideWorkspaceConfiguration(overrideWorkspaceConfiguration)
				.gradleDistribution(distribution)
				.javaHome(javaHome)
				.arguments(gradleArguments)
				.gradleUserHome(gradleUserHome)
				.jvmArguments(gradleJvmArguments)
				.offlineMode(offlineMode)
				.build();
		// @formatter:on
		return build;
	}

	public static boolean shouldSynchronize(File location) {
		for (IProject project : ProjectUtils.getGradleProjects()) {
			File projectDir = project.getLocation() == null ? null : project.getLocation().toFile();
			if (location.equals(projectDir)) {
				boolean shouldSynchronize = checkGradlePersistence(project, projectDir);
				if (shouldSynchronize) {
					JavaLanguageServerPlugin.logInfo(project.getName() + " was modified since last time the workspace was opened, must be synchronized");
				}
				return shouldSynchronize;
			}
		}
		JavaLanguageServerPlugin.logInfo("No previous Gradle project at " + location + ", it must be synchronized");
		return true;
	}

	private static boolean checkGradlePersistence(IProject project, File projectDir) {
		if (ProjectUtils.isJavaProject(project) && !project.getFile(IJavaProject.CLASSPATH_FILE_NAME).exists()) {
			return true;
		}
		boolean shouldSynchronize = true;
		PersistentModel model = CorePlugin.modelPersistence().loadModel(project);
		if (model.isPresent()) {
			File persistentFile = CorePlugin.getInstance().getStateLocation().append("project-preferences").append(project.getName()).toFile();
			if (persistentFile.exists()) {
				long modified = persistentFile.lastModified();
				if (projectDir.exists()) {
					File[] files = projectDir.listFiles(new FilenameFilter() {

						@Override
						public boolean accept(File dir, String name) {
							if (name != null && GradleBuildSupport.GRADLE_FILE_EXT.matcher(name).matches()) {
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
