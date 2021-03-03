/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.BuildSupportManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

public class BuildPathCommand {
	private static final String UNSUPPORTED_OPERATION = "Unsupported operation. Please use your build tool project file to manage the source directories of the project.";
	public static final String UNSUPPORTED_ON_MAVEN = "Unsupported operation. Please use pom.xml file to manage the source directories of maven project.";
	public static final String UNSUPPORTED_ON_GRADLE = "Unsupported operation. Please use build.gradle file to manage the source directories of gradle project.";

	// In most cases it will be either maven or gradle.
	// But if there is support for other build systems such as bazel, then we will need
	// a more generic message.
	private static String unsupportedOperationMessage(IProject targetProject) {
		if (BuildSupportManager.obtainBuildSupports().size() == 2) {
			// Case for built-in maven/gradle support
			return BuildSupportManager.find("Gradle").get().applies(targetProject) ? UNSUPPORTED_ON_GRADLE : UNSUPPORTED_ON_MAVEN;

		} else {
			// If other build tools supported
			return UNSUPPORTED_OPERATION;
		}
	}

	public static Result addToSourcePath(String sourceFolderUri) {
		IPath sourceFolderPath = ResourceUtils.filePathFromURI(sourceFolderUri);
		IProject targetProject = findBelongedProject(sourceFolderPath);

		if (targetProject != null && !ProjectUtils.isGeneralJavaProject(targetProject)) {
			return new Result(false, unsupportedOperationMessage(targetProject));
		}

		IPath projectLocation = null;
		IContainer projectRootResource = null;
		IPath[] exclusionPath = new IPath[0];
		if (targetProject == null) {
			try {
				IPath workspaceRoot = ProjectUtils.findBelongedWorkspaceRoot(sourceFolderPath);
				if (workspaceRoot == null) {
					return new Result(false, Messages.format("The folder ''{0}'' doesn''t belong to any workspace.", getWorkspacePath(sourceFolderPath).toOSString()));
				}

				targetProject = ProjectUtils.createInvisibleProjectIfNotExist(workspaceRoot);
				final IFolder workspaceLink = targetProject.getFolder(ProjectUtils.WORKSPACE_LINK);
				projectLocation = workspaceRoot;
				projectRootResource = workspaceLink;
				List<IProject> subProjects = ProjectUtils.getVisibleProjects(workspaceRoot);
				exclusionPath = subProjects.stream().map(project -> {
					IPath relativePath = project.getLocation().makeRelativeTo(workspaceRoot);
					return workspaceLink.getFolder(relativePath).getFullPath();
				}).toArray(IPath[]::new);
			} catch (OperationCanceledException | CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to create the invisible project.", e);
				return new Result(false, "Failed to add the folder to the workspace invisible project's source path. Reason: " + e.getMessage());
			}
		} else {
			projectLocation = targetProject.getLocation();
			projectRootResource = targetProject;
		}

		IPath relativeSourcePath = sourceFolderPath.makeRelativeTo(projectLocation);
		IPath sourcePath = relativeSourcePath.isEmpty() ? projectRootResource.getFullPath() : projectRootResource.getFolder(relativeSourcePath).getFullPath();
		IJavaProject javaProject = JavaCore.create(targetProject);
		try {
			if (ProjectUtils.addSourcePath(sourcePath, exclusionPath, javaProject)) {
				return new Result(true, Messages.format("Successfully added ''{0}'' to the project {1}''s source path.", new String[] { getWorkspacePath(sourceFolderPath).toOSString(), targetProject.getName() }));
			} else {
				return new Result(true, Messages.format("No need to add it to source path again, because the folder ''{0}'' is already in the project {1}''s source path.",
						new String[] { getWorkspacePath(sourceFolderPath).toOSString(), targetProject.getName() }));
			}
		} catch (CoreException e) {
			return new Result(false, e.getMessage());
		}

	}

	public static Result removeFromSourcePath(String sourceFolderUri) {
		IPath sourceFolderPath = ResourceUtils.filePathFromURI(sourceFolderUri);
		IProject targetProject = findBelongedProject(sourceFolderPath);
		if (targetProject != null && !ProjectUtils.isGeneralJavaProject(targetProject)) {
			return new Result(false, unsupportedOperationMessage(targetProject));
		}

		IPath projectLocation = null;
		IContainer projectRootResource = null;
		if (targetProject == null) {
			IPath workspaceRoot = ProjectUtils.findBelongedWorkspaceRoot(sourceFolderPath);
			if (workspaceRoot == null) {
				return new Result(false, Messages.format("The folder ''{0}'' doesn''t belong to any workspace.", getWorkspacePath(sourceFolderPath).toOSString()));
			}

			String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(workspaceRoot);
			targetProject = ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);
			if (!targetProject.exists()) {
				return new Result(true, Messages.format("No need to remove it from source path, because the folder ''{0}'' isn''t on any project''s source path.", getWorkspacePath(sourceFolderPath).toOSString()));
			}

			projectLocation = workspaceRoot;
			projectRootResource = targetProject.getFolder(ProjectUtils.WORKSPACE_LINK);
		} else {
			projectLocation = targetProject.getLocation();
			projectRootResource = targetProject;
		}

