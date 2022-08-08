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
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
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

		// Add unmanaged folder nature if it's missing.
		if (!invisibleProject.hasNature(UnmanagedFolderNature.NATURE_ID)) {
			addMissingNature(invisibleProject, monitor);
		}

		IJavaProject javaProject = JavaCore.create(invisibleProject);

		IFolder workspaceLinkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		List<String> sourcePathsFromPreferences = null;
		if (preferencesManager != null && preferencesManager.getPreferences() != null) {
			sourcePathsFromPreferences = preferencesManager.getPreferences().getInvisibleProjectSourcePaths();
		}
		Set<IPath> sourcePaths = new HashSet<>();
		if (sourcePathsFromPreferences != null) {
			sourcePaths.addAll(getSourcePaths(sourcePathsFromPreferences, workspaceLinkFolder));
		} else {
			IPath relativeSourcePath = sourceDirectory.makeRelativeTo(rootPath);
			IFolder sourceFolder = workspaceLinkFolder.getFolder(relativeSourcePath);
			sourcePaths.addAll(collectSourcePaths(javaFile, sourceFolder, workspaceLinkFolder));
		}

		List<IPath> excludingPaths = getExcludingPath(javaProject, rootPath, workspaceLinkFolder);

		IPath outputPath = getOutputPath(javaProject, preferencesManager.getPreferences().getInvisibleProjectOutputPath(), false /*isUpdate*/);

		IClasspathEntry[] classpathEntries = resolveClassPathEntries(javaProject, new ArrayList<>(sourcePaths), excludingPaths, outputPath);
		javaProject.setRawClasspath(classpathEntries, outputPath, monitor);

		if (forceUpdateLibPath && preferencesManager != null && preferencesManager.getPreferences() != null) {
			UpdateClasspathJob.getInstance().updateClasspath(javaProject, preferencesManager.getPreferences().getReferencedLibraries());
		}

		return true;
	}

	/**
	 * Based on the trigger file and its belonging source folder, search more places and collect
	 * the valid source paths.
	 * @param triggerFilePath the path of the import trigger file.
	 * @param triggerFolder the folder which is the source root of the trigger file.
	 * @param linkedFolder the invisible project's linked folder.
	 */
	private static Collection<IPath> collectSourcePaths(IPath triggerFilePath, IFolder triggerFolder,
			IFolder linkedFolder) {
		IPath linkedFolderPath = linkedFolder.getLocation();
		Collection<File> foldersToSearch = collectFoldersToSearch(triggerFilePath, triggerFolder, linkedFolderPath);
		IProject currentProject = linkedFolder.getProject();
		if (currentProject == null) {
			return Collections.emptySet();
		}
		Collection<IPath> triggerFiles = collectTriggerFiles(currentProject, foldersToSearch);
		
		Set<IPath> sourcePaths = new HashSet<>();
		sourcePaths.add(triggerFolder.getFullPath());

		for (IPath javaFilePath : triggerFiles) {
			String packageName = getPackageName(javaFilePath, linkedFolderPath);
			IPath directory = inferSourceDirectory(javaFilePath.toFile().toPath(), packageName);
			if (directory == null || !linkedFolderPath.isPrefixOf(directory)
					|| isPartOfMatureProject(directory)) {
				continue;
			}
			IPath relativeSourcePath = directory.makeRelativeTo(linkedFolderPath);
			IPath sourcePath = linkedFolder.getFolder(relativeSourcePath).getFullPath();
			sourcePaths.add(sourcePath);
		}
		return sourcePaths;
	}

	/**
	 * Collect folders that may contain Java source files.
	 * @param triggerFilePath the path of the import trigger file.
	 * @param triggerFolder the folder which is the source root of the trigger file.
	 * @param linkedFolderPath the path of invisible project's linked folder.
	 */
	private static Collection<File> collectFoldersToSearch(IPath triggerFilePath, IFolder triggerFolder,
			IPath linkedFolderPath) {
		Set<File> foldersToSearch = new HashSet<>();
		if (Objects.equals(triggerFolder.getLocation(), linkedFolderPath)) {
			foldersToSearch.addAll(getDirectChildFolders(triggerFilePath, triggerFolder));
		} else {
			foldersToSearch.addAll(getSiblingFolders(triggerFolder, linkedFolderPath));
		}
		return foldersToSearch;
	}

	/**
	 * Get the direct child folders of the given parent folder.
	 * @param triggerFilePath the path of the import trigger file.
	 * @param parentFolder the parent folder.
	 */
	private static Collection<File> getDirectChildFolders(IPath triggerFilePath, IFolder parentFolder) {
		File parent = parentFolder.getLocation().toFile();
		if (parent.isFile()) {
			return Collections.emptySet();
		}

		Set<File> children = new HashSet<>();
		File[] childrenFiles = parent.listFiles();
		for (File dir : childrenFiles) {
			if (!dir.isDirectory()) {
				continue;
			}

			Path dirPath = new Path(dir.getAbsolutePath());
			// skip ancestor folder of the trigger file.
			if (dirPath.isPrefixOf(triggerFilePath)) {
				continue;
			}

			children.add(dir);
		}
		return children;
	}

	/**
	 * Get the sibling folders of the trigger folder.
	 * @param triggerFolder the folder which is the source root of the trigger file.
	 * @param linkedFolderPath the path of invisible project's linked folder.
	 */
	private static Collection<File> getSiblingFolders(IFolder triggerFolder, IPath linkedFolderPath) {
		Set<File> siblings = new HashSet<>();
		IResource parentFolder = triggerFolder.getParent();
		if (parentFolder == null) {
			return Collections.emptySet();
		}

		if (!linkedFolderPath.isPrefixOf(parentFolder.getLocation())) {
			return Collections.emptySet();
		}

		File parent = parentFolder.getLocation().toFile();
		if (parent.isFile()) {
			return Collections.emptySet();
		}

		File[] peerFiles = parent.listFiles();
		for (File peerFile : peerFiles) {
			if (peerFile.isDirectory() && !peerFile.getName().equals(triggerFolder.getName())) {
				siblings.add(peerFile);
			}
		}
		return siblings;
	}

	/**
	 * Collect the Java files contained in the search folders. Each folder will
	 * only be collected one Java file (if they have) at max 3 depth level.
	 * @param project the invisible project that is currently dealing with.
	 * @param searchFolders the folders to search.
	 */
	private static Collection<IPath> collectTriggerFiles(IProject project, Collection<File> searchFolders) {
		JavaFileDetector javaFileDetector = new JavaFileDetector(project);
		for (File file : searchFolders) {
			try {
				Files.walkFileTree(
					file.toPath(),
					EnumSet.noneOf(FileVisitOption.class),
					3 /*maxDepth*/,
					javaFileDetector
				);
			} catch (IOException e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}
		return javaFileDetector.getTriggerFiles();
	}

	/**
	 * Try to infer the source root of the given compilation unit path.
	 * @param javaProject Java project.
	 * @param unitPath the path of the compilation unit.
	 */
	public static void inferSourceRoot(IJavaProject javaProject, IPath unitPath) {
		IProject project = javaProject.getProject();
		IPath projectRealFolder = ProjectUtils.getProjectRealFolder(project);

		IPath rootPath = ProjectUtils.findBelongedWorkspaceRoot(projectRealFolder);
		if (rootPath == null) {
			return;
		}

		String packageName = getPackageName(unitPath, rootPath);
		IPath sourceDirectory = inferSourceDirectory(unitPath.toFile().toPath(), packageName);
		if (sourceDirectory == null || !rootPath.isPrefixOf(sourceDirectory)
				|| isPartOfMatureProject(sourceDirectory)) {
			return;
		}

		IPath relativeSourcePath = sourceDirectory.makeRelativeTo(rootPath);
		IFolder workspaceLinkFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK);
		IPath sourcePath = workspaceLinkFolder.getFolder(relativeSourcePath).getFullPath();
		Set<IPath> sourcePaths = new HashSet<>();
		sourcePaths.add(sourcePath);

		try {
			for (IClasspathEntry entry : javaProject.getRawClasspath()) {
				if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					sourcePaths.add(entry.getPath());
				}
			}
			List<IPath> excludingPaths = getExcludingPath(javaProject, rootPath, workspaceLinkFolder);
			IPath outputPath = getOutputPath(javaProject, getPreferences().getInvisibleProjectOutputPath(), false /*isUpdate*/);
			IClasspathEntry[] classpathEntries = resolveClassPathEntries(javaProject, new ArrayList<>(sourcePaths), excludingPaths, outputPath);

			WorkspaceJob updateClasspathJob = new WorkspaceJob("Update invisible project classpath") {

				@Override
				public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
					try {
						javaProject.setRawClasspath(classpathEntries, outputPath, monitor);
						ProjectUtils.refreshDiagnostics(monitor);
					} catch (CoreException e) {
						JavaLanguageServerPlugin.log(e);
					}
					return Status.OK_STATUS;
				}
			};
			updateClasspathJob.setPriority(Job.BUILD);
			updateClasspathJob.setSystem(true);
			updateClasspathJob.schedule();
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
			return;
		}
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
		return container.append(MavenProjectImporter.POM_FILE).toFile().exists()
			|| container.append(GradleProjectImporter.BUILD_GRADLE_DESCRIPTOR).toFile().exists()
			|| container.append(GradleProjectImporter.SETTINGS_GRADLE_DESCRIPTOR).toFile().exists()
			|| container.append(GradleProjectImporter.BUILD_GRADLE_KTS_DESCRIPTOR).toFile().exists()
			|| container.append(GradleProjectImporter.SETTINGS_GRADLE_KTS_DESCRIPTOR).toFile().exists();
	}

	private static String getPackageName(IPath javaFile, IPath workspaceRoot) {
		return getPackageName(javaFile, workspaceRoot, SRC_PREFIXES);
	}

	private static String getPackageName(IPath javaFile, IPath workspaceRoot, String[][] srcPrefixes) {
		IProject project = ProjectsManager.getDefaultProject();
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

	/**
	 * Add unmanaged folder nature id.
	 * @param project
	 * @param monitor
	 * @throws CoreException
	 */
	private static void addMissingNature(IProject project, IProgressMonitor monitor) throws CoreException {
		IProjectDescription description = project.getDescription();
		String[] natureIds = description.getNatureIds();
		String[] newNatureIds = new String[natureIds.length + 1];
		System.arraycopy(natureIds, 0, newNatureIds, 0, natureIds.length);
		newNatureIds[newNatureIds.length - 1] = UnmanagedFolderNature.NATURE_ID;
		description.setNatureIds(newNatureIds);
		project.setDescription(description, monitor);
	}

	/**
	 * A File Visitor which is used to find Java source files.
	 *
	 * <p>Note: public only for testing purpose.</p>
	 */
	public static class JavaFileDetector extends SimpleFileVisitor<java.nio.file.Path> {

		private IProject currentProject;
		private Set<IPath> javaFiles;
		private Set<String> exclusions;
		private Set<IPath> projectPaths;
		private Set<String> buildFiles;

		public JavaFileDetector(IProject currentProject) {
			this.currentProject = currentProject;
			this.javaFiles = new HashSet<>();
			this.exclusions = new HashSet<>();
			List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
			if (javaImportExclusions != null) {
				exclusions.addAll(javaImportExclusions);
			}
			buildFiles = new HashSet<>(Arrays.asList(
				MavenProjectImporter.POM_FILE,
				GradleProjectImporter.BUILD_GRADLE_DESCRIPTOR,
				GradleProjectImporter.BUILD_GRADLE_KTS_DESCRIPTOR,
				GradleProjectImporter.SETTINGS_GRADLE_DESCRIPTOR,
				GradleProjectImporter.SETTINGS_GRADLE_KTS_DESCRIPTOR,
				IProjectDescription.DESCRIPTION_FILE_NAME,
				IJavaProject.CLASSPATH_FILE_NAME
			));
			// store paths from other projects that need to be excluded
			projectPaths = new HashSet<>();
			IProject[] allProjects = ProjectUtils.getAllProjects();
			for (IProject project : allProjects) {
				if (Objects.equals(project, this.currentProject)) {
					continue;
				}

				if (ProjectUtils.isVisibleProject(project)) {
					this.projectPaths.add(project.getLocation());
					continue;
				}

				if (Objects.equals(project.getName(), ProjectsManager.DEFAULT_PROJECT_NAME)) {
					continue;
				}

				// Add the path of linked resources for those invisible projects
				try {
					project.accept((IResourceVisitor) resource -> {
						if (resource.isLinked()) {
							projectPaths.add(resource.getLocation().removeLastSegments(1));
							return false;
						}
						return true;
					}, IResource.DEPTH_INFINITE, false /*includePhantoms*/);
				} catch (CoreException e) {
					JavaLanguageServerPlugin.log(e);
				}
			}
		}

		@Override
		public FileVisitResult preVisitDirectory(java.nio.file.Path dirPath, BasicFileAttributes attrs) throws IOException {
			if (isExcluded(dirPath)) {
				return FileVisitResult.SKIP_SUBTREE;
			}

			File dir = dirPath.toFile();
			File[] files = dir.listFiles();
			if (files == null) {
				return FileVisitResult.SKIP_SUBTREE;
			}

			File javaFile = null;
			for (File f : files) {
				if (!f.isFile()) {
					continue;
				}

				// stop searching as long as any one of the sub folders contain build files.
				if (buildFiles.contains(f.getName())) {
					return FileVisitResult.TERMINATE;
				}

				if (javaFile == null && f.getName().endsWith(".java")) {
					javaFile = f;
				}
			}

			if (javaFile != null) {
				javaFiles.add(new Path(javaFile.getPath()));
				return FileVisitResult.TERMINATE;
			}

			return FileVisitResult.CONTINUE;
		}

		public Set<IPath> getTriggerFiles() {
			return javaFiles;
		}

		private boolean isExcluded(java.nio.file.Path dir) {
			if (dir.getFileName() == null) {
				return true;
			}

			IPath path = ResourceUtils.filePathFromURI(dir.toUri().toString());
			for (IPath projectPath: projectPaths) {
				if (projectPath.isPrefixOf(path)) {
					return true;
				}
			}

			boolean excluded = false;
			for (String pattern : exclusions) {
				boolean includePattern = false;
				if (pattern.startsWith("!")) {
					includePattern = true;
					pattern = pattern.substring(1);
				}
				PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
				if (matcher.matches(dir)) {
					excluded = includePattern ? false : true;
				}
			}
			return excluded;
		}
	}
}
