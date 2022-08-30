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
package org.eclipse.jdt.ls.core.internal.framework.android;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProgressReport;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.framework.IFrameworkSupport;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.GradleEnvironment;

public class AndroidSupport implements IFrameworkSupport {

	public static String SYNCHRONIZATION_TASKS_API_GRADLE = "5.4";
	public static String ASSEMBLE_TASK_NAME = "assemble";

	@Override
	public void onDidProjectsImported(IProgressMonitor monitor) {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager == null) {
			return;
		}

		if (!preferencesManager.getPreferences().isAndroidSupportEnabled()) {
			return;
		}

		List<IProject> projects = ProjectUtils.getGradleProjects();
		if (projects.isEmpty()) {
			return;
		}

		JavaLanguageClient client = JavaLanguageServerPlugin.getProjectsManager().getConnection();
		ProgressReport progressReport = new ProgressReport(UUID.randomUUID().toString());
		progressReport.setTask("Running Gradle tasks");
		progressReport.setComplete(false);
		progressReport.setTotalWork(1);
		progressReport.setStatus("Generating Android project sources files...");
		client.sendProgressReport(progressReport);

		// Avoid double-processing a GradleBuild
		Set<GradleBuild> processedGradleBuilds = new HashSet<>();
		for (IProject project : projects) {
			if (!ProjectUtils.isJavaProject(project)) {
				continue;
			}
			Optional<GradleBuild> gradleBuild = GradleCore.getWorkspace().getBuild(project);
			if (gradleBuild.isEmpty()) {
				continue;
			}
			GradleBuild build = gradleBuild.get();
			if (!processedGradleBuilds.add(build)) {
				continue;
			}
			try {
				BuildEnvironment buildEnvironment = build.withConnection(connection -> connection.getModel(BuildEnvironment.class), monitor);
				if (buildEnvironment == null) {
					continue;
				}
				GradleEnvironment gradleEnvironment = buildEnvironment.getGradle();
				if (gradleEnvironment == null) {
					continue;
				}
				String gradleVersion = gradleEnvironment.getGradleVersion();
				if (gradleVersion == null) {
					continue;
				}
				// for Gradle versions support eclipseModel.synchronizationTasks() API, there is nothing to do after importing
				if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version(SYNCHRONIZATION_TASKS_API_GRADLE)) >= 0) {
					continue;
				}
				GradleProject rootProject = build.withConnection(connection -> connection.getModel(GradleProject.class), monitor);
				if (hasAndroidTasks(rootProject)) {
					build.withConnection(connection -> {
						connection.newBuild().forTasks(ASSEMBLE_TASK_NAME).run();
						return null;
					}, monitor);
				}
			} catch (Exception e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}
		progressReport.setWorkDone(1);
		progressReport.setComplete(true);
		client.sendProgressReport(progressReport);

	}

	private boolean hasAndroidTasks(GradleProject project) {
		for (GradleTask task : project.getTasks()) {
			if ("Android".equalsIgnoreCase(task.getGroup())) {
				return true;
			}
		}
		for (GradleProject subProject : project.getChildren()) {
			if (hasAndroidTasks(subProject)) {
				return true;
			}
		}
		return false;
	}

}
