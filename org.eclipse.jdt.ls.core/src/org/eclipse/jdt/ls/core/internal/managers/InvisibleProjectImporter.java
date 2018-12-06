/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

public class InvisibleProjectImporter extends AbstractProjectImporter {

	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		IPath workspaceLocation = ResourcesPlugin.getWorkspace().getRoot().getLocation();
		IPath rootPath = ResourceUtils.filePathFromURI(rootFolder.toPath().toUri().toString());
		if (workspaceLocation.equals(rootPath)) {
			return false;
		}

		return ProjectUtils.getVisibleProjects(rootPath).isEmpty();
	}

	/**
	 * If it finds a java file somewhere under the root folder hierarchy. Then: 1)
	 * open the file to infer the package. 2) if package matches ancestor folders
	 * AND package is fully contained in root folder, then infer a source directory
	 * (parent of package dir) and create an invisible project out of it.
	 **/
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager == null || preferencesManager.getPreferences() == null) {
			return;
		}

		Collection<IPath> triggerFiles = preferencesManager.getPreferences().getTriggerFiles();
		if (triggerFiles == null || triggerFiles.isEmpty()) {
			return;
		}

		IPath rootPath = ResourceUtils.filePathFromURI(rootFolder.toPath().toUri().toString());
		Optional<IPath> triggerJavaFile = triggerFiles.stream().filter(triggerFile -> rootPath.isPrefixOf(triggerFile)).findFirst();
		if (!triggerJavaFile.isPresent()) {
			return;
		}

		String packageName = getPackageName(triggerJavaFile.get());
		IPath sourceDirectory = inferSourceDirectory(triggerJavaFile.get().toFile().toPath(), packageName);
		if (sourceDirectory == null || !rootPath.isPrefixOf(sourceDirectory)) {
			return;
		}

		String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(rootPath);
		IProject invisibleProject = ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);
		if (!invisibleProject.exists()) {
			try {
				JavaLanguageServerPlugin.logInfo("Try to create an invisible project for the workspace " + rootPath);
				invisibleProject = ProjectUtils.createInvisibleProjectIfNotExist(rootPath);
				IFolder workspaceLinkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);
				IPath relativeSourcePath = sourceDirectory.makeRelativeTo(rootPath);
				IPath sourcePath = relativeSourcePath.isEmpty() ? workspaceLinkFolder.getFullPath() : workspaceLinkFolder.getFolder(relativeSourcePath).getFullPath();
				List<IProject> subProjects = ProjectUtils.getVisibleProjects(rootPath);
				List<IPath> subProjectPaths = subProjects.stream().map(project -> {
					IPath relativePath = project.getLocation().makeRelativeTo(rootPath);
					return workspaceLinkFolder.getFolder(relativePath).getFullPath();
				}).collect(Collectors.toList());
				IJavaProject javaProject = JavaCore.create(invisibleProject);
				ProjectUtils.addSourcePath(sourcePath, subProjectPaths.toArray(new IPath[0]), javaProject);
				JavaLanguageServerPlugin.logInfo("Successfully created a workspace invisible project " + invisibleProjectName);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to create the invisible project.", e);
			}
		}
	}

	@Override
	public void reset() {
		// do nothing
	}

	private String getPackageName(IPath javaFile) {
		IProject project = JavaLanguageServerPlugin.getProjectsManager().getDefaultProject();
		if (project == null || !project.isAccessible()) {
			return "";
		}

		IJavaProject javaProject = JavaCore.create(project);
		return JDTUtils.getPackageName(javaProject, javaFile.toFile().toURI());
	}

	private IPath inferSourceDirectory(java.nio.file.Path filePath, String packageName) {
		String packagePath = packageName.replace(JDTUtils.PERIOD, JDTUtils.PATH_SEPARATOR);
		java.nio.file.Path sourcePath = filePath.getParent();
		if (StringUtils.isBlank(packagePath)) {
			return ResourceUtils.filePathFromURI(sourcePath.toUri().toString());
		} else if (sourcePath.endsWith(Paths.get(packagePath))) { // package should match ancestor folders.
			int packageCount = packageName.split("\\" + JDTUtils.PERIOD).length;
			while (packageCount > 0) {
				sourcePath = sourcePath.getParent();
				packageCount--;
			}
			return ResourceUtils.filePathFromURI(sourcePath.toUri().toString());
		}

		return null;
	}
}
