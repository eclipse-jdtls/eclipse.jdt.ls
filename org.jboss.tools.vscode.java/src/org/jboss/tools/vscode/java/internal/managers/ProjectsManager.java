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

import static org.jboss.tools.vscode.java.internal.ProjectUtils.isJavaProject;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.internal.StatusFactory;

public class ProjectsManager {

	public static final String DEFAULT_PROJECT_NAME= "jdt.ls-java-project";

	public enum CHANGE_TYPE { CREATED, CHANGED, DELETED};

	public IStatus createProject(final String projectName, List<IProject> resultingProjects, IProgressMonitor monitor) {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
		try {
			IProject defaultJavaProject = createJavaProject(subMonitor.split(10));
			resultingProjects.add(defaultJavaProject);

			File userProjectRoot = (projectName == null)?null:new File(projectName);

			IProjectImporter importer = getImporter(userProjectRoot, subMonitor.split(20));
			if (importer != null) {
				List<IProject> projects = importer.importToWorkspace(subMonitor.split(70));
				List<IProject> javaProjects = projects.stream().filter(p -> isJavaProject(p)).collect(Collectors.toList());
				resultingProjects.addAll(javaProjects);
			}

			JavaLanguageServerPlugin.logInfo("Number of created projects " + resultingProjects.size());
			return Status.OK_STATUS;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem importing to workspace", e);
			return StatusFactory.newErrorStatus("Import failed: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			JavaLanguageServerPlugin.logInfo("Import cancelled");
			return Status.CANCEL_STATUS;
		}
	}

	private static IWorkspaceRoot getWorkspaceRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	public void fileChanged(String uriString, CHANGE_TYPE changeType) {
		if (uriString == null) {
			return;
		}
		URI path;
		try {
			path = new URI(uriString);
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve "+uriString, e);
			return;
		}
		IFile[] resources = getWorkspaceRoot().findFilesForLocationURI(path);
		if (resources.length < 1) {
			return;
		}
		IResource resource = resources[0];
		try {
			if (changeType == CHANGE_TYPE.DELETED) {
				resource = resource.getParent();
			}
			if (resource != null) {
				resource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
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

	public IProject getDefaultProject() {
		return getWorkspaceRoot().getProject(DEFAULT_PROJECT_NAME);
	}

	private Collection<IProjectImporter> importers() {
		return Arrays.asList(new MavenProjectImporter(), new EclipseProjectImporter());
	}

	private IProject createJavaProject(IProgressMonitor monitor) throws CoreException, OperationCanceledException, InterruptedException {
		IProject project = getDefaultProject();
		if (project.exists()) {
			return project;
		}
		JavaLanguageServerPlugin.logInfo("Creating the default Java project");
		//Create project
		project.create(monitor);
		project.open(monitor);

		//Turn into Java project
		IProjectDescription description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, monitor);
		IJavaProject javaProject = JavaCore.create(project);

		//Add build output folder
		IFolder output = project.getFolder("bin");
		if (!output.exists()) {
			output.create(true, true, monitor);
		}
		javaProject.setOutputLocation(output.getFullPath(), monitor);

		//Add source folder
		IFolder source = project.getFolder("src");
		if (!source.exists()) {
			source.create(true, true, monitor);
		}
		IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(source);
		IClasspathEntry src =JavaCore.newSourceEntry(root.getPath());

		//Find default JVM
		IClasspathEntry jre = JavaRuntime.getDefaultJREContainerEntry();

		//Add JVM to project class path
		javaProject.setRawClasspath(new IClasspathEntry[]{jre, src} , monitor);

		JavaLanguageServerPlugin.logInfo("Finished creating the default Java project");
		return project;
	}


}
