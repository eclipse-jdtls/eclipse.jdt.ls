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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
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
		// avoid this computation entirely if disabled
		if (!prefs.getPreferences().isTelemetryEnabled()) {
			return;
		}

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
		if (sourceLevelMin != 0) {
			properties.addProperty("compiler.source.min", Float.toString(sourceLevelMin));
		}
		if (sourceLevelMax != 0) {
			properties.addProperty("compiler.source.max", Float.toString(sourceLevelMax));
		}
		properties.addProperty("time.projectsinitialized", Long.toString(projectInitElapsedTime));
		properties.addProperty("time.serviceready", Long.toString(serviceReadyElapsedTime));
		properties.addProperty("time.buildFinished", Long.toString(buildFinishedElapsedTime));
		properties.addProperty("initialization.first", Boolean.toString(this.firstTimeInitialization));

		Map<IPath, Long> deps = computeDependencySize();
		int indexCount = deps.size();
		long librarySize = deps.values().stream().reduce(0l, Long::sum);
		properties.addProperty("dependency.count", Integer.toString(indexCount));
		properties.addProperty("dependency.size", Long.toString(librarySize));

		telemetryEvent(JAVA_PROJECT_BUILD, properties);
	}

	/**
	 * The total size (in bytes) of all dependencies in the workspace
	 * This should correspond to the total size of dependencies used by a
	 * project.
	 *
	 * @return the size (in bytes) of all dependencies indexed in the workspace.
	 */
	private static Map<IPath, Long> computeDependencySize() {
		Map<IPath, Long> result = new HashMap<>();
		for (IJavaProject proj : ProjectUtils.getJavaProjects()) {
			if (!ProjectsManager.DEFAULT_PROJECT_NAME.equals(proj.getProject().getName())) {
				try {
					IPackageFragmentRoot[] pfroots = proj.getPackageFragmentRoots();
					List<String> vmLibraries = getVMLibraries(proj);
					for (IPackageFragmentRoot pfroot : pfroots) {
						IPath pfPath = pfroot.getPath();
						if (isValidDependency(pfroot, vmLibraries)) {
							result.put(pfPath, pfPath.toFile().length());
						}
					}
				} catch (CoreException e) {
					// continue
				}
			}
		}
		return result;
	}

	private static List<String> getVMLibraries(IJavaProject proj) throws CoreException {
		IVMInstall vmInstall = JavaRuntime.getVMInstall(proj.getJavaProject());
		if (vmInstall == null) {
			return Collections.emptyList();
		}
		List<String> vmLibLocations = Arrays.stream(JavaRuntime.getLibraryLocations(vmInstall)).map(lib -> {
			try {
				return lib.getSystemLibraryPath().toFile().getCanonicalPath();
			} catch (IOException e) {
				return null;
			}
		}).filter(Objects::nonNull).collect(Collectors.toList());
		return vmLibLocations;
	}

	private static boolean isValidDependency(IPackageFragmentRoot pfRoot, List<String> vmLibLocations) {
		try {
			if (pfRoot.getKind() == IPackageFragmentRoot.K_BINARY && pfRoot.getPath() != null) {
				String pfPath = pfRoot.getPath().toFile().getCanonicalPath();
				if (!vmLibLocations.contains(pfPath)) {
					return true;
				}
			}
		} catch (CoreException | IOException e) {
			return false;
		}
		return false;
	}

	private void telemetryEvent(String name, JsonObject properties) {
		boolean telemetryEnabled = prefs.getPreferences().isTelemetryEnabled();
		if (telemetryEnabled) {
			client.telemetryEvent(new TelemetryEvent(name, properties));
		}
	}

}