		IPath relativeSourcePath = sourceFolderPath.makeRelativeTo(projectLocation);
		IPath sourcePath = relativeSourcePath.isEmpty() ? projectRootResource.getFullPath() : projectRootResource.getFolder(relativeSourcePath).getFullPath();
		IJavaProject javaProject = JavaCore.create(targetProject);
		try {
			if (ProjectUtils.removeSourcePath(sourcePath, javaProject)) {
				return new Result(true, Messages.format("Successfully removed ''{0}'' from the project {1}''s source path.", new String[] { getWorkspacePath(sourceFolderPath).toOSString(), targetProject.getName() }));
			} else {
				return new Result(true, Messages.format("No need to remove it from source path, because the folder ''{0}'' isn''t on any project''s source path.", getWorkspacePath(sourceFolderPath).toOSString()));
			}
		} catch (CoreException e) {
			return new Result(false, e.getMessage());
		}
	}

	public static Result listSourcePaths() {
		try {
			List<SourcePath> sourcePathList = new ArrayList<>();
			IProject[] projects = ProjectUtils.getAllProjects();
			for (IProject project : projects) {
				if (!ProjectsManager.DEFAULT_PROJECT_NAME.equals(project.getName()) && ProjectUtils.isJavaProject(project)) {
					sourcePathList.addAll(tryAddProjectToSourcePath(project));
				}
			}

			return new ListCommandResult(true, null, sourcePathList.toArray(new SourcePath[0]));

		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve the existing source paths in current workspace.", e);
			return new ListCommandResult(false, e.getMessage());
		}
	}

	private static List<SourcePath> tryAddProjectToSourcePath(IProject project) throws JavaModelException {
		List<SourcePath> sourcePathList = new ArrayList<>();
		IPath[] paths = ProjectUtils.listSourcePaths(JavaCore.create(project));

		for (IPath path : paths) {
			IPath entryPath = path;
			String projectName = project.getName();
			String projectType = determineProjectType(project);

			IContainer projectRoot = project;
			if (!ProjectUtils.isVisibleProject(project)) {
				projectType = "Workspace";
				IFolder workspaceLinkFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK);
				if (!workspaceLinkFolder.isLinked()) {
					continue;
				}

				projectRoot = workspaceLinkFolder;
			}

			IPath relativePath = entryPath.makeRelativeTo(projectRoot.getFullPath());
			IPath location = projectRoot.getRawLocation().append(relativePath);
			IPath displayPath = getWorkspacePath(location);

			sourcePathList.add(new SourcePath(location != null ? location.toOSString() : "", displayPath != null ? displayPath.toOSString() : entryPath.toOSString(), entryPath.toOSString(), projectName, projectType));
		}

		return sourcePathList;
	}

	private static String determineProjectType(IProject project) {
		return BuildSupportManager
				.find(project)
				.map(buildSupport -> buildSupport.buildToolName())
				.orElseGet(() -> "General");
	}

	private static IProject findBelongedProject(IPath sourceFolder) {
		List<IProject> projects = Stream.of(ProjectUtils.getAllProjects()).filter(ProjectUtils::isJavaProject).sorted(new Comparator<IProject>() {
			@Override
			public int compare(IProject p1, IProject p2) {
				return p2.getLocation().toOSString().length() - p1.getLocation().toOSString().length();
			}
		}).collect(Collectors.toList());

		for (IProject project : projects) {
			if (project.getLocation().isPrefixOf(sourceFolder)) {
				return project;
			}
		}

		return null;
	}

	private static IPath getWorkspacePath(IPath path) {
		PreferenceManager manager = JavaLanguageServerPlugin.getPreferencesManager();
		Collection<IPath> rootPaths = manager.getPreferences().getRootPaths();
		if (rootPaths != null) {
			for (IPath rootPath : rootPaths) {
				if (rootPath.isPrefixOf(path)) {
					return path.makeRelativeTo(rootPath.append(".."));
				}
			}
		}

		return path;
	}

	public static class Result {
		public boolean status;
		public String message;

		Result(boolean status, String message) {
			this.status = status;
			this.message = message;
		}
	}

	public static class ListCommandResult extends Result {
		public SourcePath[] data;

		ListCommandResult(boolean status, String message) {
			super(status, message);
			data = new SourcePath[0];
		}

		ListCommandResult(boolean status, String message, SourcePath[] data) {
			super(status, message);
			this.data = data;
		}
	}

	public static class SourcePath {
		public String path;
		public String displayPath;
		public String classpathEntry;
		public String projectName;
		public String projectType;

		SourcePath(String path, String displayPath, String classpathEntry, String projectName, String projectType) {
			this.path = path;
			this.displayPath = displayPath;
			this.classpathEntry = classpathEntry;
			this.projectName = projectName;
			this.projectType = projectType;
		}
	}
}
