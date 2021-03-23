/*******************************************************************************
 * Copyright (c) 2018-2021 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

public class InvisibleProjectImporter extends AbstractProjectImporter {
	public static final String[][] SRC_PREFIXES = new String[][] {
		{ "src" }
	};

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

		loadInvisibleProject(triggerJavaFile.get(), rootPath, true, monitor);
	}

	@Override
	public void reset() {
		// do nothing
	}

	/**
	 * Based on the trigger file, check whether to load the invisible project to
	 * manage it. Return true if an invisible project is enabled.
	 * 
	 * @throws CoreException
	 */
	public static boolean loadInvisibleProject(IPath javaFile, IPath rootPath, boolean forceUpdateLibPath, IProgressMonitor monitor) throws CoreException {
		if (!ProjectUtils.getVisibleProjects(rootPath).isEmpty()) {
			return false;
		}

		String packageName = getPackageName(javaFile, rootPath);
		IPath sourceDirectory = inferSourceDirectory(javaFile.toFile().toPath(), packageName);
		if (sourceDirectory == null || !rootPath.isPrefixOf(sourceDirectory)
				|| isPartOfMatureProject(sourceDirectory)) {
			return false;
		}

		String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(rootPath);
		IProject invisibleProject = ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);

		if (!invisibleProject.exists()) {
			try {
				JavaLanguageServerPlugin.logInfo("Try to create an invisible project for the workspace " + rootPath);
				invisibleProject = ProjectUtils.createInvisibleProjectIfNotExist(rootPath);
				forceUpdateLibPath = true;
				JavaLanguageServerPlugin.logInfo("Successfully created a workspace invisible project " + invisibleProjectName);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to create the invisible project.", e);
				return false;
			}
		}

		IJavaProject javaProject = JavaCore.create(invisibleProject);

		IFolder workspaceLinkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		List<String> sourcePathsFromPreferences = null;
		if (preferencesManager != null && preferencesManager.getPreferences() != null) {
			sourcePathsFromPreferences = preferencesManager.getPreferences().getInvisibleProjectSourcePaths();
		}
		List<IPath> sourcePaths;
		if (sourcePathsFromPreferences != null) {
			sourcePaths = getSourcePaths(sourcePathsFromPreferences, workspaceLinkFolder);
		} else {
			IPath relativeSourcePath = sourceDirectory.makeRelativeTo(rootPath);
			IPath sourcePath = workspaceLinkFolder.getFolder(relativeSourcePath).getFullPath();
			sourcePaths = Arrays.asList(sourcePath);
		}

		List<IPath> excludingPaths = getExcludingPath(javaProject, rootPath, workspaceLinkFolder);

		IPath outputPath = getOutputPath(javaProject, preferencesManager.getPreferences().getInvisibleProjectOutputPath(), false /*isUpdate*/);

		IClasspathEntry[] classpathEntries = resolveClassPathEntries(javaProject, sourcePaths, excludingPaths, outputPath);
		javaProject.setRawClasspath(classpathEntries, outputPath, monitor);

		if (forceUpdateLibPath && preferencesManager != null && preferencesManager.getPreferences() != null) {
			UpdateClasspathJob.getInstance().updateClasspath(javaProject, preferencesManager.getPreferences().getReferencedLibraries());
		}

		return true;
	}


	public static IClasspathEntry[] resolveClassPathEntries(IJavaProject javaProject, List<IPath> sourcePaths, List<IPath> excludingPaths, IPath outputPath) throws CoreException {
		List<IClasspathEntry> newEntries = new LinkedList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
				newEntries.add(entry);
			}
		}

		// Sort the source paths to make the child folders come first
		Collections.sort(sourcePaths, new Comparator<IPath>() {
			@Override
			public int compare(IPath path1, IPath path2) {
				return path1.toString().compareTo(path2.toString()) * -1;
			}
		});

		List<IClasspathEntry> sourceEntries = new LinkedList<>();
		for (IPath currentPath : sourcePaths) {
			boolean canAddToSourceEntries = true;
			List<IPath> exclusionPatterns = new ArrayList<>();
			for (IClasspathEntry sourceEntry : sourceEntries) {
				if (Objects.equals(sourceEntry.getPath(), currentPath)) {
					JavaLanguageServerPlugin.logError("Skip duplicated source path: " + currentPath.toString());
					canAddToSourceEntries = false;
					break;
				}

				if (currentPath.isPrefixOf(sourceEntry.getPath())) {
					exclusionPatterns.add(sourceEntry.getPath().makeRelativeTo(currentPath).addTrailingSeparator());
				}
			}

			if (currentPath.equals(outputPath)) {
				throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "The output path cannot be equal to the source path, please provide a new path."));
			} else if (currentPath.isPrefixOf(outputPath)) {
				exclusionPatterns.add(outputPath.makeRelativeTo(currentPath).addTrailingSeparator());
			} else if (outputPath.isPrefixOf(currentPath)) {
				throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "The specified output path contains source folders, please provide a new path instead."));
			}

			if (canAddToSourceEntries) {
				if (excludingPaths != null) {
					for (IPath exclusion : excludingPaths) {
						if (currentPath.isPrefixOf(exclusion) && !currentPath.equals(exclusion)) {
							exclusionPatterns.add(exclusion.makeRelativeTo(currentPath).addTrailingSeparator());
						}
					}
				}
				sourceEntries.add(JavaCore.newSourceEntry(currentPath, exclusionPatterns.toArray(IPath[]::new)));
			}
		}
		newEntries.addAll(sourceEntries);
		return newEntries.toArray(IClasspathEntry[]::new);
	}

	public static IPath getOutputPath(IJavaProject javaProject, String outputPath, boolean isUpdate) throws CoreException {
		if (outputPath == null) {
			outputPath = "";
		} else {
			outputPath = outputPath.trim();
		}
		
		if (new org.eclipse.core.runtime.Path(outputPath).isAbsolute()) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "The output path must be a relative path to the workspace."));
		}

		IProject project = javaProject.getProject();
		if (StringUtils.isEmpty(outputPath)) {
			// blank means using default output path
			return javaProject.getProject().getFolder("bin").getFullPath();
		}

		outputPath = ProjectUtils.WORKSPACE_LINK + IPath.SEPARATOR + outputPath;
		IPath outputFullPath = project.getFolder(outputPath).getFullPath();
		if (javaProject.getOutputLocation().equals(outputFullPath)) {
			return outputFullPath;
		}
	
		File outputDirectory = project.getFolder(outputPath).getLocation().toFile();
		// Avoid popping too much dialogs during activation, only show the error dialog when it's updated
		if (isUpdate && outputDirectory.exists() && outputDirectory.list().length != 0) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Cannot set the output path to a folder which is not empty, please provide a new path."));
		}

		return outputFullPath;
	}

	public static List<IPath> getSourcePaths(List<String> sourcePaths, IFolder workspaceLinkFolder) throws CoreException {
		if (sourcePaths == null) {
			return Collections.emptyList();
		}

		sourcePaths = sourcePaths.stream()
				.map(path -> path.trim())
				.distinct()
				.collect(Collectors.toList());

		List<IPath> sourceList = new LinkedList<>();
		for (String sourcePath : sourcePaths) {
			if (new org.eclipse.core.runtime.Path(sourcePath).isAbsolute()) {
				throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "The source path must be a relative path to the workspace."));
			}
			IFolder sourceFolder = workspaceLinkFolder.getFolder(sourcePath);
			if (sourceFolder.exists()) {
				sourceList.add(sourceFolder.getFullPath());
			}
		}
		return sourceList;
	}

	public static List<IPath> getExcludingPath(IJavaProject javaProject, IPath rootPath, IFolder workspaceLinkFolder) throws CoreException {
		if (rootPath == null) {
			rootPath = ProjectUtils.findBelongedWorkspaceRoot(workspaceLinkFolder.getLocation());
		}

		if (rootPath == null) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Failed to find the belonging root of the linked folder: " + workspaceLinkFolder.toString()));
		}

		final IPath root = rootPath;
		IPath libFolder = new Path(InvisibleProjectBuildSupport.LIB_FOLDER);
		List<IProject> subProjects = ProjectUtils.getVisibleProjects(rootPath);
		List<IPath> excludingPaths = subProjects.stream().map(project -> {
			IPath relativePath = project.getLocation().makeRelativeTo(root);
			return workspaceLinkFolder.getFolder(relativePath).getFullPath();
		}).collect(Collectors.toList());
		excludingPaths.add(libFolder);

		return excludingPaths;
	}

	private static boolean isPartOfMatureProject(IPath sourcePath) {
		sourcePath = sourcePath.removeTrailingSeparator();
		List<String> segments = Arrays.asList(sourcePath.segments());
		int index = segments.lastIndexOf("src");
		if (index <= 0) {
			return false;
		}

		IPath srcPath = sourcePath.removeLastSegments(segments.size() -1 - index);
		IPath container = srcPath.removeLastSegments(1);
		return container.append("pom.xml").toFile().exists()
			|| container.append("build.gradle").toFile().exists();
	}

	private static String getPackageName(IPath javaFile, IPath workspaceRoot) {
		return getPackageName(javaFile, workspaceRoot, SRC_PREFIXES);
	}

	private static String getPackageName(IPath javaFile, IPath workspaceRoot, String[][] srcPrefixes) {
		IProject project = JavaLanguageServerPlugin.getProjectsManager().getDefaultProject();
		if (project == null || !project.isAccessible()) {
			return "";
		}

		IJavaProject javaProject = JavaCore.create(project);
		return getPackageName(javaFile, workspaceRoot, javaProject, srcPrefixes);
	}

	public static String getPackageName(IPath javaFile, IPath workspaceRoot, IJavaProject javaProject) {
		return getPackageName(javaFile, workspaceRoot, javaProject, SRC_PREFIXES);
	}

	public static String getPackageName(IPath javaFile, IPath workspaceRoot, IJavaProject javaProject, String[][] srcPrefixes) {
		File nioFile = javaFile.toFile();
		try {
			String content = com.google.common.io.Files.toString(nioFile, StandardCharsets.UTF_8);
			if (StringUtils.isBlank(content)) {
				File found = findNearbyNonEmptyFile(nioFile);
				if (found == null) {
					return inferPackageNameFromPath(javaFile, workspaceRoot, srcPrefixes);
				}

				nioFile = found;
			}
		} catch (IOException e) {
		}

		return JDTUtils.getPackageName(javaProject, nioFile.toURI());
	}

	private static File findNearbyNonEmptyFile(File nioFile) throws IOException {
		java.nio.file.Path directory = nioFile.getParentFile().toPath();
		try (Stream<java.nio.file.Path> walk = Files.walk(directory, 1)) {
			Optional<java.nio.file.Path> found = walk.filter(Files::isRegularFile).filter(file -> {
				try {
					return file.toString().endsWith(".java") && !Objects.equals(nioFile.getName(), file.toFile().getName()) && Files.size(file) > 0;
				} catch (IOException e) {
					return false;
				}
			}).findFirst();

			if (found.isPresent()) {
				return found.get().toFile();
			}
		} catch (IOException e) {
		}

		return null;
	}

	private static String inferPackageNameFromPath(IPath javaFile, IPath workspaceRoot, String[][] srcPrefixes) {
		IPath parentPath = javaFile.removeTrailingSeparator().removeLastSegments(1);
		IPath relativePath = parentPath.makeRelativeTo(workspaceRoot);
		List<String> segments = Arrays.asList(relativePath.segments());
		for (int i = 0; i < srcPrefixes.length; i++) {
			int index = Collections.indexOfSubList(segments, Arrays.asList(srcPrefixes[i]));
			if (index > -1) {
				return String.join(JDTUtils.PERIOD, segments.subList(index + srcPrefixes[i].length, segments.size()));
			}
		}

		return String.join(JDTUtils.PERIOD, segments);
	}

	private static IPath inferSourceDirectory(java.nio.file.Path filePath, String packageName) {
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

	public static IPath tryResolveSourceDirectory(IPath javaFile, IPath rootPath, String[][] potentialSrcPrefixes) {
		String packageName = getPackageName(javaFile, rootPath, potentialSrcPrefixes);
		return inferSourceDirectory(javaFile.toFile().toPath(), packageName);
	}

}
