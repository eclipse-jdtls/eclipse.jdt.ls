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
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

public class EclipseProjectImporter extends AbstractProjectImporter {

	/**
	 * The name of the folder containing metadata information for the workspace.
	 */
	public static final String METADATA_FOLDER = ".metadata"; //$NON-NLS-1$

	private Collection<File> projectFiles = null;

	@Override
	public boolean applies(IProgressMonitor monitor) throws InterruptedException {
		Collection<File> files = getProjectFiles(monitor);
		return files != null && !files.isEmpty();
	}

	synchronized Collection<File> getProjectFiles(IProgressMonitor monitor) throws InterruptedException {
		if (projectFiles == null) {
			projectFiles = collectProjectFiles(monitor);
		}
		return projectFiles;
	}

	Collection<File> collectProjectFiles(IProgressMonitor monitor) throws InterruptedException {
		Set<File> files = new LinkedHashSet<>();
		Set<String> visitedDirectories = new HashSet<>();
		collectProjectFilesFromDirectory(files, rootFolder, visitedDirectories, true, monitor);
		return files;
	}

	@Override
	public void reset() {
		projectFiles = null;
	}


	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException, InterruptedException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
		Collection<File> files = getProjectFiles(subMonitor.split(5));
		if (files == null || files.isEmpty()) {
			return;
		}
		JavaLanguageServerPlugin.logInfo("Importing Eclipse project(s)");
		int projectSize = files.size();
		subMonitor.setWorkRemaining(projectSize);
		for(File file : files) {
			if (monitor.isCanceled()) {
				throw new InterruptedException();
			}
			createProject(file, subMonitor.split(1));
		}
		subMonitor.done();
	}

	IProject createProject(File file, IProgressMonitor m) throws CoreException {
		SubMonitor monitor = SubMonitor.convert(m, 100);
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IPath dotProjectPath = new Path(file.getAbsolutePath());
		IProjectDescription descriptor = workspace.loadProjectDescription(dotProjectPath);
		String name = descriptor.getName();
		if (!descriptor.hasNature(JavaCore.NATURE_ID)) {
			return null;
		}
		IProject project = workspace.getRoot().getProject(name);
		if (project.exists()) {
			IPath existingProjectPath = project.getLocation();
			if (existingProjectPath.equals(dotProjectPath.removeLastSegments(1))) {
				project.open(IResource.NONE, monitor);
				project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
				return project;
			} else {
				project = findUniqueProject(workspace, name);
				descriptor.setName(project.getName());
			}
		}
		project.create(descriptor, monitor);
		project.open(IResource.NONE, monitor);
		monitor.done();
		return project;
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

	/**
	 * Collect the list of .project files that are under directory into files.
	 *
	 * Copied from org.eclipse.ui.internal.wizards.datatransfer.WizardProjectsImportPage
	 *
	 * @param files
	 * @param directory
	 * @param directoriesVisited
	 *            Set of canonical paths of directories, used as recursion guard
	 * @param nestedProjects
	 *            whether to look for nested projects
	 * @param monitor
	 *            The monitor to report to
	 * @return boolean <code>true</code> if the operation was completed.
	 * @throws InterruptedException
	 */
	static boolean collectProjectFilesFromDirectory(Collection<File> files, File directory,
			Set<String> directoriesVisited, boolean nestedProjects, IProgressMonitor monitor) throws InterruptedException {

		if (monitor.isCanceled()) {
			throw new InterruptedException();
		}
		if (directory == null) {
			return true;
		}
		//monitor.subTask(NLS.bind(
		//		DataTransferMessages.WizardProjectsImportPage_CheckingMessage,
		//		directory.getPath()));
		File[] contents = directory.listFiles();
		if (contents == null) {
			return false;
		}

		// Initialize recursion guard for recursive symbolic links
		if (directoriesVisited == null) {
			directoriesVisited = new HashSet<>();
			try {
				directoriesVisited.add(directory.getCanonicalPath());
			} catch (IOException exception) {
				//StatusManager.getManager().handle(
				//		StatusUtil.newStatus(IStatus.ERROR, exception
				//				.getLocalizedMessage(), exception));
			}
		}

		// first look for project description files
		final String dotProject = IProjectDescription.DESCRIPTION_FILE_NAME;
		List<File> directories = new ArrayList<>();
		for (File file : contents) {
			if(file.isDirectory()){
				directories.add(file);
			} else if (file.getName().equals(dotProject) && file.isFile()) {
				files.add(file);
				if (!nestedProjects) {
					// don't search sub-directories since we can't have nested
					// projects
					return true;
				}
			}
		}
		// no project description found or search for nested projects enabled,
		// so recurse into sub-directories
		for (File dir : directories) {
			if (!dir.getName().equals(METADATA_FOLDER)) {
				try {
					String canonicalPath = dir.getCanonicalPath();
					if (!directoriesVisited.add(canonicalPath)) {
						// already been here --> do not recurse
						continue;
					}
				} catch (IOException exception) {
					//StatusManager.getManager().handle(
					//		StatusUtil.newStatus(IStatus.ERROR, exception
					//				.getLocalizedMessage(), exception));

				}
				collectProjectFilesFromDirectory(files, dir,
						directoriesVisited, nestedProjects, monitor);
			}
		}
		return true;
	}
}