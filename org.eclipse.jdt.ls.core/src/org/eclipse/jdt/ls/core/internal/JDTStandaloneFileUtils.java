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
package org.eclipse.jdt.ls.core.internal;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class JDTStandaloneFileUtils {
	public static final String PATH_SEPARATOR = "/";
	public static final String PERIOD = ".";
	public static final String SRC = "src";

	public static ICompilationUnit getFakeCompilationUnit(URI uri, IProgressMonitor monitor) {
		if (uri == null || !"file".equals(uri.getScheme()) || !uri.getPath().endsWith(".java")) {
			return null;
		}
		java.nio.file.Path path = Paths.get(uri);
		//Only support existing standalone java files
		if (!java.nio.file.Files.isReadable(path)) {
			return null;
		}

		IPath workspaceRoot = ProjectUtils.findBelongedWorkspaceRoot(ResourceUtils.filePathFromURI(uri.toString()));
		if (workspaceRoot != null) {
			return getFakeCompilationUnitForInsideStandaloneFile(uri, workspaceRoot, monitor);
		} else {
			return getFakeCompilationUnitForExternalStandaloneFile(uri, monitor);
		}
	}

	private static ICompilationUnit getFakeCompilationUnitForExternalStandaloneFile(URI uri, IProgressMonitor monitor) {
		IProject project = JavaLanguageServerPlugin.getProjectsManager().getDefaultProject();
		if (project == null || !project.isAccessible()) {
			return null;
		}

		IJavaProject javaProject = JavaCore.create(project);
		String packageName = JDTUtils.getPackageName(javaProject, uri);
		java.nio.file.Path path = Paths.get(uri);
		String fileName = path.getName(path.getNameCount() - 1).toString();
		String packagePath = packageName.replace(PERIOD, PATH_SEPARATOR);
		IPath filePath = new Path(SRC).append(packagePath).append(fileName);
		final IFile file = project.getFile(filePath);
		if (!file.isLinked()) {
			try {
				JDTUtils.createFolders(file.getParent(), monitor);
				file.createLink(uri, IResource.REPLACE, monitor);
			} catch (CoreException e) {
				String errMsg = "Failed to create linked resource from " + uri + " to " + project.getName();
				JavaLanguageServerPlugin.logException(errMsg, e);
			}
		}

		if (file.isLinked()) {
			return (ICompilationUnit) JavaCore.create(file, javaProject);
		}

		return null;
	}

	private static ICompilationUnit getFakeCompilationUnitForInsideStandaloneFile(URI uri, IPath workspaceRoot, IProgressMonitor monitor) {
		java.nio.file.Path path = Paths.get(uri);
		if (!ProjectUtils.getVisibleProjects(workspaceRoot).isEmpty()) {
			return null;
		}

		// Try to resolve the CompilationUnit from the workspace root associated invisible project.
		String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(workspaceRoot);
		IProject invisibleProject = ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);
		if (!invisibleProject.exists()) {
			try {
				invisibleProject = ProjectUtils.createInvisibleProjectIfNotExist(workspaceRoot);
				IJavaProject javaProject = JavaCore.create(invisibleProject);
				IFolder workspaceLinkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);
				try {
					// Mark the containing folder of the opened file as Source Root of the invisible project.
					String packageName = JDTUtils.getPackageName(javaProject, uri);
					IPath containerPath = getContainingFolderPath(path, packageName);
					IPath containerRelativePath = new Path("");
					if (workspaceRoot.isPrefixOf(containerPath)) {
						containerRelativePath = containerPath.makeRelativeTo(workspaceRoot);
					}
					IPath sourcePath = containerRelativePath.isEmpty() ? workspaceLinkFolder.getFullPath() : workspaceLinkFolder.getFolder(containerRelativePath).getFullPath();
					List<IProject> subProjects = ProjectUtils.getVisibleProjects(workspaceRoot);
					List<IPath> subProjectPaths = subProjects.stream().map(project -> {
						IPath relativePath = project.getLocation().makeRelativeTo(workspaceRoot);
						return workspaceLinkFolder.getFolder(relativePath).getFullPath();
					}).collect(Collectors.toList());
					ProjectUtils.addSourcePath(sourcePath, subProjectPaths.toArray(new IPath[0]), javaProject);
				} catch (JavaModelException e) {
					String errMsg = "Failed to update classpath to the invisible project " + invisibleProject.getName() + " .";
					JavaLanguageServerPlugin.logException(errMsg, e);
					return null;
				}

				IPath fileRelativePath = ResourceUtils.filePathFromURI(uri.toString()).makeRelativeTo(workspaceRoot);
				final IFile file = workspaceLinkFolder.getFile(fileRelativePath);
				return (ICompilationUnit) JavaCore.create(file, javaProject);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to create the invisible project.", e);
				return null;
			}
		}

		return null;
	}

	private static IPath getContainingFolderPath(java.nio.file.Path filePath, String packageName) {
		String packagePath = packageName.replace(PERIOD, PATH_SEPARATOR);
		java.nio.file.Path sourcePath = filePath.getParent();
		if (StringUtils.isNotBlank(packagePath) && sourcePath.endsWith(Paths.get(packagePath))) {
			int packageCount = packageName.split("\\" + PERIOD).length;
			while (packageCount > 0) {
				sourcePath = sourcePath.getParent();
				packageCount--;
			}
		}

		return ResourceUtils.filePathFromURI(sourcePath.toUri().toString());
	}
}
