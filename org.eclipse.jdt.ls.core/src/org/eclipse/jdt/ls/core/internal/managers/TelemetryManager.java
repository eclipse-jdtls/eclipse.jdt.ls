/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.util.Arrays;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.search.indexing.IndexManager;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class TelemetryManager {

	private static final String JAVA_PROJECT_BUILD = "java.workspace.initialized";

	private JavaLanguageClient client;
	private PreferenceManager prefs;
	private ProjectsManager projectsManager;
	private long languageServerStartTime;
	private long serviceReadyTime;
	private long projectsInitializedTime;
	private boolean firstTimeInitialization;
	public TelemetryManager(JavaLanguageClient client, PreferenceManager prefs) {
		this.client = client;
		this.prefs = prefs;
	}

	public TelemetryManager() {
	}

	public void setLanguageClient(JavaLanguageClient client) {
		this.client = client;
	}

	public void setPreferenceManager(PreferenceManager prefs) {
		this.prefs = prefs;
	}

	public void onLanguageServerStart(long timeMillis, boolean firstTimeInitialization) {
		this.languageServerStartTime = timeMillis;
		this.firstTimeInitialization = firstTimeInitialization;
	}

	public void onProjectsInitialized(ProjectsManager projectsManager, long timeMillis) {
		this.projectsManager = projectsManager;
		this.projectsInitializedTime = timeMillis;
	}

	public void onServiceReady(long timeMillis) {
		this.serviceReadyTime = timeMillis;
	}

	public void onBuildFinished(long buildFinishedTime) {
		JsonObject properties = new JsonObject();
		float sourceLevelMin = 0, sourceLevelMax = 0;
		int javaProjectCount = 0;
		JsonArray buildToolNamesList = new JsonArray();

		for (IProject project : ProjectUtils.getAllProjects()) {
			Optional<IBuildSupport> bs = this.projectsManager.getBuildSupport(project);
			if (bs.isPresent() && !ProjectsManager.DEFAULT_PROJECT_NAME.equals(project.getName())) {
				String buildToolName = bs.get().buildToolName();
				if (!buildToolNamesList.contains(new JsonPrimitive(buildToolName))) {
					buildToolNamesList.add(buildToolName);
				}
				String sourceLevel = ProjectUtils.getJavaSourceLevel(project);
				if (sourceLevel != null) {
					javaProjectCount++;
					if (sourceLevelMin == 0 || Float.parseFloat(sourceLevel) < sourceLevelMin) {
						sourceLevelMin = Float.parseFloat(sourceLevel);
					}
					if (sourceLevelMax == 0 || Float.parseFloat(sourceLevel) > sourceLevelMax) {
						sourceLevelMax = Float.parseFloat(sourceLevel);
					}
				}
			}
		}

		long projectInitElapsedTime, serviceReadyElapsedTime, buildFinishedElapsedTime;
		projectInitElapsedTime = this.projectsInitializedTime - this.languageServerStartTime;
		serviceReadyElapsedTime = this.serviceReadyTime - this.languageServerStartTime;
		buildFinishedElapsedTime = buildFinishedTime - this.languageServerStartTime;

		properties.add("buildToolNames", buildToolNamesList);
		properties.addProperty("javaProjectCount", javaProjectCount);
		properties.addProperty("compiler.source.min", Float.toString(sourceLevelMin));
		properties.addProperty("compiler.source.max", Float.toString(sourceLevelMax));
		properties.addProperty("time.projectsinitialized", Long.toString(projectInitElapsedTime));
		properties.addProperty("time.serviceready", Long.toString(serviceReadyElapsedTime));
		properties.addProperty("time.buildFinished", Long.toString(buildFinishedElapsedTime));
		properties.addProperty("initialization.first", Boolean.toString(this.firstTimeInitialization));

		IndexManager manager = JavaModelManager.getIndexManager();
		if (manager != null) {
			int indexCount = manager.indexLocations.elementSize;
			long librarySize = computeDependencySize(manager);
			properties.addProperty("dependency.size", Long.toString(librarySize));
			properties.addProperty("dependency.count", Integer.toString(indexCount));
		}

		telemetryEvent(JAVA_PROJECT_BUILD, properties);
	}

	/**
	 * The total size (in bytes) of all dependencies in the workspace that have been
	 * indexed. This should correspond to the total size of dependencies used by a
	 * project.
	 *
	 * @param manager
	 *                    the index manager for the workspace
	 *
	 * @return the size (in bytes) of all dependencies indexed in the workspace.
	 */
	private static long computeDependencySize(IndexManager manager) {
		Object[] libraries = manager.indexLocations.keyTable;
		return Arrays.asList(libraries).stream().mapToLong(lib -> lib != null ? ((IPath) lib).toFile().length() : 0).sum();
	}

	private void telemetryEvent(String name, JsonObject properties) {
		boolean telemetryEnabled = prefs.getPreferences().isTelemetryEnabled();
		if (telemetryEnabled) {
			client.telemetryEvent(new TelemetryEvent(name, properties));
		}
	}

}
