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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.core.resources.IProject;
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
import org.eclipse.jdt.ls.core.internal.managers.GradleProjectImporter;
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

		List<Object> projectUris = new ArrayList<>();
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
				projectUris.add(project.getLocationURI().toString());
			}
		}
		if (!projectUris.isEmpty()) {
			ActionableNotification notification = new ActionableNotification().withSeverity(MessageType.Info)
					.withMessage("Would you like to generate Java source files out of your proto files?")
					.withCommands(Arrays.asList(new Command("Yes", "java.protobuf.generateSources", Arrays.asList(projectUris))));
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
					if (attribute.getName().equals(PROTOBUF_GENERATED_SOURCE)
							&& attribute.getValue().equals("true")) {
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
	 * @param projectUris the project uris where the tasks will be executed.
	 * @param monitor progress monitor.
	 */
	public static void generateProtobufSources(List<String> projectUris, IProgressMonitor monitor) {
		JavaLanguageClient client = JavaLanguageServerPlugin.getProjectsManager().getConnection();
		ProgressReport progressReport = new ProgressReport(UUID.randomUUID().toString());
		progressReport.setTask("Running Gradle tasks");
		progressReport.setComplete(false);
		progressReport.setTotalWork(projectUris.size());
		progressReport.setStatus("Generating Java sources from proto files...");
		client.sendProgressReport(progressReport);
		try {
			boolean succeeded = false;
			for (String projectUri : projectUris) {
				if (!StringUtils.isEmpty(projectUri)) {
					succeeded = runGenerateProtobufTasks(projectUri, monitor);
				}
				progressReport.setWorkDone(progressReport.getWorkDone() + 1);
			}

			if (!succeeded) {
				ActionableNotification notification = new ActionableNotification().withSeverity(MessageType.Error)
						.withMessage("Exception happens when generating source files, please open logs for details.")
						.withCommands(Arrays.asList(new Command("Open", "java.open.serverLog")));
				client.sendActionableNotification(notification);
			}
		} finally {
			progressReport.setComplete(true);
			client.sendProgressReport(progressReport);
		}
	}

	/**
	 * Run the Gradle task 'generateProto' & 'generateTestProto' under the uri.
	 * @param projectUri uri of the project.
	 * @param monitor progress monitor.
	 */
	private static boolean runGenerateProtobufTasks(String projectUri, IProgressMonitor monitor) {
		Path dir = Path.of(URI.create(projectUri));
		BuildConfiguration configuration = GradleProjectImporter.getBuildConfiguration(dir);
		GradleBuild build = GradleCore.getWorkspace().createBuild(configuration);

		try {
			build.withConnection(connection -> {
				connection.newBuild().forTasks("generateProto", "generateTestProto").run();
				return null; 
			}, monitor);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e);
			return false;
		}
		return true;
	}
}
