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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.DefaultGradleBuild;
import org.eclipse.buildship.core.internal.launch.GradleClasspathProvider;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.buildship.core.internal.util.file.FileUtils;
import org.eclipse.buildship.core.internal.workspace.FetchStrategy;
import org.eclipse.buildship.core.internal.workspace.InternalGradleBuild;
import org.eclipse.buildship.core.internal.workspace.WorkbenchShutdownEvent;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.apt.core.util.AptConfig;
import org.eclipse.jdt.apt.core.util.IFactoryPath;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.IPreferencesChangeListener;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.eclipse.EclipseProject;

/**
 * @author Fred Bricon
 *
 */
public class GradleBuildSupport implements IBuildSupport {

	private static final String GRADLE = "Gradle";
	private static final String GROOVY = "Groovy";
	private static final String GROOVY_EXTENSION = ".groovy";
	private static final String COMPILE_TEST_GROOVY = "compileTestGroovy";
	private static final String COMPILE_GROOVY = "compileGroovy";
	private static Pattern GROOVY_PATTERN = Pattern.compile("(?m)^(?<file>.*\\.groovy):\\s*(?<line>\\d+):\\s*(?<message>.*)\\s+@\\s+line\\s+\\d+,\\s+column\\s+(?<col>\\d+)\\.");
	private static final String KOTLIN = "Kotlin";
	private static final String KOTLIN_EXTENSION = ".kt";
	private static final String COMPILE_TEST_KOTLIN = "compileTestKotlin";
	private static final String COMPILE_KOTLIN = "compileKotlin";
	private static final String SCALA = "Scala";
	private static final String SCALA_EXTENSION = ".scala";
	private static final String COMPILE_TEST_SCALA = "compileTestScala";
	private static final String COMPILE_SCALA = "compileScala";
	private static Pattern KOTLIN_PATTERN = Pattern.compile("(?m)^(?:(?<type>[ew]): )?(?:file:///)?(?<file>.*\\.(?:kt|kts|aj)):(?<line>\\d+):(?<col>\\d+)?\\s*(?<message>.*)$");
	private static Pattern SCALA_PATTERN = Pattern.compile("(?m)^(?:(?:\\[(?<type>\\w+)\\]\\s+)?(?<file>/?[^:]+(?:\\.scala|\\.java)):(?<line>\\d+):(?:(?<col>\\d+):)?\\s*(?<message>.*))$");
	private static final String ASPECTJ = "AspectJ";
	private static final String ASPECTJ_EXTENSION = ".aj";
	private static final String COMPILE_TEST_ASPECTJ = "compileTestAspectj";
	private static final String COMPILE_ASPECTJ = "compileAspectj";
	private static Pattern ASPECTJ_PATTERN = Pattern.compile("(?m)^(?<file>.*\\.aj):(?<line>\\d+)\\s+\\[(?<type>error|warning)\\]\\s+(?<message>.*)\\R(?<source>.*)\\R(?<indent>\\s*)\\^");
	private static final String UNKNOWN = "Unknown";
	private static final List<String> includeTasks = Arrays.asList(COMPILE_KOTLIN, COMPILE_TEST_KOTLIN, COMPILE_GROOVY, COMPILE_TEST_GROOVY, COMPILE_ASPECTJ, COMPILE_TEST_ASPECTJ, COMPILE_SCALA, COMPILE_TEST_SCALA);
	public static final Pattern GRADLE_FILE_EXT = Pattern.compile("^.*\\.gradle(\\.kts)?$");
	public static final String GRADLE_PROPERTIES = "gradle.properties";
	public static final List<String> WATCH_FILE_PATTERNS = Arrays.asList("**/*.gradle", "**/*.gradle.kts", "**/gradle.properties");
	public static final String UNSUPPORTED_ON_GRADLE = "Unsupported operation. Please use build.gradle file to manage the source directories of gradle project.";
	public static final Path CONTAINER_PATH = new Path("org.eclipse.buildship.core.gradleclasspathcontainer");
	public static String[] EXCLUSIONS_PATTERNS = { "**" };
	private static IPreferencesChangeListener listener = new GradlePreferenceChangeListener();

	/**
	 * The relative path where store the sources generated by annotation processors
	 */
	private static final String GENERATED_SOURCES_PATH = "bin/generated-sources/annotations";
	/**
	 * The relative path where store the test sources generated by annotation processors
	 */
	private static final String GENERATED_TEST_SOURCES_PATH = "bin/generated-test-sources/annotations";

	@Override
	public boolean applies(IProject project) {
		return ProjectUtils.isGradleProject(project);
	}

