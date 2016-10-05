/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.managers;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.internal.StatusFactory;

public class ProjectsManager {

	public enum CHANGE_TYPE { CREATED, CHANGED, DELETED};

	public IStatus createProject(final String projectName, List<IProject> resultingProjects, IProgressMonitor monitor) {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
		try {
			File projectRoot = (projectName == null)?null:new File(projectName);
			IProjectImporter importer = getImporter(projectRoot, subMonitor.split(20));
			if (importer == null) {
				return StatusFactory.UNSUPPORTED_PROJECT;
			}
			List<IProject> projects = importer.importToWorkspace(subMonitor.split(80));

			List<IProject> javaProjects = projects.stream().filter(p -> {
				try {
					return p.hasNature(JavaCore.NATURE_ID);
				} catch (CoreException e) {
					return false;
				}
			}).collect(Collectors.toList());

			JavaLanguageServerPlugin.logInfo("Number of created projects " + javaProjects.size());
			resultingProjects.addAll(javaProjects);
			return Status.OK_STATUS;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem importing to workspace", e);
			return StatusFactory.newErrorStatus("Import failed: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			JavaLanguageServerPlugin.logInfo("Import cancelled");
			return Status.CANCEL_STATUS;
		}
	}

	private IWorkspaceRoot getWorkspaceRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	public void fileChanged(String uri, CHANGE_TYPE changeType) {
		String path = null;
		try {
			path = new URI(uri).getPath();
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve "+uri, e);
		}
		IFile resource = getWorkspaceRoot().getFileForLocation(Path.fromOSString(path));
		if (resource == null) {
			return;
		}
		IResource toRefresh = resource;
		try {
			if (changeType == CHANGE_TYPE.DELETED) {
				toRefresh = resource.getParent();
			}
			if (toRefresh != null) {
				toRefresh.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem refreshing workspace", e);
		}
	}

	private IProjectImporter getImporter(File rootFolder, IProgressMonitor monitor) throws InterruptedException, CoreException {
		Collection<IProjectImporter> importers = importers();
		SubMonitor subMonitor = SubMonitor.convert(monitor, importers.size());
		for (IProjectImporter importer : importers) {
			importer.initialize(rootFolder);
			if (importer.applies(subMonitor.split(1))) {
				return importer;
			}
		}
		return null;
	}

	private Collection<IProjectImporter> importers() {
		//TODO read extension point
		return Arrays.asList(new MavenProjectImporter(), new EclipseProjectImporter());
	}
}
