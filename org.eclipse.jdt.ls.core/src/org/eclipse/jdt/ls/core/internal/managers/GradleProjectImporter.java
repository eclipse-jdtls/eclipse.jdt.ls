/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.eclipse.buildship.core.internal.DefaultGradleBuild;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.core.runtime.jobs.Job;
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
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
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
	public static final String GRADLE_WRAPPER_PROPERTIES_DESCRIPTOR = "gradle/wrapper/gradle-wrapper.properties";

	public static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.forVersion(GradleVersion.current().getVersion());

	public static final String IMPORTING_GRADLE_PROJECTS = "Importing Gradle project(s)";

	public static final String COMPATIBILITY_MARKER_ID = IConstants.PLUGIN_ID + ".gradlecompatibilityerrormarker";
	public static final String GRADLE_UPGRADE_WRAPPER_MARKER_ID = IConstants.PLUGIN_ID + ".gradleupgradewrappermarker";

	public static final String GRADLE_INVALID_TYPE_CODE_MESSAGE = "Exact exceptions are not shown due to an outdated Gradle version, please consider to update your Gradle version to " + GradleUtils.INVALID_TYPE_FIXED_VERSION + " and above.";

	public static final String GRADLE_MARKER_COLUMN_START = "gradleColumnStart";
	public static final String GRADLE_MARKER_COLUMN_END = "gradleColumnEnd";

	/**
	 * Nature id of the gradle build server project.
	 */
	public static final String GRADLE_BUILD_SERVER_NATURE = "com.microsoft.gradle.bs.importer.GradleBuildServerProjectNature";
	/**
	 * Builder id of the gradle build server.
	 */
	public static final String GRADLE_BUILD_SERVER_BUILDER_ID = "com.microsoft.gradle.bs.importer.builder.BuildServerBuilder";
	/**
	 * Builder id of the java problem checker, it's used to provide diagnostics during auto build for gradle build server projects.
	 */
	public static final String JAVA_PROBLEM_CHECKER_ID = "java.bs.JavaProblemChecker";

	private static final int GRADLE_RELATED = 0x00080000;
	private static final int INVALID_TYPE_CODE_ID = GRADLE_RELATED + 1;

	//@formatter:off
	public static final String GRADLE_WRAPPER_CHEKSUM_WARNING_TEMPLATE =
			"Security Warning! The gradle wrapper '@wrapper@' could be malicious. "
			+ "If you trust it, please add \n"
			+ "`{\"sha256\": \"@checksum@\","
			+ "\n\"allowed\": true}`"
			+ "\n to the `java.imports.gradle.wrapper.checksums` preference."
			+ ""
			.replaceAll("\n", System.lineSeparator());
	//@formatter:on

	/**
	 * A flag whether this importer is activated by manual selection mode.
	 */
	private boolean manualSelection = false;

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
				// The gradle build server has higher priority than buildship when importing gradle projects.
				// If code goes here, it means that the project cannot be imported by gradle build server.
				// The buildship should try to import it if it can. And the buildship importer should clean
				// up the configurations of the gradle build server in the project description if it has.
				if (!ProjectUtils.isGradleProject(project) && !ProjectUtils.hasNature(project, GRADLE_BUILD_SERVER_NATURE)) {
					String path = project.getLocation().toOSString();
					gradleDetector.addExclusions(path.replace("\\", "\\\\"));
				}
			}
			directories = gradleDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}

	@Override
	public boolean applies(Collection<IPath> buildFiles, IProgressMonitor monitor) {
		manualSelection = true;
		if (!getPreferences().isImportGradleEnabled()) {
			return false;
		}

		Collection<Path> configurationDirs = findProjectPathByConfigurationName(buildFiles, Arrays.asList(
			BUILD_GRADLE_DESCRIPTOR,
			SETTINGS_GRADLE_DESCRIPTOR,
			BUILD_GRADLE_KTS_DESCRIPTOR,
			SETTINGS_GRADLE_KTS_DESCRIPTOR
		), true /*includeNested*/);
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
		List<Path> directoriesToImport = new ArrayList<>(this.directories);
		if (manualSelection) {
			directoriesToImport = eliminateNestedPaths(directoriesToImport);
		}
		int projectSize = directoriesToImport.size();
		SubMonitor subMonitor = SubMonitor.convert(monitor, projectSize + 1);
		subMonitor.setTaskName(IMPORTING_GRADLE_PROJECTS);
		JavaLanguageServerPlugin.logInfo(IMPORTING_GRADLE_PROJECTS);
		subMonitor.worked(1);
		// run just once at the first project, assuming that all projects are using the same gradle version.
		inferGradleJavaHome(directoriesToImport.iterator().next(), monitor);
		MultiStatus compatibilityStatus = new MultiStatus(IConstants.PLUGIN_ID, -1, "Compatibility issue occurs when importing Gradle projects", null);
		MultiStatus gradleUpgradeWrapperStatus = new MultiStatus(IConstants.PLUGIN_ID, -1, "Gradle upgrade wrapper", null);
		for (Path directory : directoriesToImport) {
			IStatus importStatus = importDir(directory, subMonitor.newChild(1));
			if (isFailedStatus(importStatus) && importStatus instanceof GradleCompatibilityStatus) {
				compatibilityStatus.add(importStatus);
			} else if (GradleUtils.hasGradleInvalidTypeCodeException(importStatus, directory, monitor)) {
				gradleUpgradeWrapperStatus.add(new GradleUpgradeWrapperStatus(importStatus, GRADLE_INVALID_TYPE_CODE_MESSAGE, directory.toUri().toString()));
			}
			checkWrapperChecksum(directory);
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
			gradleProject.deleteMarkers(COMPATIBILITY_MARKER_ID, true, IResource.DEPTH_ZERO);
			gradleProject.deleteMarkers(GRADLE_UPGRADE_WRAPPER_MARKER_ID, true, IResource.DEPTH_INFINITE);
		}
		for (IStatus status : compatibilityStatus.getChildren()) {
			// only report first compatibility issue
			JavaLanguageServerPlugin.log(new Status(IStatus.ERROR, status.getPlugin(), status.getMessage(), status.getException()));
			GradleCompatibilityStatus gradleStatus = ((GradleCompatibilityStatus) status);
			for (IProject gradleProject : ProjectUtils.getGradleProjects()) {
				if (URIUtil.sameURI(URI.create(JDTUtils.getFileURI(gradleProject)), URI.create(gradleStatus.getProjectUri()))) {
					ResourceUtils.createErrorMarker(gradleProject, gradleStatus, COMPATIBILITY_MARKER_ID);
				}
			}
			if (JavaLanguageServerPlugin.getProjectsManager() != null && JavaLanguageServerPlugin.getProjectsManager().getConnection() != null) {
				GradleCompatibilityInfo info = new GradleCompatibilityInfo(gradleStatus.getProjectUri(), gradleStatus.getMessage(), gradleStatus.getHighestJavaVersion(), GradleVersion.current().getVersion());
				EventNotification notification = new EventNotification().withType(EventType.IncompatibleGradleJdkIssue).withData(info);
				JavaLanguageServerPlugin.getProjectsManager().getConnection().sendEventNotification(notification);
			}
			break;
		}
		for (IStatus status : gradleUpgradeWrapperStatus.getChildren()) {
			// only report first marker
			GradleUpgradeWrapperStatus gradleStatus = ((GradleUpgradeWrapperStatus) status);
			for (IProject gradleProject : ProjectUtils.getGradleProjects()) {
				if (!URIUtil.sameURI(URI.create(JDTUtils.getFileURI(gradleProject)), URI.create(gradleStatus.getProjectUri()))) {
					continue;
				}
				IFile wrapperProperties = gradleProject.getFile(GRADLE_WRAPPER_PROPERTIES_DESCRIPTOR);
				if (!wrapperProperties.exists()) {
					continue;
				}
				try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(wrapperProperties.getContents()))){
					String line;
					while ((line = reader.readLine()) != null) {
						if (line.contains("distributionUrl")) {
							IMarker marker = ResourceUtils.createWarningMarker(GRADLE_UPGRADE_WRAPPER_MARKER_ID, wrapperProperties, GRADLE_INVALID_TYPE_CODE_MESSAGE, INVALID_TYPE_CODE_ID, reader.getLineNumber());
							marker.setAttribute(GRADLE_MARKER_COLUMN_START, 0);
							marker.setAttribute(GRADLE_MARKER_COLUMN_END, line.length());
							UpgradeGradleWrapperInfo info = new UpgradeGradleWrapperInfo(gradleStatus.getProjectUri(), GRADLE_INVALID_TYPE_CODE_MESSAGE, GradleVersion.current().getVersion());
							EventNotification notification = new EventNotification().withType(EventType.UpgradeGradleWrapper).withData(info);
							JavaLanguageServerPlugin.getProjectsManager().getConnection().sendEventNotification(notification);
							break;
						}
					}
				} catch (IOException e) {
					// Do nothing
				}
			}
			break;
		}
		// https://github.com/redhat-developer/vscode-java/issues/3904
		// skip synchronizeAnnotationProcessingConfiguration  if not required
		boolean shouldSynchronize = false;
		for (Path directory : directoriesToImport) {
			File location = directory.toFile();
			if (shouldSynchronize(location)) {
				shouldSynchronize = true;
				break;
			}
		}
		if (shouldSynchronize) {
			GradleUtils.synchronizeAnnotationProcessingConfiguration(subMonitor);
		}
		eliminateBuildServerFootprint(monitor);
		subMonitor.done();
	}

	private void inferGradleJavaHome(Path projectFolder, IProgressMonitor monitor) {
		if (StringUtils.isNotBlank(getPreferences().getGradleJavaHome())) {
			return;
		}

		File javaHome = getJavaHome(getPreferences());
		String javaVersion;
		if (javaHome == null) {
			javaVersion = System.getProperty("java.version");
		} else {
			StandardVMType type = new StandardVMType();
			javaVersion = type.readReleaseVersion(javaHome);
		}
		if (StringUtils.isBlank(javaVersion)) {
			// return if failed to get java version.
			return;
		}
		GradleVersion gradleVersion = GradleUtils.getGradleVersion(projectFolder, monitor);
		if (GradleUtils.isIncompatible(gradleVersion, javaVersion)) {
			String highestJavaVersion = GradleUtils.getHighestSupportedJava(gradleVersion);
			File javaHomeFile = GradleUtils.getJdkToLaunchDaemon(highestJavaVersion);
			if (javaHomeFile != null) {
				getPreferences().setGradleJavaHome(javaHomeFile.getAbsolutePath());
			}
		}
	}

	private IStatus importDir(Path projectFolder, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		return startSynchronization(projectFolder, monitor);
	}

	public static void checkWrapperChecksum(Path rootFolder) {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager == null) {
			return;
		}

		if (Files.exists(rootFolder.resolve(WrapperValidator.GRADLE_WRAPPER_JAR))) {
			Job checksumJob = new Job("Validating Gradle wrapper checksum...") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					WrapperValidator validator = new WrapperValidator();
					try {
						ValidationResult result = validator.checkWrapper(rootFolder.toFile().getAbsolutePath());
						if (!result.isValid() && !WrapperValidator.contains(result.getChecksum())) {
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
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
					return Status.OK_STATUS;
				}
			};
			checksumJob.setPriority(Job.LONG);
			checksumJob.schedule();
		}
	}

	public static GradleDistribution getGradleDistribution(Path rootFolder) {
		Preferences preferences = getPreferences();
		if (preferences.isGradleWrapperEnabled() && Files.exists(rootFolder.resolve("gradle/wrapper/gradle-wrapper.properties"))) {
			return GradleDistribution.fromBuild();
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
					if (GradleUtils.isIncompatible(GradleVersion.version(gradleVersion), javaVersion)) {
						Path projectName = projectFolder.getName(projectFolder.getNameCount() - 1);
						String message = String.format("Can't use Java %s and Gradle %s to import Gradle project %s.", javaVersion, gradleVersion, projectName.toString());
						String highestJavaVersion = GradleUtils.getHighestSupportedJava(GradleVersion.version(gradleVersion));
						return new GradleCompatibilityStatus(resultStatus, message, projectFolder.toUri().toString(), highestJavaVersion);
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
		return getBuildConfiguration(rootFolder, false);
	}

	public static BuildConfiguration getBuildConfiguration(Path rootFolder, boolean noDaemon) {
		GradleDistribution distribution = getGradleDistribution(rootFolder);
		Preferences preferences = getPreferences();
		File javaHome = getJavaHome(preferences);
		File gradleUserHome = getGradleUserHomeFile();
		List<String> gradleArguments = new ArrayList<>();
		// https://github.com/microsoft/vscode-gradle/issues/1519
		//if (noDaemon) {
		//	gradleArguments.add("--no-daemon");
		//}
		gradleArguments.addAll(getGradleInitScriptArgs());
		gradleArguments.addAll(preferences.getGradleArguments());
		List<String> gradleJvmArguments = preferences.getGradleJvmArguments();
		boolean offlineMode = preferences.isImportGradleOfflineEnabled();
		boolean autoSync = preferences.getUpdateBuildConfigurationStatus().equals(FeatureStatus.automatic);
		boolean overrideWorkspaceConfiguration = !(distribution instanceof WrapperGradleDistribution) || offlineMode || (gradleArguments != null && !gradleArguments.isEmpty()) || (gradleJvmArguments != null && !gradleJvmArguments.isEmpty())
				|| gradleUserHome != null || javaHome != null || autoSync;
		// @formatter:off
		BuildConfiguration build = BuildConfiguration.forRootProjectDirectory(rootFolder.toFile())
				.overrideWorkspaceConfiguration(overrideWorkspaceConfiguration)
				.gradleDistribution(distribution)
				.javaHome(javaHome)
				.arguments(gradleArguments)
				.gradleUserHome(gradleUserHome)
				.jvmArguments(gradleJvmArguments)
				.offlineMode(offlineMode)
				.autoSync(autoSync)
				.build();
		// @formatter:on
		return build;
	}

	static boolean useDefaultVM() {
		File javaHome = getGradleJavaHomeFile();
		if (javaHome == null) {
			IVMInstall javaDefaultRuntime = JavaRuntime.getDefaultVMInstall();
			return javaDefaultRuntime != null
				&& javaDefaultRuntime.getVMRunner(ILaunchManager.RUN_MODE) != null;
		}

		return false;
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
					File[] files = projectDir.listFiles((FilenameFilter) (dir, name) -> {
						if (name != null && GradleBuildSupport.GRADLE_FILE_EXT.matcher(name).matches()) {
							return new File(dir, name).lastModified() > modified;
						}
						return false;
					});
					shouldSynchronize = files != null && files.length > 0;
				}
			}
		}
		return shouldSynchronize;
	}

	/**
	 * update the gradle wrapper to the given version
	 *
	 * @param projectUri
	 *                          uri of the project
	 * @param gradleVersion
	 *                          the target gradle version
	 * @param monitor
	 *                          the progress monitor
	 * @return the path to the new gradle-wrapper.properties file
	 *
	 *         Upgrade the Gradle version in given project uri. The method includes
	 *         two steps: modify the gradle properties file and execute "wrapper"
	 *         task.
	 *
	 *         If the GradleBuild related to the project exists, we will use that
	 *         instance to get root project directory, and execute wrapper task.
	 *         Otherwise, we will directly regard the given uri as project uri, and
	 *         create a new GradleBuild for that project uri and execute wrapper
	 *         task.
	 */
	public static String upgradeGradleVersion(String projectUri, String gradleVersion, IProgressMonitor monitor) {
		String newDistributionUrl = String.format("https://services.gradle.org/distributions/gradle-%s-bin.zip", gradleVersion);
		Path projectFolder = Paths.get(URI.create(projectUri));
		IProject project = ProjectUtils.getProjectFromUri(projectUri);
		Optional<GradleBuild> build = GradleCore.getWorkspace().getBuild(project);
		GradleBuild gradleBuild = null;
		if (!build.isEmpty()) {
			gradleBuild = build.get();
			if (gradleBuild instanceof DefaultGradleBuild) {
				projectFolder = ((DefaultGradleBuild) gradleBuild).getBuildConfig().getRootProjectDirectory().toPath();
			}
		}
		File propertiesFile = projectFolder.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties").toFile();
		Properties properties = new Properties();
		if (propertiesFile.exists()) {
			try (FileInputStream stream = new FileInputStream(propertiesFile)) {
				properties.load(stream);
				properties.setProperty("distributionUrl", newDistributionUrl);
			} catch (IOException e) {
				return null;
			}
		} else {
			properties.setProperty("distributionBase", "GRADLE_USER_HOME");
			properties.setProperty("distributionPath", "wrapper/dists");
			properties.setProperty("distributionUrl", newDistributionUrl);
			properties.setProperty("zipStoreBase", "GRADLE_USER_HOME");
			properties.setProperty("zipStorePath", "wrapper/dists");
		}
		if (monitor.isCanceled()) {
			return null;
		}
		try {
			properties.store(new FileOutputStream(propertiesFile), null);
		} catch (Exception e) {
			return null;
		}
		try {
			if (gradleBuild == null) {
				gradleBuild = GradleCore.getWorkspace().createBuild(getBuildConfiguration(projectFolder));
			}
			gradleBuild.withConnection(connection -> {
				connection.newBuild().forTasks("wrapper").run();
				return null;
			}, monitor);
		} catch (Exception e) {
			// Do nothing
		}

		return propertiesFile.getAbsolutePath();
	}

	/**
	 * Get Gradle init script arguments
	 */
	private static List<String> getGradleInitScriptArgs() {
		List<String> args = new LinkedList<>();

		// Add init script of jdt.ls
		File initScript = GradleUtils.getGradleInitScript("/gradle/init/init.gradle");
		addInitScriptToArgs(initScript, args);

		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager == null) {
			return args;
		}

		// Add init script of protobuf support
		if (preferencesManager.getPreferences().isProtobufSupportEnabled()) {
			File protobufInitScript =  GradleUtils.getGradleInitScript("/gradle/protobuf/init.gradle");
			addInitScriptToArgs(protobufInitScript, args);
		}

		// Add init script of android support
		if (preferencesManager.getPreferences().isAndroidSupportEnabled()) {
			File androidInitScript =  GradleUtils.getGradleInitScript("/gradle/android/init.gradle");
			addInitScriptToArgs(androidInitScript, args);
		}

		// Add init script of aspectj support
		if (preferencesManager.getPreferences().isAspectjSupportEnabled()) {
			File aspectjInitScript = GradleUtils.getGradleInitScript("/gradle/aspectj/init.gradle");
			addInitScriptToArgs(aspectjInitScript, args);
		}

		return args;
	}

	private static void addInitScriptToArgs(File initScript, List<String> args) {
		if (initScript != null && initScript.exists() && initScript.length() > 0) {
			args.add("--init-script");
			args.add(initScript.getAbsolutePath());
		}
	}

	@Override
	public void reset() {
	}

	public static boolean isFailedStatus(IStatus status) {
		return status != null && !status.isOK() && status.getException() != null;
	}

	/**
	 * Eliminate the footprint of the Gradle build server projects. This is necessary
	 * cleanup in case that user uninstalls/disables the gradle extension.
	 */
	private static void eliminateBuildServerFootprint(IProgressMonitor monitor) {
		for (IProject project : ProjectUtils.getAllProjects()) {
			try {
				if (ProjectUtils.hasNature(project, GRADLE_BUILD_SERVER_NATURE)) {
					GradleUtils.removeConfigurationFromProjectDescription(
						project,
						new HashSet<>(Arrays.asList(GRADLE_BUILD_SERVER_NATURE)),
						new HashSet<>(Arrays.asList(GRADLE_BUILD_SERVER_BUILDER_ID, JAVA_PROBLEM_CHECKER_ID)),
						monitor
					);
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to remove Gradle build server configuration from project description", e);
			}
		}
	}

	public class GradleCompatibilityStatus extends Status {

		private String projectUri;
		private String highestJavaVersion;

		public GradleCompatibilityStatus(IStatus status, String message, String projectUri, String highestJavaVersion) {
			super(status.getSeverity(), status.getPlugin(), status.getCode(), message, status.getException());
			this.projectUri = projectUri;
			this.highestJavaVersion = highestJavaVersion;
		}

		public String getProjectUri() {
			return this.projectUri;
		}

		public String getHighestJavaVersion() {
			return this.highestJavaVersion;
		}
	}

	public class GradleUpgradeWrapperStatus extends Status {

		private String projectUri;

		public GradleUpgradeWrapperStatus(IStatus status, String message, String projectUri) {
			super(status.getSeverity(), status.getPlugin(), status.getCode(), message, status.getException());
			this.projectUri = projectUri;
		}

		public String getProjectUri() {
			return this.projectUri;
		}
	}

	private class GradleCompatibilityInfo {

		private String projectUri;
		private String message;
		private String highestJavaVersion;
		private String recommendedGradleVersion;

		public GradleCompatibilityInfo(String projectPath, String message, String highestJavaVersion, String recommendedGradleVersion) {
			this.projectUri = projectPath;
			this.message = message;
			this.highestJavaVersion = highestJavaVersion;
			this.recommendedGradleVersion = recommendedGradleVersion;
		}
	}

	private class UpgradeGradleWrapperInfo {
		private String projectUri;
		private String message;
		private String recommendedGradleVersion;

		public UpgradeGradleWrapperInfo(String projectUri, String message, String recommendedGradleVersion) {
			this.projectUri = projectUri;
			this.message = message;
			this.recommendedGradleVersion = recommendedGradleVersion;
		}
	}
}