	@Override
	public void update(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {
		if (!applies(project)) {
			return;
		}
		JavaLanguageServerPlugin.debugTrace("Starting Gradle update for " + project.getName());
		Optional<GradleBuild> build = GradleCore.getWorkspace().getBuild(project);
		if (build.isPresent()) {
			GradleBuild gradleBuild = build.get();
			boolean isRoot = isRoot(project, gradleBuild, monitor);
			if (force && isRoot) {
				String projectPath = project.getLocation().toFile().getAbsolutePath();
				BuildConfiguration buildConfiguration = GradleProjectImporter.getBuildConfiguration(Paths.get(projectPath));
				gradleBuild = GradleCore.getWorkspace().createBuild(buildConfiguration);
			}
			File buildFile = project.getFile(GradleProjectImporter.BUILD_GRADLE_DESCRIPTOR).getLocation().toFile();
			File settingsFile = project.getFile(GradleProjectImporter.SETTINGS_GRADLE_DESCRIPTOR).getLocation().toFile();
			File buildKtsFile = project.getFile(GradleProjectImporter.BUILD_GRADLE_KTS_DESCRIPTOR).getLocation().toFile();
			File settingsKtsFile = project.getFile(GradleProjectImporter.SETTINGS_GRADLE_KTS_DESCRIPTOR).getLocation().toFile();
			boolean shouldUpdate = (buildFile.exists() && JavaLanguageServerPlugin.getDigestStore().updateDigest(buildFile.toPath()))
					|| (settingsFile.exists() && JavaLanguageServerPlugin.getDigestStore().updateDigest(settingsFile.toPath()))
					|| (buildKtsFile.exists() && JavaLanguageServerPlugin.getDigestStore().updateDigest(buildKtsFile.toPath()))
					|| (settingsKtsFile.exists() && JavaLanguageServerPlugin.getDigestStore().updateDigest(settingsKtsFile.toPath()));
			// https://github.com/redhat-developer/vscode-java/issues/3893
			shouldUpdate |= isRoot;
			if (!shouldUpdate) {
				if (force && gradleBuild instanceof DefaultGradleBuild defaultGradleBuild) {
					org.eclipse.buildship.core.internal.configuration.BuildConfiguration gradleConfig = defaultGradleBuild.getBuildConfig();
					if (!gradleConfig.isAutoSync()) {
						shouldUpdate = true;
					}
				}
			}
			if (shouldUpdate) {
				gradleBuild.synchronize(monitor);
				cleanScalaProjects(monitor);
				syncAnnotationProcessingConfiguration(gradleBuild, monitor);
			}
		}
	}

	public static void syncAnnotationProcessingConfiguration(IProject project, IProgressMonitor monitor) {
		Optional<GradleBuild> build = GradleCore.getWorkspace().getBuild(project);
		if (build.isPresent()) {
			syncAnnotationProcessingConfiguration(build.get(), monitor);
		}
	}

	/**
	 * Synchronize the annotation processing configurations to JDT APT.
	 * @param gradleBuild The GradleBuild instance.
	 * @param monitor progress monitor.
	 */
	@SuppressWarnings("unchecked")
	public static void syncAnnotationProcessingConfiguration(GradleBuild gradleBuild, IProgressMonitor monitor) {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager == null) {
			return;
		}
		if (!preferencesManager.getPreferences().isGradleAnnotationProcessingEnabled()) {
			return;
		}

		File initScript = GradleUtils.getGradleInitScript("/gradle/apt/init.gradle");
		if (initScript == null) {
			return;
		}

		Map<File, Map<String, Object>> model = null;
		try {
			model = gradleBuild.withConnection(connection -> {
				return connection.model(Map.class).withArguments("--init-script", initScript.getAbsolutePath()).get();
			}, monitor);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}

		if (model == null) {
			return;
		}

		// Even reloading a sub project will get the annotation processors for all
		// the projects, due to the Gradle custom model api's limitation.
		for (IProject project : ProjectUtils.getGradleProjects()) {
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject == null) {
				continue;
			}

			Map<String, Object> apConfigurations = model.get(project.getLocation().toFile());
			if (apConfigurations == null) {
				continue;
			}

			if (apConfigurations.isEmpty()) {
				disableApt(javaProject);
				continue;
			}

			Set<File> processors = getProcessors(apConfigurations);
			if (processors.isEmpty()) {
				continue;
			}

			AptConfig.setGenSrcDir(javaProject, GENERATED_SOURCES_PATH);
			AptConfig.setGenTestSrcDir(javaProject, GENERATED_TEST_SOURCES_PATH);

			if (!AptConfig.isEnabled(javaProject)) {
				// setEnabled will ensure the output folder existing on disk, that why
				// we set enabled status after the output folder is set to APT, which can
				// avoid generating default output folder.
				AptConfig.setEnabled(javaProject, true);
			}

			IFactoryPath factoryPath = AptConfig.getDefaultFactoryPath(javaProject);
			for(File processor : processors){
				factoryPath.addExternalJar(processor);
			}

			try {
				AptConfig.setFactoryPath(javaProject, factoryPath);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}

			List<Object> compilerArgs = getCompilerArgs(apConfigurations);
			Map<String, String> newOptions = GradleUtils.parseProcessorOptions(compilerArgs);
			Map<String, String> currentOptions = AptConfig.getRawProcessorOptions(javaProject);
			if(!currentOptions.equals(newOptions)) {
				AptConfig.setProcessorOptions(newOptions, javaProject);
			}
		}
	}

	private boolean isRoot(IProject project, GradleBuild gradleBuild, IProgressMonitor monitor) {
		if (gradleBuild instanceof InternalGradleBuild internalGradleBuild) {
			CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
			Map<String, EclipseProject> eclipseProjects = internalGradleBuild.getModelProvider().fetchModels(EclipseProject.class, FetchStrategy.LOAD_IF_NOT_CACHED, tokenSource, monitor);
			File projectDirectory = project.getLocation().toFile();
			for (EclipseProject eclipseProject : eclipseProjects.values()) {
				File eclipseProjectDirectory = eclipseProject.getProjectDirectory();
				if (eclipseProjectDirectory.equals(projectDirectory)) {
					return eclipseProject.getParent() == null;
				}
			}
		}
		return false;
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		if (resource != null && resource.getType() == IResource.FILE && isBuildLikeFileName(resource.getName())
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
		return GRADLE_FILE_EXT.matcher(fileName).matches() || fileName.equals(GRADLE_PROPERTIES);
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

	@Override
	public boolean useDefaultVM(IProject project, IVMInstall defaultVM) {
		return GradleProjectImporter.useDefaultVM();
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

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IBuildSupport.registerPreferencesChangeListener(PreferenceManager)
	 */
	@Override
	public void registerPreferencesChangeListener(PreferenceManager preferenceManager) throws CoreException {
		preferenceManager.addPreferencesChangeListener(listener);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IBuildSupport.unregisterPreferencesChangeListener(PreferenceManager)
	 */
	@Override
	public void unregisterPreferencesChangeListener(PreferenceManager preferenceManager) throws CoreException {
		preferenceManager.removePreferencesChangeListener(listener);
	}

	@Override
	public String buildToolName() {
		return GRADLE;
	}

	@Override
	public String unsupportedOperationMessage() {
		return UNSUPPORTED_ON_GRADLE;
	}

	@Override
	public boolean hasSpecificDeleteProjectLogic() {
		return true;
	}

	@Override
	public void deleteInvalidProjects(Collection<IPath> rootPaths, ArrayList<IProject> deleteProjectCandates, IProgressMonitor monitor) {
		List<IProject> validGradleProjects = new ArrayList<>();
		List<IProject> suspiciousGradleProjects = new ArrayList<>();

		for (IProject project : deleteProjectCandates) {
			if (applies(project)) {
				if (ResourceUtils.isContainedIn(project.getLocation(), rootPaths)) {
					validGradleProjects.add(project);
				} else {
					suspiciousGradleProjects.add(project);
				}
			}

		}

		List<IProject> unrelatedProjects = findUnrelatedGradleProjects(suspiciousGradleProjects, validGradleProjects);
		unrelatedProjects.forEach((project) -> {
			try {
				project.delete(false, true, monitor);
			} catch (CoreException e1) {
				JavaLanguageServerPlugin.logException(e1.getMessage(), e1);
			}
		});
	}

	/**
	 * Find those gradle projects not referenced by any gradle project in the
	 * current workspace.
	 */
	private List<IProject> findUnrelatedGradleProjects(List<IProject> suspiciousProjects, List<IProject> validProjects) {
		suspiciousProjects.sort((IProject p1, IProject p2) -> p1.getLocation().toOSString().length() - p2.getLocation().toOSString().length());

		List<IProject> unrelatedCandidates = new ArrayList<>();
		Collection<IPath> validSubPaths = new ArrayList<>();
		for (IProject suspiciousProject : suspiciousProjects) {
			if (validSubPaths.contains(suspiciousProject.getFullPath().makeRelative())) {
				continue;
			}

			// Check whether the suspicious gradle project is the parent project of the opening project.
			boolean isParentProject = false;
			Collection<IPath> subpaths = null;
			PersistentModel model = CorePlugin.modelPersistence().loadModel(suspiciousProject);
			if (model.isPresent()) {
				subpaths = model.getSubprojectPaths();
				if (!subpaths.isEmpty()) {
					for (IProject validProject : validProjects) {
						if (subpaths.contains(validProject.getFullPath().makeRelative())) {
							isParentProject = true;
							break;
						}
					}
				}
			}

			if (isParentProject) {
				validSubPaths.addAll(subpaths);
			} else {
				unrelatedCandidates.add(suspiciousProject);
			}
		}

		List<IProject> result = new ArrayList<>();
		// Exclude those projects which are the subprojects of the verified parent project.
		for (IProject candidate : unrelatedCandidates) {
			if (!validSubPaths.contains(candidate.getFullPath().makeRelative())) {
				result.add(candidate);
			}
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> getCompilerArgs(Map<String, Object> apConfigurations) {
		return apConfigurations.get("compilerArgs") instanceof List<?> l ? (List<Object>) l : List.of();
	}

	@SuppressWarnings("unchecked")
	private static Set<File> getProcessors(Map<String, Object> apConfigurations) {
		return apConfigurations.get("processors") instanceof Set<?> set ? (Set<File>) set : Set.of();
	}

	private static void disableApt(IJavaProject javaProject) {
		if (AptConfig.isEnabled(javaProject)) {
			AptConfig.setEnabled(javaProject, false);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IBuildSupport#compile(org.eclipse.core.resources.IProject)
	 */
	@Override
	public void compile(IResource resource, IProgressMonitor monitor) {
		PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferenceManager == null || !preferenceManager.getPreferences().isAutobuildEnabled()) {
			return;
		}
		boolean shouldCompile = (preferenceManager.getPreferences().isAspectjSupportEnabled() && (resource == null || resource.getName().endsWith(ASPECTJ_EXTENSION)))
				|| (preferenceManager.getPreferences().isKotlinSupportEnabled() && (resource == null || resource.getName().endsWith(KOTLIN_EXTENSION)))
				|| (preferenceManager.getPreferences().isScalaSupportEnabled() && (resource == null || resource.getName().endsWith(SCALA_EXTENSION)))
				|| (preferenceManager.getPreferences().isGroovySupportEnabled() && (resource == null || resource.getName().endsWith(GROOVY_EXTENSION)));
		if (!shouldCompile) {
			return;
		}
		if (resource != null && applies(resource.getProject())) {
			String uriString = JDTUtils.getFileURI(resource);
			PublishDiagnosticsParams $ = new PublishDiagnosticsParams(ResourceUtils.toClientUri(uriString), Collections.emptyList());
			JavaClientConnection conn = JavaLanguageServerPlugin.getInstance().getProtocol().getClientConnection();
			conn.publishDiagnostics($);
			File projectDir = resource.getProject().getLocation().toFile();
			String projectLocation;
			try {
				projectLocation = projectDir.getCanonicalPath();
			} catch (IOException e) {
				JavaLanguageServerPlugin.logException(e);
				return;
			}
			try (ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()) {
				GradleProject rootProject = connection.model(GradleProject.class).get();
				GradleProject gradleProject = getProject(rootProject, projectLocation);
				if (gradleProject == null) {
					gradleProject = rootProject;
				}
				List<String> taskNames = gradleProject.getTasks().stream().map(t -> t.getName()).filter(name -> includeTasks.contains(name)).collect(Collectors.toList());
				compile(gradleProject, taskNames, monitor);
			} catch (Exception e) {
				if (Boolean.getBoolean("jdt.ls.debug")) {
					JavaLanguageServerPlugin.logException(e);
				}
			}
			return;
		}
		if (resource == null) {
			Set<IProject> projects = new HashSet<>();
			for (IProject project : ProjectUtils.getGradleProjects()) {
				if (!project.isOpen()) {
					continue;
				}
				Optional<GradleBuild> gradleBuild = GradleCore.getWorkspace().getBuild(project);
				if (gradleBuild.isPresent()) {
					GradleBuild gb = gradleBuild.get();
					if (gb instanceof InternalGradleBuild igb) {
						File rootDir = igb.getBuildConfig().getProperties().getRootProjectDirectory();
						if (rootDir != null && rootDir.equals(project.getRawLocation().toFile())) {
							projects.add(project);
						}
					}
				}
			}
			Map<String, GradleProject> roots = new HashMap<>();
			for (IProject project : projects) {
				File projectDir = project.getLocation().toFile();
				try (ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()) {
					GradleProject gradleProject = connection.getModel(GradleProject.class);
					roots.put(gradleProject.getPath(), gradleProject);
				}
			}
			for (GradleProject gradleProject : roots.values()) {
				List<String> taskNames = new ArrayList<>();
				for (String taskName : includeTasks) {
					if (hasTask(gradleProject, taskName)) {
						taskNames.add(taskName);
					}
				}
				compile(gradleProject, taskNames, monitor);
			}
		}
	}

	private void compile(GradleProject gradleProject, List<String> taskNames, IProgressMonitor monitor) {
		if (gradleProject != null && !taskNames.isEmpty()) {
			SubMonitor progress = SubMonitor.convert(monitor, 100).checkCanceled();
			long start = System.currentTimeMillis();
			try {
				Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, monitor);
			} catch (OperationCanceledException | InterruptedException e) {
				// ignore
			}
			File projectDir = gradleProject.getProjectDirectory();
			try (ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()) {
				// @formatter:off
				progress.beginTask("Run gradle tasks: " + taskNames + " for project '" + gradleProject.getPath() + "'", 100);
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
				try {
					connection
						.newBuild()
						.setStandardError(errorStream)
						.setStandardOutput(outputStream)
						.forTasks(taskNames.toArray(new String[0]))
						.withArguments("-q",
							"-Dorg.gradle.configureondemand=true",
							"-Dorg.gradle.caching=true",
							"--continue", "--console=plain"
						)
						.run();
				} catch (Exception e) {
					if (Boolean.getBoolean("jdt.ls.debug")) {
						JavaLanguageServerPlugin.logException(e);
					}
				} finally {
					String log = outputStream.toString();
					if (!log.isBlank()) {
						JavaLanguageServerPlugin.debugTrace("Gradle log: " + log);
					}
					String error = errorStream.toString();
					if (!error.isBlank()) {
						boolean parseKotlin = taskNames.contains(COMPILE_KOTLIN) || taskNames.contains(COMPILE_TEST_KOTLIN);
						boolean parseGroovy = taskNames.contains(COMPILE_GROOVY) || taskNames.contains(COMPILE_TEST_GROOVY);
						boolean parseAspectj = taskNames.contains(COMPILE_ASPECTJ) || taskNames.contains(COMPILE_TEST_ASPECTJ);
						boolean parseScala = taskNames.contains(COMPILE_SCALA) || taskNames.contains(COMPILE_TEST_SCALA);
						publishDiagnostics(error, parseKotlin, parseGroovy, parseAspectj, parseScala);
					}
					JavaModelManager.getJavaModelManager().resetExternalFilesCache();
					try {
						IPath path = Path.fromOSString(projectDir.getAbsolutePath());
						IContainer[] containers = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(path.toFile().toURI());
						IProject[] projects = Arrays.stream(containers)
								.filter(container -> container instanceof IProject)
								.map(container -> (IProject) container)
								.toArray(IProject[]::new);
						IProject[] workspaceProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
						for (IProject project: projects) {
							project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
							for (IProject wp: workspaceProjects) {
								if (wp.isAccessible() && !wp.equals(project)) {
									if (project.getLocation().isPrefixOf(wp.getLocation())) {
										wp.refreshLocal(IResource.DEPTH_INFINITE, monitor);
									}
								}
							}
						}
					} catch (CoreException e) {
						if (Boolean.getBoolean("jdt.ls.debug")) {
							JavaLanguageServerPlugin.logException(e);
						}
					}
					progress.done();
				}
				// @formatter:on
				long elapsed = System.currentTimeMillis() - start;
				JavaLanguageServerPlugin.debugTrace("Compiling gradle project " + gradleProject.getName() + " took " + elapsed + " ms");
			}
		}
	}

	private boolean hasTask(GradleProject project, String taskName) {
		for (GradleTask task : project.getTasks()) {
			if (task.getName().equals(taskName)) {
				return true;
			}
		}
		for (GradleProject childProject : project.getChildren()) {
			if (hasTask(childProject, taskName)) {
				return true;
			}
		}
		return false;
	}

	private void publishDiagnostics(String error, boolean parseKotlin, boolean parseGroovy, boolean parseAspectj, boolean parseScala) {
		JavaLanguageServerPlugin.logError("Gradle Error log: " + error);
		Map<String, List<Diagnostic>> diagnosticMap = new HashMap<>();
		if (parseKotlin) {
			getDiagnostics(KOTLIN_PATTERN, error, diagnosticMap);
		}
		if (parseGroovy) {
			getDiagnostics(GROOVY_PATTERN, error, diagnosticMap);
		}
		if (parseAspectj) {
			getDiagnostics(ASPECTJ_PATTERN, error, diagnosticMap);
		}
		if (parseScala) {
			getDiagnostics(SCALA_PATTERN, error, diagnosticMap);
		}
		diagnosticMap.forEach((uri, diagnostics) -> {
			PublishDiagnosticsParams $ = new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), diagnostics);
			JavaClientConnection conn = JavaLanguageServerPlugin.getInstance().getProtocol().getClientConnection();
			conn.publishDiagnostics($);
		});
	}

	private void getDiagnostics(Pattern pattern, String error, Map<String, List<Diagnostic>> diagnosticMap) {
		Matcher matcher = pattern.matcher(error);
		while (matcher.find()) {
			String uri = getGroup(matcher, "file", null);
			if (uri == null) {
				continue;
			}
			Diagnostic diag = new Diagnostic();
			String message = getGroup(matcher, "message", UNKNOWN);
			diag.setMessage(message);
			String ID = Integer.toString(-1);
			diag.setCode(ID);
			DiagnosticSeverity severity;
			String type = getGroup(matcher, "type", "e");
			if ("w".equalsIgnoreCase(type) || "warning".equalsIgnoreCase(type) || "warn".equalsIgnoreCase(type)) {
				severity = DiagnosticSeverity.Warning;
			} else {
				severity = DiagnosticSeverity.Error;
			}
			diag.setSeverity(severity);
			int line;
			try {
				line = Integer.valueOf(matcher.group("line"));
				line--;
			} catch (Exception e) {
				line = 0;
			}
			int startChar = 0;
			int endChar = -1;
			// AspectJ
			if (uri.endsWith(ASPECTJ_EXTENSION)) {
				// whole line
				startChar = 0;
				endChar = 500;
			} else {
				try {
					startChar = Integer.valueOf(matcher.group("col"));
					if (uri.endsWith(KOTLIN_EXTENSION)) {
						startChar--;
					}
					if (uri.endsWith(SCALA_EXTENSION)) {
						startChar--;
					}
				} catch (Exception e) {
					// ignore
				}
			}
			if (startChar < 0) {
				startChar = 0;
			}
			if (line < 0) {
				line = 0;
			}
			if (endChar < 0) {
				endChar = startChar;
			}
			Position startPosition = new Position(line, startChar);
			Position endPosition = new Position(line, endChar);
			Range range = new Range(startPosition, endPosition);
			diag.setRange(range);
			diag.setSource(GRADLE);
			if (uri != null) {
				if (uri.endsWith(KOTLIN_EXTENSION)) {
					diag.setSource(KOTLIN);
				} else if (uri.endsWith(GROOVY_EXTENSION)) {
					diag.setSource(GROOVY);
				} else if (uri.endsWith(ASPECTJ_EXTENSION)) {
					diag.setSource(ASPECTJ);
				} else if (uri.endsWith(SCALA_EXTENSION)) {
					diag.setSource(SCALA);
				}
			}
			List<Diagnostic> diagnostics = diagnosticMap.get(uri);
			if (diagnostics == null) {
				diagnostics = new ArrayList<>();
				diagnosticMap.put(uri, diagnostics);
			}
			diagnostics.add(diag);
			if (Boolean.getBoolean("jdt.ls.debug")) {
				StringBuilder builder = new StringBuilder();
				builder.append("--- Diagnostic ---");
				builder.append("\nResource: " + matcher.group("file"));
				builder.append("\nType: " + type);
				builder.append("\nPosition: Line: " + line + " Character:" + startChar);
				builder.append("\nmessage: " + matcher.group("message").trim());
				JavaLanguageServerPlugin.debugTrace(builder.toString());
			}
		}
	}

	private String getGroup(Matcher matcher, String group, String defaultValue) {
		try {
			return Optional.ofNullable(matcher.group(group)).map(String::trim).orElse(defaultValue);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private GradleProject getProject(GradleProject rootProject, String projectLocation) {
		try {
			if (rootProject.getProjectDirectory().getCanonicalPath().equals(projectLocation)) {
				return rootProject;
			}
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException(e);
		}
		for (GradleProject child : rootProject.getChildren().getAll()) {
			GradleProject gradleProject = getProject(child, projectLocation);
			if (gradleProject != null) {
				return gradleProject;
			}
		}
		return null;
	}

	// Gradle Tooling API removes several scala libraries and adds the Scala builder and container that aren't recognized by Java LS.
	// See https://github.com/gradle/gradle/blob/b3c5d40e82439da4627b38b4ced93121e551b0eb/platforms/ide/ide-plugins/src/main/java/org/gradle/plugins/ide/eclipse/EclipsePlugin.java#L375-L377
	@Override
	public void cleanScalaProjects(IProgressMonitor monitor) {
		PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferenceManager == null || !preferenceManager.getPreferences().isScalaSupportEnabled()) {
			return;
		}
		for (IProject project : ProjectUtils.getGradleProjects()) {
			try {
				IProjectDescription description = project.getDescription();
				ICommand[] oldSpecs = description.getBuildSpec();
				List<ICommand> newSpecs = new LinkedList<>();
				for (ICommand command : oldSpecs) {
					String builderName = command.getBuilderName();
					IExtension extension = Platform.getExtensionRegistry().getExtension(ResourcesPlugin.PI_RESOURCES, ResourcesPlugin.PT_BUILDERS, builderName);
					if (extension != null) {
						IConfigurationElement[] configs = extension.getConfigurationElements();
						if (configs.length > 0) {
							newSpecs.add(command);
						}
					}
				}
				String[] natureIds = description.getNatureIds();
				List<String> newNatureIds = new LinkedList<>();
				for (String natureId : natureIds) {
					if (CorePlugin.workspaceOperations().isNatureRecognizedByEclipse(natureId)) {
						newNatureIds.add(natureId);
					}
				}
				if (natureIds.length != newNatureIds.size() || oldSpecs.length != newSpecs.size()) {
					description.setBuildSpec(newSpecs.toArray(new ICommand[newSpecs.size()]));
					description.setNatureIds(newNatureIds.toArray(new String[newNatureIds.size()]));
					project.setDescription(description, IResource.FORCE, monitor);
				}
				if (ProjectUtils.isJavaProject(project)) {
					IJavaProject javaProject = JavaCore.create(project);
					IClasspathEntry[] classpath = javaProject.getRawClasspath();
					List<IClasspathEntry> newClasspath = new LinkedList<>();
					boolean updateClasspath = false;
					for (IClasspathEntry entry : classpath) {
						if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
							try {
								final ClasspathContainerInitializer initializer = JavaCore.getClasspathContainerInitializer(entry.getPath().segment(0));
								if (initializer != null) {
									newClasspath.add(entry);
								} else {
									updateClasspath = true;
								}
							} catch (Exception e) {
								// ignore
							}
						} else {
							newClasspath.add(entry);
						}
					}
					if (updateClasspath) {
						javaProject.setRawClasspath(newClasspath.toArray(new IClasspathEntry[0]), monitor);
					}
					checkSourcePaths(project, monitor);
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Clean projects", e);
			}
		}
	}

	private void checkSourcePaths(IProject project, IProgressMonitor monitor) {
		if (project == null || !ProjectUtils.isJavaProject(project)) {
			return;
		}
		File projectDir = project.getLocation().toFile();
		File initScript = null;
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()) {
			initScript = createInitScript();
			BuildLauncher launcher = connection.newBuild();
			launcher.withArguments("--init-script", initScript.getAbsolutePath(), "--no-configuration-cache");
			launcher.forTasks("javalsCheckProject");
			launcher.setStandardOutput(outputStream);
			launcher.run();
			String output = outputStream.toString();
			process(project, output, monitor);
		} catch (Exception e) {
			if (Boolean.getBoolean("jdt.ls.debug")) {
				JavaLanguageServerPlugin.logException(e);
			}
		} finally {
			if (initScript != null) {
				try {
					Files.delete(initScript.toPath());
				} catch (IOException e) {
					JavaLanguageServerPlugin.logException(e);
				}
			}
		}
		return;
	}

	private File createInitScript() throws IOException {
		String scriptContent = """
				import org.gradle.api.artifacts.component.ModuleComponentIdentifier
				import org.gradle.jvm.JvmLibrary
				import org.gradle.language.base.artifact.SourcesArtifact

				allprojects {
					afterEvaluate { project ->
						if (project.tasks.findByName('javalsCheckProject')) return
						project.tasks.register('javalsCheckProject') {
							doLast {
								println "JAVALS_START"
								def targetConfNames = ['runtimeClasspath', 'testRuntimeClasspath', 'scalaRuntime']
								def componentIds = [] as Set
								def processedFiles = [] as Set
						targetConfNames.findAll { project.configurations.findByName(it) }.each { name ->
									def conf = project.configurations[name]
									if (conf.canBeResolved) {
										conf.incoming.resolutionResult.allComponents.each {
											if (it.id instanceof ModuleComponentIdentifier) componentIds.add(it.id)
										}
									}
								}
						def sourceMap = [:]
								if (!componentIds.isEmpty()) {
									project.dependencies.createArtifactResolutionQuery()
										.forComponents(componentIds)
										.withArtifacts(JvmLibrary, SourcesArtifact)
										.execute()
										.resolvedComponents.each { res ->
											def sources = res.getArtifacts(SourcesArtifact)
											if (!sources.isEmpty()) {
												sourceMap["${res.id.group}:${res.id.module}".toString()] = sources.iterator().next().file.absolutePath
											}
										}
								}
								targetConfNames.findAll { project.configurations.findByName(it) }.each { name ->
									def conf = project.configurations[name]
									if (conf.canBeResolved) {
										conf.resolvedConfiguration.resolvedArtifacts.each { artifact ->
											def binPath = artifact.file.absolutePath
											if (!processedFiles.contains(binPath)) {
												def id = artifact.moduleVersion.id
												def key = "${id.group}:${id.name}".toString()
												def srcPath = sourceMap[key] ?: "NO_SOURCE"
												println "LIB|${binPath}|${srcPath}"
												processedFiles.add(binPath)
											}
										}
									}
								}
						if (project.hasProperty('sourceSets')) {
									project.sourceSets.each { ss ->
										def scope = ss.name // 'main', 'test', 'integrationTest'
										ss.allSource.srcDirs.each { dir ->
											if (dir.exists() && !ss.resources.srcDirs.contains(dir)) {
												println "SRC|${scope}|${dir.absolutePath}"
											}
										}
										ss.resources.srcDirs.each { dir ->
											if (dir.exists()) {
												println "RES|${scope}|${dir.absolutePath}"
											}
										}
									}
								}
								println "JAVALS_END"
							}
						}
					}
				}
				""";
		File tempInit = File.createTempFile("javalsCheckProject", ".gradle");
		Files.write(tempInit.toPath(), scriptContent.getBytes());
		return tempInit;
	}

	private static void process(IProject project, String output, IProgressMonitor monitor) {
		List<String> taskClasspaths = new LinkedList<>();
		Map<String, String> taskClasspathSources = new HashMap<>();
		List<String> sources = new LinkedList<>();
		List<String> resources = new LinkedList<>();
		boolean start = false;
		try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if ("JAVALS_END".equals(line)) {
					start = false;
					continue;
				}
				if (start) {
					if (!line.isEmpty()) {
						String[] elements = line.split("\\|");
						if (elements.length > 1) {
							switch (elements[0]) {
								case "LIB": {
									taskClasspaths.add(elements[1]);
									if (elements.length > 2) {
										taskClasspathSources.put(elements[1], elements[2]);
									} else {
										taskClasspathSources.put(elements[1], "NO_SOURCE");
									}
									break;
								}
								case "SRC": {
									sources.add(elements[2]);
									break;
								}
								case "RES": {
									resources.add(elements[2]);
									break;
								}
								default:
									JavaLanguageServerPlugin.logInfo("Unexpected value: " + elements[0]);
									break;
							}
						}
					}
				}
				if ("JAVALS_START".equals(line)) {
					start = true;
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e);
		}
		// @formatter:off
		List<String> paths = taskClasspaths
				.stream()
				.distinct()
				.collect(Collectors.toList());
		// @formatter:on
		if (!paths.isEmpty()) {
			List<String> toAdd = new ArrayList<>();
			IJavaProject javaProject = JavaCore.create(project);
			for (String path : paths) {
				try {
					IClasspathContainer container = JavaCore.getClasspathContainer(CONTAINER_PATH, javaProject);
					if (container != null) {
						IClasspathEntry[] entries = container.getClasspathEntries();
						Optional<IClasspathEntry> optional = Arrays.stream(entries).filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY).filter(entry -> Objects.equals(entry.getPath(), new Path(path))).findFirst();
						if (!optional.isPresent()) {
							toAdd.add(path);
						}
					}
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.logException(e);
				}
			}
			if (!toAdd.isEmpty()) {
				try {
					IClasspathEntry[] classpath = javaProject.getRawClasspath();
					List<IClasspathEntry> entries = new LinkedList<>();
					for (String path : toAdd) {
						IPath sourcePath;
						String source = taskClasspathSources.get(path);
						if (source != null && !"NO_SOURCE".equals(source)) {
							sourcePath = new Path(source);
						} else {
							sourcePath = null;
						}
						IClasspathEntry entry = JavaCore.newLibraryEntry(new Path(path), sourcePath, null);
						entries.add(entry);
						if (Boolean.getBoolean("jdt.ls.debug")) {
							JavaLanguageServerPlugin.logInfo("Add classpath entry: " + path);
						}
					}
					// @formatter:off
					IClasspathEntry[] newClasspath = Stream.concat(Arrays
						.stream(classpath), entries.stream())
						.distinct()
						.toArray(IClasspathEntry[]::new);
					// @formatter:on
					for (int i = 0; i < newClasspath.length; i++) {
						IClasspathEntry entry = newClasspath[i];
						if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
							Optional<String> optional = resources.stream().filter(r -> Objects.equals(entry.getPath().removeFirstSegments(1), new Path(r).makeRelativeTo(project.getLocation()))).findFirst();
							if (optional.isPresent()) {
								IPath[] exclusions = entry.getExclusionPatterns();
								if (exclusions == null) {
									exclusions = new IPath[0];
								}
								List<IPath> currentExclusions = new ArrayList<>(Arrays.asList(exclusions));
								for (String ext : EXCLUSIONS_PATTERNS) {
									IPath newPath = new Path(ext);
									boolean exists = currentExclusions.stream().anyMatch(existingPath -> existingPath.toString().equals(ext));
									if (!exists) {
										currentExclusions.add(newPath);
									}
								}
								IClasspathEntry newEntry = JavaCore.newSourceEntry(entry.getPath(), entry.getInclusionPatterns(), currentExclusions.toArray(new IPath[0]), entry.getOutputLocation(), entry.getExtraAttributes());
								newClasspath[i] = newEntry;
							}
						}
					}
					javaProject.setRawClasspath(newClasspath, monitor);
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.logException(e);
				}
			}
		}
	}

}
