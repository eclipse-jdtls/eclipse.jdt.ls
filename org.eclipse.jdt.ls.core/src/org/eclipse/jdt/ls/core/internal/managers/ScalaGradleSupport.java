/*******************************************************************************
 * Copyright (c) 2016-2026 Red Hat Inc. and others.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import com.google.common.collect.ImmutableSet;

/**
 * @author snjeza
 *
 */
public class ScalaGradleSupport {

	public static final Path CONTAINER_PATH = new Path("org.eclipse.buildship.core.gradleclasspathcontainer");
	// Gradle Tooling API removes several scala libraries and adds the Scala builder and container that aren't recognized by Java LS.
	// See https://github.com/gradle/gradle/blob/b3c5d40e82439da4627b38b4ced93121e551b0eb/platforms/ide/ide-plugins/src/main/java/org/gradle/plugins/ide/eclipse/EclipsePlugin.java#L375-L377
	public static final Set<String> SCALA_LIBRARIES = ImmutableSet.of("scala-library", "scala-swing", "scala-dbc");
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
			initScript = getInitScript();
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
		}
	}

	private File getInitScript() throws IOException {
		return GradleUtils.getGradleInitScript("/gradle/scala/javals.gradle");
	}

	/*
	 * The process method checks if a library has been added and if not, adds it.
	 * Gradle Tooling API excludes some scala libraries because it expects Scala IDE to add them.
	 * Since Scala IDE for VS Code doesn't exist, we add those libraries.
	 * The method also checks resources folders and excludes them from the compilation.
	 */
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
			IJavaProject javaProject = JavaCore.create(project);
			List<String> toAdd = getMissingPaths(javaProject, paths);
			if (!toAdd.isEmpty()) {
				try {
					configureClasspath(javaProject, toAdd, taskClasspathSources, resources, monitor);
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.logException(e);
				}
			}
		}
	}

	private static void configureClasspath(IJavaProject javaProject, List<String> toAdd, Map<String, String> taskClasspathSources, List<String> resources, IProgressMonitor monitor) throws JavaModelException {
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
		javaProject.setRawClasspath(newClasspath, monitor);
	}

	private static List<String> getMissingPaths(IJavaProject javaProject, List<String> paths) {
		List<String> toAdd = new ArrayList<>();
		// @formatter:off
		List<String> scalaLibs = paths
				.stream()
				.filter(s -> SCALA_LIBRARIES.stream().anyMatch(s::contains))
				.collect(Collectors.toList());
		// @formatter:on
		for (String path : scalaLibs) {
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
		return toAdd;
	}

}
