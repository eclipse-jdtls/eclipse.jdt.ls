/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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

import static org.eclipse.core.resources.IProjectDescription.DESCRIPTION_FILE_NAME;

import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

public class EclipseProjectImporter extends AbstractProjectImporter {

	@Override
	public boolean applies(IProgressMonitor monitor) throws CoreException {
		if (directories == null) {
			BasicFileDetector eclipseDetector = new BasicFileDetector(rootFolder.toPath(), DESCRIPTION_FILE_NAME)
					.addExclusions("**/bin");//default Eclipse build dir
			for (IProject project : ProjectUtils.getAllProjects(false)) {
				File projectFile = project.getLocation().toFile();
				eclipseDetector.addExclusions(projectFile.getAbsolutePath());
			}
			directories = eclipseDetector.scan(monitor);
		}
		directories = directories.stream().filter(path -> (new File(path.toFile(), IJavaProject.CLASSPATH_FILE_NAME).exists())).collect(Collectors.toList());
		return !directories.isEmpty();
	}

	@Override
	public void reset() {
		directories = null;
	}

	public void setDirectories(Collection<java.nio.file.Path> paths) {
		directories = paths;
	}


	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException {
		if (!applies(monitor)) {
			return;
		}
		SubMonitor subMonitor = SubMonitor.convert(monitor, directories.size());
		JavaLanguageServerPlugin.logInfo("Importing Eclipse project(s)");
		directories.forEach(d -> importDir(d, subMonitor.newChild(1)));
		subMonitor.done();
	}

	private void importDir(java.nio.file.Path dir, IProgressMonitor m) {
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
					project.open(IResource.NONE, monitor.newChild(1));
					project.refreshLocal(IResource.DEPTH_INFINITE, monitor.newChild(1));
					return;
				} else {
					project = findUniqueProject(workspace, name);
					descriptor.setName(project.getName());
				}
			}
			project.create(descriptor, monitor.newChild(1));
			project.open(IResource.NONE, monitor.newChild(1));
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e.getStatus());
			throw new RuntimeException(e);
		} finally {
			monitor.done();
		}
	}

	private IPath fixDevice(IPath path) {
		if (path != null && path.getDevice() != null) {
			return path.setDevice(path.getDevice().toUpperCase());
		}
		if (Platform.OS_WIN32.equals(Platform.getOS()) && path != null && path.toString().startsWith("//")) {
			String server = path.segment(0);
			String pathStr = path.toString().replace(server, server.toUpperCase());
			return new Path(pathStr);
		}
		return path;
	}

	//XXX should be package protected. Temporary fix (ahaha!) until test fragment can work in tycho builds
	public IProject findUniqueProject(IWorkspace workspace, String basename) {
		IProject project = null;
		String name;
		for (int i = 1; project == null || project.exists(); i++) {
			name = (i < 2)? basename:basename + " ("+ i +")";
			project = workspace.getRoot().getProject(name);
		}
		return project;
	}

}