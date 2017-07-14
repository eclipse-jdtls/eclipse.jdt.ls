/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.importer.pde.internal;

import static org.eclipse.core.resources.IProjectDescription.DESCRIPTION_FILE_NAME;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.JavaCore;

public class EclipseProjectImporter {

	public void importDir(java.nio.file.Path dir, IProgressMonitor m) throws CoreException {
		SubMonitor monitor = SubMonitor.convert(m, 4);
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IPath dotProjectPath = new Path(dir.resolve(DESCRIPTION_FILE_NAME).toAbsolutePath().toString());
		IProjectDescription descriptor;
		try {
			descriptor = workspace.loadProjectDescription(dotProjectPath);
			String name = descriptor.getName();
			if (!descriptor.hasNature(JavaCore.NATURE_ID)) {
				return;
			}
			IProject project = workspace.getRoot().getProject(name);
			if (project.exists()) {
				IPath existingProjectPath = project.getLocation();
				existingProjectPath = fixDevice(existingProjectPath);
				dotProjectPath = fixDevice(dotProjectPath);
				if (existingProjectPath.equals(dotProjectPath.removeLastSegments(1))) {
					project.open(IResource.NONE, monitor.split(1));
					project.refreshLocal(IResource.DEPTH_INFINITE, monitor.split(1));
					return;
				} else {
					project = findUniqueProject(workspace, name);
					descriptor.setName(project.getName());
				}
			}
			project.create(descriptor, monitor.split(1));
			project.open(IResource.NONE, monitor.split(1));
		} finally {
			monitor.done();
		}
	}

	private IPath fixDevice(IPath path) {
		if (path != null && path.getDevice() != null) {
			return path.setDevice(path.getDevice().toUpperCase());
		}
		return path;
	}

	private IProject findUniqueProject(IWorkspace workspace, String basename) {
		IProject project = null;
		String name;
		for (int i = 1; project == null || project.exists(); i++) {
			name = (i < 2) ? basename : basename + " (" + i + ")";
			project = workspace.getRoot().getProject(name);
		}
		return project;
	}

}