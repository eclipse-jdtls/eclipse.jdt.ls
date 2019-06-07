/*******************************************************************************
 * Copyright (c) 2016-2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.buildship.core.internal.configuration.GradleProjectNature;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.m2e.core.internal.IMavenConstants;

/**
 * @author Fred Bricon
 */
@SuppressWarnings("restriction")
public final class ProjectUtils {

	public static final String WORKSPACE_LINK = "_";

	private static final String JAR_SUFFIX = ".jar";

	private static final String SOURCE_JAR_SUFFIX = "-sources.jar";

	private ProjectUtils() {
		//No instanciation
	}

	public static boolean hasNature(IProject project, String natureId) {
		try {
			return project != null && project.hasNature(natureId);
		} catch (CoreException e) {
			return false;
		}
	}

	public static boolean isJavaProject(IProject project) {
		return hasNature(project, JavaCore.NATURE_ID);
	}

	public static boolean isMavenProject(IProject project) {
		return hasNature(project, IMavenConstants.NATURE_ID);
	}

	public static boolean isGradleProject(IProject project) {
		return hasNature(project, GradleProjectNature.ID);
	}

	public static boolean isGeneralJavaProject(IProject project) {
		return isJavaProject(project) && !isMavenProject(project) && !isGradleProject(project);
	}

	public static String getJavaSourceLevel(IProject project) {
		Map<String, String> options = getJavaOptions(project);
		return options == null ? null : options.get(JavaCore.COMPILER_SOURCE);
	}

	public static Map<String, String> getJavaOptions(IProject project) {
		if (!isJavaProject(project)) {
			return null;
		}
		IJavaProject javaProject = JavaCore.create(project);
		return javaProject.getOptions(true);
	}

	public static List<IProject> getGradleProjects() {
		return Stream.of(getAllProjects()).filter(ProjectUtils::isGradleProject).collect(Collectors.toList());
	}

	public static IJavaProject[] getJavaProjects() {
		//@formatter:off
		return Stream.of(getAllProjects())
					.filter(ProjectUtils::isJavaProject)
					.map(p -> JavaCore.create(p))
					.filter(p -> p != null)
					.toArray(IJavaProject[]::new);
		//@formatter:on
	}

	public static IProject[] getAllProjects() {
		return ResourcesPlugin.getWorkspace().getRoot().getProjects();
	}

	public static boolean addSourcePath(IPath sourcePath, IJavaProject project) throws CoreException {
		return addSourcePath(sourcePath, null, project);
	}

