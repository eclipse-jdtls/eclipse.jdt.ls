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

import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

import com.google.common.collect.Sets;

public class EclipseBuildSupport implements IBuildSupport {

	private Set<String> files = Sets.newHashSet(".classpath", ".project", ".factorypath");
	private Set<String> folders = Sets.newHashSet(".settings");

	@Override
	public boolean applies(IProject project) {
		return true; //all projects are Eclipse projects
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		if (resource == null || resource.getProject() == null) {
			return false;
		}
		IProject project = resource.getProject();
		for (String file : files) {
			if (resource.equals(project.getFile(file))) {
				return true;
			}
		}
		IPath path = resource.getFullPath();
		for (String folder : folders) {
			IPath folderPath = project.getFolder(folder).getFullPath();
			if (folderPath.isPrefixOf(path)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor) throws CoreException {
		if (resource == null || !applies(resource.getProject())) {
			return false;
		}
		refresh(resource, changeType, monitor);
		IProject project = resource.getProject();
		if (ProjectUtils.isJavaProject(project)) {
			IJavaProject javaProject = JavaCore.create(project);
			IClasspathEntry[] classpath = javaProject.getRawClasspath();
			for (IClasspathEntry entry : classpath) {
				if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
					IPath path = entry.getPath();
					IFile r = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
					if (r != null && r.equals(resource)) {
						IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
						javaProject.setRawClasspath(new IClasspathEntry[0], monitor);
						javaProject.setRawClasspath(rawClasspath, monitor);
						break;
					}
				}
			}
		}
		return false;
	}

	@Override
	public void discoverSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		boolean shouldDiscoverSources = (classFile.getJavaProject() != null && Objects.equals(ProjectsManager.getDefaultProject(), classFile.getJavaProject().getProject()));

		if (!shouldDiscoverSources) {
			PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
			shouldDiscoverSources = preferencesManager != null && preferencesManager.getPreferences().isEclipseDownloadSources();
		}
		if (shouldDiscoverSources) {
			JavaLanguageServerPlugin.getDefaultSourceDownloader().discoverSource(classFile, monitor);
		}
	}


}
