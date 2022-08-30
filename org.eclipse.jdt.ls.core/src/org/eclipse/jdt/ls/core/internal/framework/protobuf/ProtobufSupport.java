/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.framework.protobuf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProgressReport;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.framework.IFrameworkSupport;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.MessageType;

public class ProtobufSupport implements IFrameworkSupport {

	/**
	 * The classpath entry attribute which will be added for the protobuf source output directories.
	 */
	public static final String PROTOBUF_GENERATED_SOURCE = "protobuf_generated_source";

	@Override
	public void onDidProjectsImported(IProgressMonitor monitor) {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager == null) {
			return;
		}

		if (!preferencesManager.getPreferences().isProtobufSupportEnabled()) {
			return;
		}

		List<IProject> projects = ProjectUtils.getGradleProjects();
		if (projects.isEmpty()) {
			return;
		}

		List<Object> projectNames = new ArrayList<>();
		for (IProject project : projects) {
			if (!ProjectUtils.isJavaProject(project)) {
				continue;
			}

			Set<File> protobufOutputDirs = findProtobufOutputDirectories(project);
			if (protobufOutputDirs.isEmpty()) {
				continue;
			}

			boolean hasGenerated = containsJavaFiles(protobufOutputDirs);
			if (!hasGenerated) {
				projectNames.add(project.getName());
			}
		}
		if (!projectNames.isEmpty()) {
			ActionableNotification notification = new ActionableNotification().withSeverity(MessageType.Info)
					.withMessage("Would you like to generate Java source files out of your proto files?")
					.withCommands(Arrays.asList(new Command("Generate", "java.protobuf.generateSources", Arrays.asList(projectNames))));
			JavaLanguageServerPlugin.getProjectsManager().getConnection().sendActionableNotification(notification);
		}
	}
	
	/**
	 * Find all the Protobuf source output directories of the given project.
	 * @param project project.
	 */
	private Set<File> findProtobufOutputDirectories(IProject project) {
		IJavaProject javaProject = JavaCore.create(project);
		Set<File> protobufOutputDirs = new HashSet<>();
		try {
			for (IClasspathEntry entry : javaProject.getRawClasspath()) {
				if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
					continue;
				}
				for (IClasspathAttribute attribute : entry.getExtraAttributes()) {
					if (Objects.equals(attribute.getName(), PROTOBUF_GENERATED_SOURCE)
							&& Objects.equals(attribute.getValue(), "true")) {
						protobufOutputDirs.add(new File(project.getLocation().toFile(),
								entry.getPath().removeFirstSegments(1).toString()));
					}
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.log(e);
		}
		return protobufOutputDirs;
	}

	/**
	 * Check if any of the input directories contains Java source files.
	 * @param generatedDirectories directories to check.
	 */
	private boolean containsJavaFiles(Set<File> generatedDirectories) {
		for (File dir : generatedDirectories) {
			if (!dir.exists()) {
				return false;
			}

			try (Stream<Path> walkStream = Files.walk(dir.toPath())) {
				boolean containsJavaFile = walkStream.filter(p -> p.toFile().isFile()).anyMatch(f -> {
					return f.toString().endsWith(".java");
				});

				if (containsJavaFile) {
					return true;
				}
			} catch (IOException e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}
		return false;
	}

	/**
	 * Run generate proto tasks.
	 * @param projectNames the project uris where the tasks will be executed.
	 * @param monitor progress monitor.
	 */
	public static void generateProtobufSources(List<String> projectNames, IProgressMonitor monitor) {
		if (projectNames == null || projectNames.isEmpty()) {
			return;
		}
		JavaLanguageClient client = JavaLanguageServerPlugin.getProjectsManager().getConnection();
		ProgressReport progressReport = new ProgressReport(UUID.randomUUID().toString());
		progressReport.setTask("Running Gradle tasks");
		progressReport.setComplete(false);
		progressReport.setTotalWork(projectNames.size());
		progressReport.setStatus("Generating Java sources from proto files...");
		client.sendProgressReport(progressReport);
		try {
			for (String projectName : projectNames) {
				if (!StringUtils.isEmpty(projectName)) {
					 runGenerateProtobufTasks(projectName, monitor);
				}
				progressReport.setWorkDone(progressReport.getWorkDone() + 1);
			}
		} finally {
			progressReport.setComplete(true);
			client.sendProgressReport(progressReport);
		}
	}

	/**
	 * Run the Gradle task 'generateProto' & 'generateTestProto' for projects with the input name.
	 * @param projectName name of the project.
	 * @param monitor progress monitor.
	 */
	private static void runGenerateProtobufTasks(String projectName, IProgressMonitor monitor) {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (project == null || !project.exists()) {
			return;
		}

		Optional<GradleBuild> build = GradleCore.getWorkspace().getBuild(project);
		if (build.isEmpty()) {
			return;
		}

		try {
			build.get().withConnection(connection -> {
				connection.newBuild().forTasks("generateProto", "generateTestProto").run();
				return null; 
			}, monitor);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e);
		}
	}
}
