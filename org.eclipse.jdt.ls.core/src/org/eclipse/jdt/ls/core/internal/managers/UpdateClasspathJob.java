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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

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
		Map<IJavaProject, Optional<UpdateClasspathRequest>> mergedRequestPerProject = requests.stream().collect(
			Collectors.groupingBy(UpdateClasspathRequest::getProject,
				Collectors.reducing((mergedRequest, request) -> {
					mergedRequest.getInclude().addAll(request.getInclude());
					mergedRequest.getExclude().addAll(request.getExclude());
					mergedRequest.getSources().putAll(request.getSources());
					return mergedRequest;
				})
			)
		);
		for (Map.Entry<IJavaProject, Optional<UpdateClasspathRequest>> entry : mergedRequestPerProject.entrySet()) {
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			if (entry.getValue() != null) {
				final IJavaProject project = entry.getKey();
				final UpdateClasspathRequest request = entry.getValue().get();
				updateClasspath(project, request.include, request.exclude, request.sources, monitor);
			}
		}
		synchronized (queue) {
			if (!queue.isEmpty()) {
				schedule(SCHEDULE_DELAY);
			}
		}
		return Status.OK_STATUS;
	}

	private void updateClasspath(IJavaProject project, Set<String> include, Set<String> exclude, Map<String, IPath> sources, IProgressMonitor monitor) throws CoreException {
		final Map<String, IPath> libraries = new HashMap<>();
		for (final String binary: include) {
			if (exclude.contains(binary)) {
				continue;
			}
			libraries.put(binary, sources.get(binary));
		}
		ProjectUtils.updateBinaries(project, libraries, monitor);
	}

	public void updateClasspath(IJavaProject project, Collection<String> include, Collection<String> exclude, Map<String, IPath> sources) {
		if (project == null || include == null) {
			return;
		}
		if (exclude == null) {
			exclude = new ArrayList<>();
		}
		if (sources == null) {
			sources = new HashMap<>();
		}
		queue(new UpdateClasspathRequest(project, new HashSet<>(include), new HashSet<>(exclude), sources));
		schedule(SCHEDULE_DELAY);
	}

	public void updateClasspath(IJavaProject project, IPath libFolderPath, IProgressMonitor monitor) throws CoreException {
		final Set<Path> binaries = ProjectUtils.collectBinaries(Collections.singleton(libFolderPath), monitor);
		final Set<String> include = new HashSet<>();
		final Map<String, IPath> sources = new HashMap<>();
		for (final Path binary: binaries) {
			final IPath source = ProjectUtils.detectSources(binary);
			include.add(binary.toString());
			sources.put(binary.toString(), source);
		}
		updateClasspath(project, include, null, sources);
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
		private Map<String, IPath> sources;

		UpdateClasspathRequest(IJavaProject project, Set<String> include, Set<String> exclude, Map<String, IPath> sources) {
			this.project = project;
			this.include = include;
			this.exclude = exclude;
			this.sources = sources;
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

		Map<String, IPath> getSources() {
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