	public static boolean addSourcePath(IPath sourcePath, IPath[] exclusionPaths, IJavaProject project) throws CoreException {
		IClasspathEntry[] existingEntries = project.getRawClasspath();
		List<IPath> parentSrcPaths = new ArrayList<>();
		List<IPath> exclusionPatterns = new ArrayList<>();
		for (IClasspathEntry entry : existingEntries) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				if (entry.getPath().equals(sourcePath)) {
					return false;
				} else if (entry.getPath().isPrefixOf(sourcePath)) {
					parentSrcPaths.add(entry.getPath());
				} else if (sourcePath.isPrefixOf(entry.getPath())) {
					exclusionPatterns.add(entry.getPath().makeRelativeTo(sourcePath).addTrailingSeparator());
				}
			}
		}

		if (!parentSrcPaths.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, Messages
					.format("Cannot add the folder ''{0}'' to the source path because it''s parent folder is already in the source path of the project ''{1}''.", new String[] { sourcePath.toOSString(), project.getProject().getName() })));
		}

		if (exclusionPaths != null) {
			for (IPath exclusion : exclusionPaths) {
				if (sourcePath.isPrefixOf(exclusion) && !sourcePath.equals(exclusion)) {
					exclusionPatterns.add(exclusion.makeRelativeTo(sourcePath).addTrailingSeparator());
				}
			}
		}

		IClasspathEntry[] newEntries = new IClasspathEntry[existingEntries.length + 1];
		System.arraycopy(existingEntries, 0, newEntries, 0, existingEntries.length);
		newEntries[newEntries.length - 1] = JavaCore.newSourceEntry(sourcePath, exclusionPatterns.toArray(new IPath[0]));
		project.setRawClasspath(newEntries, project.getOutputLocation(), null);
		return true;
	}

	public static boolean removeSourcePath(IPath sourcePath, IJavaProject project) throws JavaModelException {
		IClasspathEntry[] existingEntries = project.getRawClasspath();
		List<IClasspathEntry> newEntries = new ArrayList<>();
		boolean found = false;
		for (IClasspathEntry entry : existingEntries) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				if (entry.getPath().equals(sourcePath)) {
					found = true;
				} else {
					newEntries.add(removeFilters(entry, sourcePath));
				}
			} else {
				newEntries.add(entry);
			}
		}

		if (found) {
			project.setRawClasspath(newEntries.toArray(new IClasspathEntry[0]), project.getOutputLocation(), null);
			return true;
		}

		return false;
	}

	public static IPath[] listSourcePaths(IJavaProject project) throws JavaModelException {
		List<IPath> result = new ArrayList<>();
		for (IClasspathEntry entry : project.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				result.add(entry.getPath());
			}
		}

		return result.toArray(new IPath[0]);
	}

	public static boolean isOnSourcePath(IPath sourcePath, IJavaProject project) throws JavaModelException {
		for (IClasspathEntry entry : project.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE && entry.getPath().equals(sourcePath)) {
				return true;
			}
		}

		return false;
	}

	public static IPath findBelongedWorkspaceRoot(IPath filePath) {
		PreferenceManager manager = JavaLanguageServerPlugin.getPreferencesManager();
		Collection<IPath> rootPaths = manager.getPreferences().getRootPaths();
		if (rootPaths != null) {
			for (IPath rootPath : rootPaths) {
				if (rootPath.isPrefixOf(filePath)) {
					return rootPath;
				}
			}
		}

		return null;
	}

	public static String getWorkspaceInvisibleProjectName(IPath workspacePath) {
		String fileName = workspacePath.toFile().getName();
		String projectName = fileName + "_" + Integer.toHexString(workspacePath.toPortableString().hashCode());
		return projectName;
	}

	public static boolean isVisibleProject(IProject project) {
		PreferenceManager manager = JavaLanguageServerPlugin.getPreferencesManager();
		Collection<IPath> rootPaths = manager.getPreferences().getRootPaths();
		if (rootPaths == null) {
			return false;
		}

		return ResourceUtils.isContainedIn(project.getLocation(), rootPaths);
	}

	public static List<IProject> getVisibleProjects(IPath workspaceRoot) {
		List<IProject> projects = new ArrayList<>();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (project.exists() && isVisibleProject(project) && workspaceRoot.isPrefixOf(project.getLocation())) {
				projects.add(project);
			}
		}

		return projects;
	}

	public static IProject createInvisibleProjectIfNotExist(IPath workspaceRoot) throws OperationCanceledException, CoreException {
		String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(workspaceRoot);
		IProject invisibleProject = ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);
		if (!invisibleProject.exists()) {
			JavaLanguageServerPlugin.getProjectsManager().createJavaProject(invisibleProject, null, null, "bin", null);
			// Link the workspace root to the invisible project.
			IFolder workspaceLinkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);
			if (!workspaceLinkFolder.isLinked()) {
				try {
					JDTUtils.createFolders(workspaceLinkFolder.getParent(), null);
					workspaceLinkFolder.createLink(workspaceRoot.toFile().toURI(), IResource.REPLACE, null);
				} catch (CoreException e) {
					invisibleProject.delete(true, null);
					throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID,
							Messages.format("Failed to create linked resource from ''{0}'' to the invisible project ''{1}''.", new String[] { workspaceRoot.toString(), invisibleProjectName }), e));
				}
			}
		}

		return invisibleProject;
	}

	private static IClasspathEntry removeFilters(IClasspathEntry entry, IPath path) {
		IPath[] inclusionPatterns = entry.getInclusionPatterns();
		List<IPath> inclusionList = new ArrayList<>();
		if (inclusionPatterns != null) {
			for (IPath pattern : inclusionPatterns) {
				if (!path.equals(entry.getPath().append(pattern))) {
					inclusionList.add(pattern);
				}
			}
		}

		IPath[] exclusionPatterns = entry.getExclusionPatterns();
		List<IPath> exclusionList = new ArrayList<>();
		if (exclusionPatterns != null) {
			for (IPath pattern : exclusionPatterns) {
				if (!path.equals(entry.getPath().append(pattern))) {
					exclusionList.add(pattern);
				}
			}
		}

		if ((inclusionPatterns == null || inclusionPatterns.length == inclusionList.size()) && (exclusionPatterns == null || exclusionPatterns.length == exclusionList.size())) {
			return entry;
		} else {
			return JavaCore.newSourceEntry(entry.getPath(), inclusionList.toArray(new IPath[0]), exclusionList.toArray(new IPath[0]), entry.getOutputLocation(), entry.getExtraAttributes());
		}
	}

	public static void updateBinaries(IJavaProject javaProject, IPath libFolderPath, IProgressMonitor monitor) throws CoreException {
		updateBinaries(javaProject, Collections.singleton(libFolderPath), monitor);
	}

	public static void updateBinaries(IJavaProject javaProject, Set<IPath> libFolderPaths, IProgressMonitor monitor) throws CoreException {
		Set<Path> binaries = collectBinaries(libFolderPaths, monitor);
		if (monitor.isCanceled()) {
			return;
		}
		IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
		List<IClasspathEntry> newEntries = Arrays.stream(rawClasspath).filter(cpe -> cpe.getEntryKind() != IClasspathEntry.CPE_LIBRARY).collect(Collectors.toCollection(ArrayList::new));

		for (Path file : binaries) {
			if (monitor.isCanceled()) {
				return;
			}
			IPath newLibPath = new org.eclipse.core.runtime.Path(file.toString());
			IPath sourcePath = detectSources(file);
			IClasspathEntry newEntry = JavaCore.newLibraryEntry(newLibPath, sourcePath, null);
			JavaLanguageServerPlugin.logInfo("Adding " + newLibPath + " to the classpath");
			newEntries.add(newEntry);
		}
		IClasspathEntry[] newClasspath = newEntries.toArray(new IClasspathEntry[newEntries.size()]);
		if (!rawClasspath.equals(newClasspath)) {
			javaProject.setRawClasspath(newClasspath, monitor);
		}
	}

	private static Set<Path> collectBinaries(Set<IPath> libFolderPaths, IProgressMonitor monitor) throws CoreException {
		Set<Path> binaries = new LinkedHashSet<>();
		FileVisitor<? super Path> jarDetector = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (monitor.isCanceled()) {
					return FileVisitResult.TERMINATE;
				}
				if (isBinary(file)) {
					binaries.add(file);
				}
				return FileVisitResult.CONTINUE;
			}

		};
		for (IPath libFolderPath : libFolderPaths) {
			String path = libFolderPath.toOSString();
			try {
				Path libFolder = Paths.get(path);
				if (!Files.isDirectory(libFolder)) {
					continue;
				}
				Files.walkFileTree(Paths.get(path), jarDetector);
			} catch (IOException e) {
				throw new CoreException(StatusFactory.newErrorStatus("Unable to analyze " + path, e));
			}
		}
		return binaries;
	}

	private static boolean isBinary(Path file) {
		String fileName = file.getFileName().toString();
		return (fileName.endsWith(JAR_SUFFIX)
				//skip source jar files
				//more robust approach would be to check if jar contains .class files or not
				&& !fileName.endsWith(SOURCE_JAR_SUFFIX));
	}

	private static IPath detectSources(Path file) {
		String filename = file.getFileName().toString();
		//better approach would be to (also) resolve sources using Maven central, or anything smarter really
		String sourceName = filename.substring(0, filename.lastIndexOf(JAR_SUFFIX)) + SOURCE_JAR_SUFFIX;
		Path sourcePath = file.getParent().resolve(sourceName);
		return Files.isRegularFile(sourcePath) ? new org.eclipse.core.runtime.Path(sourcePath.toString()) : null;
	}


}
