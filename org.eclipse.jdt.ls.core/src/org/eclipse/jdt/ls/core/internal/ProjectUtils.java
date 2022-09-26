/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.plexus.util.DirectoryScanner;
import org.eclipse.buildship.core.internal.configuration.GradleProjectNature;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.commands.DiagnosticsCommand;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.managers.BuildSupportManager;
import org.eclipse.jdt.ls.core.internal.managers.GradleProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.InternalBuildSupports;
import org.eclipse.jdt.ls.core.internal.managers.MavenProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.UnmanagedFolderNature;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.m2e.core.internal.IMavenConstants;

/**
 * @author Fred Bricon
 */
@SuppressWarnings("restriction")
public final class ProjectUtils {

	public static final String SETTINGS = ".settings";

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
		return isJavaProject(project) && isInternalBuildSupport(BuildSupportManager.find(project).orElse(null));
	}

	public static boolean isInternalBuildSupport(IBuildSupport buildSupport) {
		return buildSupport != null && Arrays.stream(InternalBuildSupports.values())
				.anyMatch(bsn -> Objects.equals(buildSupport.buildToolName(), bsn.toString()));
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

	public static IProject[] getAllProjects(boolean includeInvisibleProjects) {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		if (includeInvisibleProjects) {
			return projects;
		} else {
			return Stream.of(projects).filter(p -> isVisibleProject(p)).collect(Collectors.toList()).toArray(new IProject[0]);
		}
	}

	public static IProject getProject(String projectName) {
		if (StringUtils.isBlank(projectName)) {
			return null;
		}

		return ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
	}

	public static IJavaProject getJavaProject(String projectName) {
		IProject project = getProject(projectName);
		return getJavaProject(project);
	}

	public static IJavaProject getJavaProject(IProject project) {
		if (project != null && isJavaProject(project)) {
			return JavaCore.create(project);
		}

		return null;
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
					.format("Cannot add the folder ''{0}'' to the source path because its parent folder is already in the source path of the project ''{1}''.", new String[] { sourcePath.toOSString(), project.getProject().getName() })));
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

	public static IPath[] listReferencedLibraries(IJavaProject project) throws JavaModelException {
		List<IPath> libraries = new LinkedList<>();
		for (IClasspathEntry entry : project.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
				IClasspathEntry resolvedEntry = JavaCore.getResolvedClasspathEntry(entry);
				if (resolvedEntry == null) {
					continue;
				}

				libraries.add(resolvedEntry.getPath());
			}
		}

		return libraries.toArray(IPath[]::new);
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

	/**
	 * Check if the project is an unmanaged folder. (aka invisible project)
	 * @param project
	 */
	public static boolean isUnmanagedFolder(IProject project) {
		if (Objects.equals(project.getName(), ProjectsManager.DEFAULT_PROJECT_NAME)) {
			return false;
		}
		if (isVisibleProject(project)) {
			return false;
		}

		return hasNature(project, UnmanagedFolderNature.NATURE_ID);
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

	public static IPath getProjectRealFolder(IProject project) {
		if (project.isAccessible() && isUnmanagedFolder(project)) {
			return project.getFolder(WORKSPACE_LINK).getLocation();
		}
		return project.getLocation();
	}

	public static IProject createInvisibleProjectIfNotExist(IPath workspaceRoot) throws OperationCanceledException, CoreException {
		String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(workspaceRoot);
		IProject invisibleProject = ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);
		if (!invisibleProject.exists()) {
			ProjectsManager.createJavaProject(invisibleProject, null, null, "bin", null);
			// Link the workspace root to the invisible project.
			IFolder workspaceLinkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);
			if (!workspaceLinkFolder.isLinked()) {
				try {
					JDTUtils.createFolders(workspaceLinkFolder.getParent(), null);
					workspaceLinkFolder.createLink(workspaceRoot.toFile().toURI(), IResource.REPLACE, null);
					File settings = new File(workspaceRoot.toFile(), SETTINGS);
					if (settings.exists()) {
						// reset the preview feature property - https://github.com/eclipse/eclipse.jdt.ls/pull/1863#issuecomment-924395431
						Hashtable<String, String> defaultOptions = JavaCore.getDefaultOptions();
						String defaultPreview = defaultOptions.get(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES);
						String defaultReport = defaultOptions.get(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES);
						IJavaProject javaProject = JavaCore.create(invisibleProject);
						javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, defaultPreview);
						javaProject.setOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, defaultReport);
						IFolder settingsLinkFolder = invisibleProject.getFolder(SETTINGS);
						settingsLinkFolder.createLink(settings.toURI(), IResource.REPLACE, null);
						JVMConfigurator.configureJVMSettings(javaProject);
					}
				} catch (CoreException e) {
					invisibleProject.delete(true, null);
					throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID,
							Messages.format("Failed to create linked resource from ''{0}'' to the invisible project ''{1}''.", new String[] { workspaceRoot.toString(), invisibleProjectName }), e));
				}
			}
		}

		return invisibleProject;
	}

	public static boolean isSettingsFolderLinked(IProject project) {
		if (project != null) {
			IFolder workspaceLinkFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK);
			if (workspaceLinkFolder.isLinked()) {
				IFolder settings = project.getFolder(SETTINGS);
				return settings.exists() && settings.isLinked();
			}
		}
		return false;
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

	public static void updateBinaries(IJavaProject javaProject, Map<Path, IPath> libraries, IProgressMonitor monitor) throws CoreException {
		if (monitor.isCanceled()) {
			return;
		}
		IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
		List<IClasspathEntry> newEntries = Arrays.stream(rawClasspath).filter(cpe -> cpe.getEntryKind() != IClasspathEntry.CPE_LIBRARY).collect(Collectors.toCollection(ArrayList::new));
		List<IClasspathEntry> libEntries = Arrays.stream(rawClasspath).filter(cpe -> cpe.getEntryKind() == IClasspathEntry.CPE_LIBRARY && cpe.getSourceAttachmentPath() != null && cpe.getSourceAttachmentPath().toFile().exists())
				.collect(Collectors.toCollection(ArrayList::new));

		for (Map.Entry<Path, IPath> library : libraries.entrySet()) {
			if (monitor.isCanceled()) {
				return;
			}
			IPath binary = new org.eclipse.core.runtime.Path(library.getKey().toString());
			IPath source = library.getValue();
			if (source == null && !libEntries.isEmpty()) {
				Optional<IClasspathEntry> result = libEntries.stream().filter(cpe -> cpe.getPath().equals(binary)).findAny();
				source = result.isPresent() ? result.get().getSourceAttachmentPath() : null;
			}
			IClasspathEntry newEntry = JavaCore.newLibraryEntry(binary, source, null);
			JavaLanguageServerPlugin.logInfo(">> Adding " + binary + " to the classpath");
			newEntries.add(newEntry);
		}
		IClasspathEntry[] newClasspath = newEntries.toArray(new IClasspathEntry[newEntries.size()]);
		if (!Arrays.equals(rawClasspath, newClasspath)) {
			javaProject.setRawClasspath(newClasspath, monitor);
		} else {
			javaProject.getJavaModel().refreshExternalArchives(new IJavaElement[]{javaProject}, monitor);
		}
	}

	public static Set<Path> collectBinaries(IPath projectDir, Set<String> include, Set<String> exclude, IProgressMonitor monitor) throws CoreException {
		Set<Path> binaries = new LinkedHashSet<>();
		Map<IPath, Set<String>> includeByPrefix = groupGlobsByPrefix(projectDir, include);
		Set<IPath> excludeResolved = exclude.stream().map(glob -> resolveGlobPath(projectDir, glob)).collect(Collectors.toSet());
		for (IPath baseDir: includeByPrefix.keySet()) {
			Path base = baseDir.toFile().toPath();
			if (monitor.isCanceled()) {
				return binaries;
			}
			if (Files.isRegularFile(base)) {
				if (isBinary(base))	{
					binaries.add(base);
				}
				continue; // base is a regular file path
			}
			if (!Files.isDirectory(base)) {
				continue; // base does not exist
			}
			Set<String> subInclude = includeByPrefix.get(baseDir);
			Set<String> subExclude = excludeResolved.stream().map(glob -> glob.makeRelativeTo(baseDir).toOSString()).collect(Collectors.toSet());
			DirectoryScanner scanner = new DirectoryScanner();
			try {
				scanner.setIncludes(subInclude.toArray(new String[subInclude.size()]));
				scanner.setExcludes(subExclude.toArray(new String[subExclude.size()]));
				scanner.addDefaultExcludes();
				scanner.setBasedir(base.toFile());
				scanner.scan();
			} catch (IllegalStateException e) {
				throw new CoreException(StatusFactory.newErrorStatus("Unable to collect binaries", e));
			}
			for (String result: scanner.getIncludedFiles()) {
				Path file = base.resolve(result);
				if (isBinary(file))	{
					binaries.add(file);
				}
			}
		}
		return binaries;
	}

	public static IPath detectSources(Path file) {
		String filename = file.getFileName().toString();
		//better approach would be to (also) resolve sources using Maven central, or anything smarter really
		String sourceName = filename.substring(0, filename.lastIndexOf(JAR_SUFFIX)) + SOURCE_JAR_SUFFIX;
		Path sourcePath = file.getParent().resolve(sourceName);
		return Files.isRegularFile(sourcePath) ? new org.eclipse.core.runtime.Path(sourcePath.toString()) : null;
	}

	private static boolean isBinary(Path file) {
		String fileName = file.getFileName().toString();
		return (fileName.endsWith(JAR_SUFFIX)
				//skip source jar files
				//more robust approach would be to check if jar contains .class files or not
				&& !fileName.endsWith(SOURCE_JAR_SUFFIX));
	}

	public static IPath resolveGlobPath(IPath base, String glob) {
		IPath pattern = new org.eclipse.core.runtime.Path(glob);
		if (!pattern.isAbsolute()) { // Append cwd to relative path
			pattern = base.append(pattern);
		}
		if (pattern.getDevice() != null) { // VS Code only matches lower-case device
			pattern = pattern.setDevice(pattern.getDevice().toLowerCase());
		}
		return pattern;
	}

	private static Map<IPath, Set<String>> groupGlobsByPrefix(IPath base, Set<String> globs) {
		Map<IPath, Set<String>> groupedPatterns = new HashMap<>();
		for (String glob: globs) {
			IPath pattern = resolveGlobPath(base, glob); // Resolve to absolute path
			int prefixLength = 0;
			while (prefixLength < pattern.segmentCount()) {
				// org.codehaus.plexus.util.DirectoryScanner only supports * and ?
				// and does not handle escaping, so break on these two special chars
				String segment = pattern.segment(prefixLength);
				if (segment.contains("*") || segment.contains("?")) {
					break;
				}
				prefixLength += 1;
			}
			IPath prefix = pattern.uptoSegment(prefixLength);
			IPath remain = pattern.removeFirstSegments(prefixLength).setDevice(null);
			if (!groupedPatterns.containsKey(prefix)) {
				groupedPatterns.put(prefix, new LinkedHashSet<>());
			}
			groupedPatterns.get(prefix).add(remain.toOSString());
		}
		return groupedPatterns;
	}

	public static void removeJavaNatureAndBuilder(IProject project, IProgressMonitor monitor) throws CoreException {
		if (project != null && project.isAccessible() && ProjectUtils.isJavaProject(project)) {
			IProjectDescription description = project.getDescription();
			String[] natureIds = description.getNatureIds();
			String[] newIds = new String[natureIds.length - 1];
			int count = 0;
			for (String id : natureIds) {
				if (!JavaCore.NATURE_ID.equals(id)) {
					newIds[count++] = id;
				}
			}
			description.setNatureIds(newIds);
			ICommand[] commands = description.getBuildSpec();
			for (int i = 0; i < commands.length; ++i) {
				if (commands[i].getBuilderName().equals(JavaCore.BUILDER_ID)) {
					ICommand[] newCommands = new ICommand[commands.length - 1];
					System.arraycopy(commands, 0, newCommands, 0, i);
					System.arraycopy(commands, i + 1, newCommands, i, commands.length - i - 1);
					description.setBuildSpec(newCommands);
					break;
				}
			}
			project.setDescription(description, IResource.FORCE, monitor);
		}
	}

	public static int getMaxProjectProblemSeverity() {
		int maxSeverity = IMarker.SEVERITY_INFO;
		for (IProject project : ProjectUtils.getAllProjects()) {
			if (ProjectsManager.DEFAULT_PROJECT_NAME.equals(project.getName())) {
				continue;
			}

			try {
				maxSeverity = Math.max(maxSeverity, project.findMaxProblemSeverity(null, true, IResource.DEPTH_ZERO));
				List<IMarker> markers = ResourceUtils.findMarkers(project, IMarker.SEVERITY_ERROR, IMarker.SEVERITY_WARNING);
				List<IMarker> buildFileMarkers = markers.stream().filter(marker -> isBuildFileMarker(marker, project)).collect(Collectors.toList());
				for (IMarker marker : buildFileMarkers) {
					maxSeverity = Math.max(maxSeverity, marker.getAttribute(IMarker.SEVERITY, 0));
					if (maxSeverity == IMarker.SEVERITY_ERROR) {
						break;
					}
				}
			} catch (CoreException e) {
				// ignore
			}

			if (maxSeverity == IMarker.SEVERITY_ERROR) {
				break;
			}
		}

		return maxSeverity;
	}

	private static boolean isBuildFileMarker(IMarker marker, IProject project) {
		IResource resource = marker.getResource();
		if (!resource.exists()) {
			return false;
		}
		String lastSegment = resource.getFullPath().lastSegment();
		if (ProjectUtils.isMavenProject(project)) {
			return lastSegment.equals(MavenProjectImporter.POM_FILE);
		} else if (ProjectUtils.isGradleProject(project)) {
			return lastSegment.equals(GradleProjectImporter.BUILD_GRADLE_DESCRIPTOR) ||
				lastSegment.equals(GradleProjectImporter.BUILD_GRADLE_KTS_DESCRIPTOR) ||
				lastSegment.equals(GradleProjectImporter.SETTINGS_GRADLE_DESCRIPTOR) ||
				lastSegment.equals(GradleProjectImporter.SETTINGS_GRADLE_KTS_DESCRIPTOR);
		}
		return false;
	}

	/**
	 * Get a collection of projects based on the given document identifiers. The belonging projects of those
	 * documents will be added to the returned collection.
	 * @param identifiers a list of the {@link org.eclipse.lsp4j.TextDocumentIdentifier}
	 */
	public static Collection<IProject> getProjectsFromDocumentIdentifiers(List<TextDocumentIdentifier> identifiers) {
		Set<IProject> projects = new HashSet<>();
		for (TextDocumentIdentifier identifier : identifiers) {
			IProject project = getProjectFromUri(identifier.getUri());
			if (project != null) {
				projects.add(project);
				continue;
			}
			IFile file = JDTUtils.findFile(identifier.getUri());
			if (file == null) {
				continue;
			}
			project = file.getProject();
			if (project != null) {
				projects.add(project);
			}
		}
		return projects;
	}

	/**
	 * Get <code>IProject</code> from a uri string. Or <code>null</code> if cannot find any project from the given uri.
	 * @param uri uri string
	 */
	public static IProject getProjectFromUri(String uri) {
		IPath uriPath = ResourceUtils.canonicalFilePathFromURI(uri);
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : projects) {
			if (project.getLocation().equals(uriPath)) {
				return project;
			}
		}
		return null;
	}

	/**
	 * Refresh the diagnostics for all the working copies.
	 * @param monitor progress monitor
	 * @throws JavaModelException
	 */
	public static void refreshDiagnostics(IProgressMonitor monitor) throws JavaModelException {
		if (JavaLanguageServerPlugin.getInstance().getProtocol() != null && JavaLanguageServerPlugin.getInstance().getProtocol().getClientConnection() != null) {
			for (ICompilationUnit unit : JavaCore.getWorkingCopies(null)) {
				IPath path = unit.getPath();
				IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
				if (file.exists()) {
					String contents = null;
					try {
						if (unit.hasUnsavedChanges()) {
							contents = unit.getSource();
						}
					} catch (Exception e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
					unit.discardWorkingCopy();
					if (unit.equals(CoreASTProvider.getInstance().getActiveJavaElement())) {
						CoreASTProvider.getInstance().disposeAST();
					}
					unit = JavaCore.createCompilationUnitFrom(file);
					unit.becomeWorkingCopy(monitor);
					if (contents != null) {
						unit.getBuffer().setContents(contents);
					}
				}
				DiagnosticsHandler diagnosticHandler = new DiagnosticsHandler(JavaLanguageServerPlugin.getInstance().getProtocol().getClientConnection(), unit);
				diagnosticHandler.clearDiagnostics();
				DiagnosticsCommand.refreshDiagnostics(JDTUtils.toURI(unit), "thisFile", JDTUtils.isDefaultProject(unit) || !JDTUtils.isOnClassPath(unit));
			}
		}
	}

}
