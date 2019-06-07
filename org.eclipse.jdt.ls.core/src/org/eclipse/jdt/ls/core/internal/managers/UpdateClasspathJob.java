/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
		Map<IJavaProject, Set<IPath>> reqPerProject = requests.stream().collect(Collectors.groupingBy(UpdateClasspathRequest::getProject, Collectors.mapping(UpdateClasspathRequest::getLibPath, Collectors.toSet())));

		for (Map.Entry<IJavaProject, Set<IPath>> reqs : reqPerProject.entrySet()) {
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			updateClasspath(reqs.getKey(), reqs.getValue(), monitor);
		}
		synchronized (queue) {
			if (!queue.isEmpty()) {
				schedule(SCHEDULE_DELAY);
			}
		}
		return Status.OK_STATUS;
	}

	private void updateClasspath(IJavaProject project, Set<IPath> libFolders, IProgressMonitor monitor) throws CoreException {
		ProjectUtils.updateBinaries(project, libFolders, monitor);
	}

	public void updateClasspath(IJavaProject project, IPath libPath) {
		if (project == null || libPath == null) {
			return;
		}
		queue(new UpdateClasspathRequest(project, libPath));
		schedule(SCHEDULE_DELAY);
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
		private IPath libPath;

		UpdateClasspathRequest(IJavaProject project, IPath libPath) {
			this.project = project;
			this.libPath = libPath;
		}

		IPath getLibPath() {
			return libPath;
		}

		@Override
		public int hashCode() {
			return Objects.hash(libPath, project);
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
			return Objects.equals(libPath, other.libPath) && Objects.equals(project, other.project);
		}

		IJavaProject getProject() {
			return project;
		}

	}

	public static UpdateClasspathJob getInstance() {
		return instance;
	}

}
