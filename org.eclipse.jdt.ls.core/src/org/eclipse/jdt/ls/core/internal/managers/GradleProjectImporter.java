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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.SynchronizationResult;
import org.eclipse.buildship.core.WrapperGradleDistribution;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.internal.gradle.checksums.ValidationResult;
import org.eclipse.jdt.ls.internal.gradle.checksums.WrapperValidator;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.GradleEnvironment;

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

	public static final String COMPATIBILITY_MARKER_ID = IConstants.PLUGIN_ID + ".gradlecompatibilityerrormarker";

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

	@Override
	public boolean applies(Collection<IPath> buildFiles, IProgressMonitor monitor) {
		if (!getPreferences().isImportGradleEnabled()) {
			return false;
		}

		Collection<Path> configurationDirs = findProjectPathByConfigurationName(buildFiles, Arrays.asList(
			BUILD_GRADLE_DESCRIPTOR,
			SETTINGS_GRADLE_DESCRIPTOR,
			BUILD_GRADLE_KTS_DESCRIPTOR,
			SETTINGS_GRADLE_KTS_DESCRIPTOR
		), false /*includeNested*/);
		if (configurationDirs == null || configurationDirs.isEmpty()) {
			return false;
		}

		Set<Path> noneGradleProjectPaths = new HashSet<>();
		for (IProject project : ProjectUtils.getAllProjects()) {
			if (!ProjectUtils.isGradleProject(project)) {
				noneGradleProjectPaths.add(project.getLocation().toFile().toPath());
			}
		}

		this.directories = configurationDirs.stream()
			.filter(d -> {
				boolean folderIsImported = noneGradleProjectPaths.stream().anyMatch(path -> {
					return path.compareTo(d) == 0;
				});
				return !folderIsImported;
			})
			.collect(Collectors.toList());

		return !this.directories.isEmpty();
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
		MultiStatus compatibilityStatus = new MultiStatus(IConstants.PLUGIN_ID, -1, "Compatibility issue occurs when importing Gradle projects", null);
		for (Path directory : directories) {
			IStatus importStatus = importDir(directory, subMonitor.newChild(1));
			if (isFailedStatus(importStatus) && importStatus instanceof GradleCompatibilityStatus) {
				compatibilityStatus.add(importStatus);
			}
		}
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
		for (IProject gradleProject : ProjectUtils.getGradleProjects()) {
			gradleProject.deleteMarkers(COMPATIBILITY_MARKER_ID, true, IResource.DEPTH_INFINITE);
		}
		if (compatibilityStatus.getChildren().length > 0) {
			for (IStatus status : compatibilityStatus.getChildren()) {
				// only report first compatibility issue
				GradleCompatibilityStatus gradleStatus = ((GradleCompatibilityStatus) status);
				for (IProject gradleProject : ProjectUtils.getGradleProjects()) {
					if (JDTUtils.getFileURI(gradleProject).equals(gradleStatus.getUri())) {
						ResourceUtils.createMarker(gradleProject, gradleStatus, COMPATIBILITY_MARKER_ID);
					}
				}
				GradleCompatibilityInfo info = new GradleCompatibilityInfo(Paths.get(URI.create(gradleStatus.uri)).toString(), gradleStatus.getMessage(), gradleStatus.getHighestJavaVersion(), GradleCompatibilityChecker.CURRENT_GRADLE);
				EventNotification notification = new EventNotification().withType(EventType.IncompatibleGradleJdkIssue).withData(info);
				JavaLanguageServerPlugin.getProjectsManager().getConnection().sendEventNotification(notification);
				break;
			}
		}
		subMonitor.done();
	}

	private IStatus importDir(Path projectFolder, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		return startSynchronization(projectFolder, monitor);
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

	protected IStatus startSynchronization(Path projectFolder, IProgressMonitor monitor) {
		File location = projectFolder.toFile();
		boolean shouldSynchronize = shouldSynchronize(location);
		if (shouldSynchronize) {
			BuildConfiguration build = getBuildConfiguration(projectFolder);
			GradleBuild gradleBuild = GradleCore.getWorkspace().createBuild(build);
			SynchronizationResult result = gradleBuild.synchronize(monitor);
			IStatus resultStatus = result.getStatus();
			if (isFailedStatus(resultStatus)) {
				try {
					BuildEnvironment environment = gradleBuild.withConnection(connection -> connection.getModel(BuildEnvironment.class), monitor);
					GradleEnvironment gradleEnvironment = environment.getGradle();
					String gradleVersion = gradleEnvironment.getGradleVersion();
					File javaHome = getJavaHome(getPreferences());
					String javaVersion;
					if (javaHome == null) {
						javaVersion = System.getProperty("java.version");
					} else {
						StandardVMType type = new StandardVMType();
						javaVersion = type.readReleaseVersion(javaHome);
					}
					if (GradleCompatibilityChecker.isIncompatible(GradleVersion.version(gradleVersion), javaVersion)) {
						URI uri = projectFolder.toUri();
						Path path = Paths.get(uri);
						Path projectName = path.getName(path.getNameCount() - 1);
						String message = String.format("Can't use Java %s and Gradle %s to import Gradle project %s.", javaVersion, gradleVersion, projectName.toString());
						String highestJavaVersion = GradleCompatibilityChecker.getHighestSupportedJava(GradleVersion.version(gradleVersion));
						return new GradleCompatibilityStatus(resultStatus, message, uri.toString(), highestJavaVersion);
					}
				} catch (Exception e) {
					// Do nothing
				}
			}
			return resultStatus;
		}
		return Status.OK_STATUS;
	}

	public static BuildConfiguration getBuildConfiguration(Path rootFolder) {
		GradleDistribution distribution = getGradleDistribution(rootFolder);
		Preferences preferences = getPreferences();
		File javaHome = getJavaHome(preferences);
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

	private static File getJavaHome(Preferences preferences) {
		File javaHome = getGradleJavaHomeFile();
		if (javaHome == null) {
			IVMInstall javaDefaultRuntime = JavaRuntime.getDefaultVMInstall();
			if (javaDefaultRuntime != null && javaDefaultRuntime.getVMRunner(ILaunchManager.RUN_MODE) != null) {
				javaHome = javaDefaultRuntime.getInstallLocation();
			} else {
				String javaHomeStr = preferences.getJavaHome();
				javaHome = javaHomeStr == null ? null : new File(javaHomeStr);
			}
		}
		return javaHome;
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

	public static boolean upgradeGradleVersion(String projectDir, IProgressMonitor monitor) {
		String newDistributionUrl = String.format("https://services.gradle.org/distributions/gradle-%s-bin.zip", GradleCompatibilityChecker.CURRENT_GRADLE);
		Path projectFolder = Paths.get(projectDir);
		File propertiesFile = projectFolder.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties").toFile();
		Properties properties = new Properties();
		if (propertiesFile.exists()) {
			try (FileInputStream stream = new FileInputStream(propertiesFile)) {
				properties.load(stream);
				properties.setProperty("distributionUrl", newDistributionUrl);
			} catch (IOException e) {
				return false;
			}
		} else {
			properties.setProperty("distributionBase", "GRADLE_USER_HOME");
			properties.setProperty("distributionPath", "wrapper/dists");
			properties.setProperty("distributionUrl", newDistributionUrl);
			properties.setProperty("zipStoreBase", "GRADLE_USER_HOME");
			properties.setProperty("zipStorePath", "wrapper/dists");
		}
		try {
			properties.store(new FileOutputStream(propertiesFile), null);
		} catch (Exception e) {
			return false;
		}
		BuildConfiguration build = getBuildConfiguration(projectFolder);
		GradleBuild gradleBuild = GradleCore.getWorkspace().createBuild(build);
		try {
			gradleBuild.withConnection(connection -> {
				connection.newBuild().forTasks("wrapper").run();
				return null;
			}, monitor);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	@Override
	public void reset() {
	}

	private static boolean isFailedStatus(IStatus status) {
		return status != null && !status.isOK() && status.getException() != null;
	}

	public class GradleCompatibilityStatus extends Status {

		private String uri;
		private String highestJavaVersion;

		public GradleCompatibilityStatus(IStatus status, String message, String uri, String highestJavaVersion) {
			super(status.getSeverity(), status.getPlugin(), status.getCode(), message, status.getException());
			this.uri = uri;
			this.highestJavaVersion = highestJavaVersion;
		}

		public String getUri() {
			return this.uri;
		}

		public String getHighestJavaVersion() {
			return this.highestJavaVersion;
		}
	}

	private class GradleCompatibilityInfo implements Serializable {

		private String projectUri;
		private String message;
		private String highestJavaVersion;
		private String recommendedGradleVersion;

		public GradleCompatibilityInfo(String projectUri, String message, String highestJavaVersion, String recommendedGradleVersion) {
			this.projectUri = projectUri;
			this.message = message;
			this.highestJavaVersion = highestJavaVersion;
			this.recommendedGradleVersion = recommendedGradleVersion;
		}
	}
}
