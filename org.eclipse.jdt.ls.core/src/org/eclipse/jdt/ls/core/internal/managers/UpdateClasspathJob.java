/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.ReferencedLibraries;

/**
 * Job updating project classpath to match content of library folders.
 */
public class UpdateClasspathJob extends WorkspaceJob {

	private static final long SCHEDULE_DELAY = 1000L;

	private final Set<UpdateClasspathRequest> queue = new LinkedHashSet<>();

	private static final UpdateClasspathJob instance = new UpdateClasspathJob();

	UpdateClasspathJob() {
		super("Update classpath Job");
	}

	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
		final List<UpdateClasspathRequest> requests;
		synchronized (this.queue) {
			requests = new ArrayList<>(this.queue);
			this.queue.clear();
		}
		Map<IJavaProject, UpdateClasspathRequest> mergedRequestPerProject = requests.stream().collect(
			Collectors.groupingBy(UpdateClasspathRequest::getProject,
				Collectors.reducing(new UpdateClasspathRequest(), (mergedRequest, request) -> {
					mergedRequest.setProject(request.getProject());
					mergedRequest.getInclude().addAll(request.getInclude());
					mergedRequest.getExclude().addAll(request.getExclude());
					mergedRequest.getSources().putAll(request.getSources());
					return mergedRequest;
				})
			)
		);
		for (Map.Entry<IJavaProject, UpdateClasspathRequest> entry : mergedRequestPerProject.entrySet()) {
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			if (entry.getValue() != null) {
				final IJavaProject project = entry.getKey();
				final UpdateClasspathRequest request = entry.getValue();
				doUpdateClasspath(project, request.include, request.exclude, request.sources, monitor);
			}
		}
		synchronized (queue) {
			if (!queue.isEmpty()) {
				schedule(SCHEDULE_DELAY);
			}
		}
		return Status.OK_STATUS;
	}

	private void doUpdateClasspath(IJavaProject javaProject, Set<String> include, Set<String> exclude, Map<String, String> sources, IProgressMonitor monitor) throws CoreException {
		JavaLanguageServerPlugin.logInfo(">> Updating classpath for project " + javaProject.getElementName());
		final IPath realFolder = ProjectUtils.getProjectRealFolder(javaProject.getProject());
		final Set<Path> binaries = ProjectUtils.collectBinaries(realFolder, include, exclude, monitor);
		final Map<Path, IPath> expandedSources = new HashMap<>();
		for (final Map.Entry<String, String> entry: sources.entrySet()) { // Expand sources to absolute path
			final Path realFolderPath = realFolder.toFile().toPath();
			final Path binary = realFolderPath.resolve(entry.getKey());
			final Path source = realFolderPath.resolve(entry.getValue());
			expandedSources.put(binary, new org.eclipse.core.runtime.Path(source.toString()));
		}
		final Map<Path, IPath> libraries = new HashMap<>();
		for (final Path binary: binaries) {
			if (expandedSources.containsKey(binary)) {
				libraries.put(binary, expandedSources.get(binary));
			} else { // If not specified in source map, try to detect it
				libraries.put(binary, ProjectUtils.detectSources(binary));
			}
		}
		ProjectUtils.updateBinaries(javaProject, libraries, monitor);
	}

	public void updateClasspath(IJavaProject project, Set<String> include, Set<String> exclude, Map<String, String> sources) {
		if (project == null || include == null) {
			return;
		}
		if (exclude == null) {
			exclude = new HashSet<>();
		}
		if (sources == null) {
			sources = new HashMap<>();
		}
		update(new UpdateClasspathRequest(project, include, exclude, sources));
	}

	public void updateClasspath(IJavaProject project, ReferencedLibraries libraries) {
		updateClasspath(project, libraries.getInclude(), libraries.getExclude(), libraries.getSources());
	}

	public void updateClasspath(IJavaProject project) {
		updateClasspath(project, JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getReferencedLibraries());
	}

	public void updateClasspath() {
		Preferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		Collection<IPath> rootPaths = preferences.getRootPaths();
		if (rootPaths == null) {
			return;
		}
		for (IPath rootPath: rootPaths) { // Update classpath for all invisible projects
			String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(rootPath);
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);
			if (!(project.exists() && project.isAccessible())) {
				continue;
			}
			updateClasspath(JavaCore.create(project), preferences.getReferencedLibraries());
		}
	}

	void update(UpdateClasspathRequest updateRequest) {
		queue(updateRequest);
		schedule(SCHEDULE_DELAY);
	}

	void queue(UpdateClasspathRequest updateRequest) {
		synchronized (queue) {
			queue.add(updateRequest);
		}
	}

	static class UpdateClasspathRequest {
		private IJavaProject project;
		private Set<String> include;
		private Set<String> exclude;
		private Map<String, String> sources;

		UpdateClasspathRequest(IJavaProject project, Set<String> include, Set<String> exclude, Map<String, String> sources) {
			this.project = project;
			this.include = include;
			this.exclude = exclude;
			this.sources = sources;
		}

		UpdateClasspathRequest() {
			this(null, new HashSet<>(), new HashSet<>(), new HashMap<>());
		}

		void setProject(IJavaProject project) {
			this.project = project;
		}

		IJavaProject getProject() {
			return project;
		}

		Set<String> getInclude() {
			return include;
		}

		Set<String> getExclude() {
			return exclude;
		}

		Map<String, String> getSources() {
			return sources;
		}

		@Override
		public int hashCode() {
			return Objects.hash(include, exclude, sources, project);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			UpdateClasspathRequest other = (UpdateClasspathRequest) obj;
			return Objects.equals(project, other.project)
				&& Objects.equals(include, other.include)
				&& Objects.equals(exclude, other.exclude)
				&& Objects.equals(sources, other.sources);
		}

	}

	public static UpdateClasspathJob getInstance() {
		return instance;
	}

}
