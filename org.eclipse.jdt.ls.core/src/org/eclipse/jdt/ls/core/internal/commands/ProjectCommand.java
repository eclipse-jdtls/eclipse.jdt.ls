/*******************************************************************************
 * Copyright (c) 2020-2023 Microsoft Corporation and others.
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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatus;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.VMStandin;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.ResolverConfiguration;

public class ProjectCommand {

	public static final String NATURE_IDS = IConstants.PLUGIN_ID + ".natureIds";
	public static final String VM_LOCATION = IConstants.PLUGIN_ID + ".vm.location";
	public static final String SOURCE_PATHS = IConstants.PLUGIN_ID + ".sourcePaths";
	public static final String OUTPUT_PATH = IConstants.PLUGIN_ID + ".outputPath";
	public static final String CLASSPATH_ENTRIES = IConstants.PLUGIN_ID + ".classpathEntries";
	public static final String REFERENCED_LIBRARIES = IConstants.PLUGIN_ID + ".referencedLibraries";
	public static final String M2E_SELECTED_PROFILES = "org.eclipse.m2e.core.selectedProfiles";
	private static final String TEST_SCOPE_VALUE = "test";

	/**
	 * Gets the project settings.
	 *
	 * @param uri
	 *        Uri of the source/class file that needs to be queried.
	 * @param settingKeys
	 *        the settings we want to query, for example:
	 *        ["org.eclipse.jdt.core.compiler.compliance", "org.eclipse.jdt.core.compiler.source"].
	 *        <p>
	 *        Besides the options defined in JavaCore, the following keys can also be used:
	 *        <ul>
	 *          <li>"org.eclipse.jdt.ls.core.natureIds": Get the nature ids of the given project</li>
	 *          <li>"org.eclipse.jdt.ls.core.vm.location": Get the location of the VM assigned to build the given Java project.</li>
	 *          <li>"org.eclipse.jdt.ls.core.sourcePaths": Get the source root paths of the given Java project.</li>
	 *          <li>"org.eclipse.jdt.ls.core.outputPath": Get the default output path of the given Java project. Note that the default output path
	 *              may not be equal to the output path of each source root.</li>
	 *          <li>"org.eclipse.jdt.ls.core.referencedLibraries": Get all the referenced library files of the given Java project.</li>
	 *          <li>"org.eclipse.jdt.ls.core.classpathEntries": Get all the classpath entries of the given Java project.</li>
	 *          <li>"org.eclipse.m2e.core.selectedProfiles": Get the selected profiles of the given Maven project.</li>
	 *        </ul>
	 * @return A <code>Map<string, string></code> with all the setting keys and
	 *         their values.
	 * @throws CoreException
	 * @throws URISyntaxException
	 */
	public static Map<String, Object> getProjectSettings(String uri, List<String> settingKeys) throws CoreException, URISyntaxException {
		IJavaProject javaProject = getJavaProjectFromUri(uri);
		IProject project = javaProject.getProject();
		Map<String, Object> settings = new HashMap<>();
		for (String key : settingKeys) {
			switch(key) {
				case NATURE_IDS:
					settings.putIfAbsent(key, project.getDescription().getNatureIds());
					break;
				case VM_LOCATION:
					IVMInstall vmInstall = JavaRuntime.getVMInstall(javaProject);
					if (vmInstall == null) {
						continue;
					}
					File location = vmInstall.getInstallLocation();
					if (location == null) {
						continue;
					}
					settings.putIfAbsent(key, location.getAbsolutePath());
					break;
				case SOURCE_PATHS:
					String[] sourcePaths = Arrays.stream(ProjectUtils.listSourcePaths(javaProject))
							.map(p -> project.getFolder(p.makeRelativeTo(project.getFullPath())).getLocation().toOSString())
							.toArray(String[]::new);
					settings.putIfAbsent(key, sourcePaths);
					break;
				case OUTPUT_PATH:
					IPath outputPath = javaProject.getOutputLocation();
					if (outputPath == null) {
						settings.putIfAbsent(key, "");
					} else {
						settings.putIfAbsent(key, project.getFolder(outputPath.makeRelativeTo(project.getFullPath())).getLocation().toOSString());
					}
					break;
				case REFERENCED_LIBRARIES:
					String[] referencedLibraries = Arrays.stream(ProjectUtils.listReferencedLibraries(javaProject))
							.map(p -> p.toOSString())
							.toArray(String[]::new);
					settings.putIfAbsent(key, referencedLibraries);
					break;
				case CLASSPATH_ENTRIES:
					List<IClasspathEntry> entriesToBeScan = new LinkedList<>();
					Collections.addAll(entriesToBeScan, javaProject.getRawClasspath());
					List<ProjectClasspathEntry> classpathEntries = new LinkedList<>();
					for (int i = 0; i < entriesToBeScan.size(); i++) {
						IClasspathEntry entry = entriesToBeScan.get(i);
						IPath path = entry.getPath();
						IPath output = entry.getOutputLocation();
						int entryKind = entry.getEntryKind();
						if (entryKind == IClasspathEntry.CPE_SOURCE) {
							IPath relativePath = path.makeRelativeTo(project.getFullPath());
							if (relativePath.isEmpty()) {
								continue; // A valid relative source path should not be empty.
							}
							path = project.getFolder(path.makeRelativeTo(project.getFullPath())).getLocation();
							if (output != null) {
								output = project.getFolder(output.makeRelativeTo(project.getFullPath())).getLocation();
							}
						} else if (entryKind == IClasspathEntry.CPE_CONTAINER) {
							// skip JRE container, since it's already handled in VM_LOCATION.
							if (!path.toString().startsWith(JavaRuntime.JRE_CONTAINER)) {
								entriesToBeScan.addAll(expandContainerEntry(javaProject, entry));
							}
							continue;
						} else if (entryKind == IClasspathEntry.CPE_LIBRARY) {
							if (!path.toFile().exists()) {
								// check if it's a project based full path
								IPath filePath = project.getFile(path.makeRelativeTo(project.getFullPath())).getLocation();
								if (filePath != null && filePath.toFile().exists()) {
									path = filePath;
								}
							}
						}
						Map<String, String> attributes = new HashMap<>();
						for (IClasspathAttribute attribute : entry.getExtraAttributes()) {
							attributes.put(attribute.getName(), attribute.getValue());
						}
						classpathEntries.add(new ProjectClasspathEntry(entryKind, path.toOSString(),
								output == null ? null : output.toOSString(), attributes));
					}
					settings.putIfAbsent(key, classpathEntries);
					break;
				case M2E_SELECTED_PROFILES:
					// Note, this is an experimental feature, the returned value might be changed.
					IProjectConfigurationManager projectManager = MavenPlugin.getProjectConfigurationManager();
					ResolverConfiguration config = (ResolverConfiguration) projectManager.getProjectConfiguration(javaProject.getProject());
					settings.putIfAbsent(key, config.getSelectedProfiles());
					break;
				default:
					settings.putIfAbsent(key, javaProject.getOption(key, true));
					break;
			}
		}
		return settings;
	}

	/**
	 * Update the project classpath by the given classpath entries.
	 */
	public static void updateClasspaths(String uri, List<ProjectClasspathEntry> entries, IProgressMonitor monitor) throws CoreException, URISyntaxException {
		IJavaProject javaProject = getJavaProjectFromUri(uri);
		IProject project = javaProject.getProject();
		Map<IPath, IPath> sourceAndOutput = new HashMap<>();
		List<IClasspathEntry> newEntries = new LinkedList<>();
		List<IClasspathEntry> newDependencyEntries = new LinkedList<>();
		for (ProjectClasspathEntry entry : entries) {
			if (entry.getKind() == IClasspathEntry.CPE_SOURCE) {
				IPath path = project.getFolder(entry.getPath()).getFullPath();
				IPath outputLocation = null;
				String output = entry.getOutput();
				if (output != null) {
					if (".".equals(output)) {
						outputLocation = project.getFullPath();
					} else {
						outputLocation = project.getFolder(output).getFullPath();
					}
				}
				sourceAndOutput.put(path, outputLocation);
			} else if (entry.getKind() == IClasspathEntry.CPE_CONTAINER) {
				if (entry.getPath().startsWith(JavaRuntime.JRE_CONTAINER)) {
					String jdkPath = entry.getPath().substring(JavaRuntime.JRE_CONTAINER.length());
					newEntries.add(getNewJdkEntry(javaProject, jdkPath));
				} else {
					JavaLanguageServerPlugin.logInfo("The container entry " + entry.getPath() + " is not supported to be updated.");
				}
			} else {
				newDependencyEntries.add(convertClasspathEntry(entry));
			}
		}
		IClasspathEntry[] sources = ProjectUtils.resolveSourceClasspathEntries(javaProject, sourceAndOutput, Collections.emptyList(), javaProject.getOutputLocation());
		newEntries.addAll(Arrays.asList(sources));
		newEntries.addAll(resolveDependencyEntries(javaProject, newDependencyEntries));

		IClasspathEntry[] rawClasspath = newEntries.toArray(IClasspathEntry[]::new);
		IJavaModelStatus checkStatus = ClasspathEntry.validateClasspath(javaProject, rawClasspath, javaProject.getOutputLocation());
		if (!checkStatus.isOK()) {
			throw new CoreException(checkStatus);
		}
		javaProject.setRawClasspath(rawClasspath, monitor);
	}

	/**
	 * Check the new dependency entries are different from the current ones or not.
	 * If they are equal, return the current dependency entries, otherwise return the new ones.
	 */
	private static List<IClasspathEntry> resolveDependencyEntries(IJavaProject javaProject, List<IClasspathEntry> newEntries) throws JavaModelException {
		List<IClasspathEntry> currentDependencyEntries = new LinkedList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE &&
					!entry.getPath().toString().startsWith(JavaRuntime.JRE_CONTAINER)) {
				currentDependencyEntries.add(entry);
			}
		}

		Map<IPath, IClasspathEntry> currentEntryMapping = new HashMap<>();
		for (IClasspathEntry entry : currentDependencyEntries) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				List<IClasspathEntry> expandedContainerEntry = expandContainerEntry(javaProject, entry);
				for (IClasspathEntry containerEntry : expandedContainerEntry) {
					currentEntryMapping.put(containerEntry.getPath(), containerEntry);
				}
			} else {
				currentEntryMapping.put(entry.getPath(), entry);
			}
		}

		// Use new dependency entries if the size is different
		if (newEntries.size() != currentEntryMapping.size()) {
			return newEntries;
		}

		for (IClasspathEntry entry : newEntries) {
			IClasspathEntry currentEntry = currentEntryMapping.get(entry.getPath());
			if (currentEntry == null) {
				return newEntries;
			}
		}

		return currentDependencyEntries;
	}

	/**
	 * Expand the container entry, the returned list is guaranteed to contain no container entry.
	 */
	private static List<IClasspathEntry> expandContainerEntry(IJavaProject javaProject, IClasspathEntry entry) throws JavaModelException {
		if (entry.getEntryKind() != IClasspathEntry.CPE_CONTAINER) {
			return Collections.singletonList(entry);
		}

		List<IClasspathEntry> resolvedEntries = new LinkedList<>();
		List<IClasspathEntry> entriesToScan = new LinkedList<>();
		entriesToScan.add(entry);
		for (int i = 0; i < entriesToScan.size(); i++) {
			IClasspathEntry currentEntry = entriesToScan.get(i);
			if (currentEntry.getEntryKind() != IClasspathEntry.CPE_CONTAINER) {
				resolvedEntries.add(currentEntry);
				continue;
			}
			IClasspathContainer container = JavaCore.getClasspathContainer(currentEntry.getPath(), javaProject);
			if (container == null) {
				continue;
			}
			IClasspathEntry[] containerEntries = container.getClasspathEntries();
			for (IClasspathEntry containerEntry : containerEntries) {
				if (containerEntry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
					entriesToScan.add(containerEntry);
				} else {
					resolvedEntries.add(containerEntry);
				}
			}
		}
		return resolvedEntries;
	}

	/**
	 * Convert ProjectClasspathEntry to IClasspathEntry.
	 */
	private static IClasspathEntry convertClasspathEntry(ProjectClasspathEntry entry) {
		List<IClasspathAttribute> attributes = new LinkedList<>();
		if (entry.getAttributes() != null) {
			for (Entry<String, String> attributeEntry : entry.getAttributes().entrySet()) {
				attributes.add(JavaCore.newClasspathAttribute(attributeEntry.getKey(), attributeEntry.getValue()));
			}
		}
		switch (entry.getKind()) {
			case IClasspathEntry.CPE_CONTAINER:
				return JavaCore.newContainerEntry(
					IPath.fromOSString(entry.getPath()),
					ClasspathEntry.NO_ACCESS_RULES,
					attributes.toArray(IClasspathAttribute[]::new),
					false
				);
			case IClasspathEntry.CPE_LIBRARY:
				return JavaCore.newLibraryEntry(
					IPath.fromOSString(entry.getPath()),
					null,
					null,
					ClasspathEntry.NO_ACCESS_RULES,
					attributes.toArray(IClasspathAttribute[]::new),
					false
				);
			case IClasspathEntry.CPE_PROJECT:
				return JavaCore.newProjectEntry(
					IPath.fromOSString(entry.getPath()),
					ClasspathEntry.NO_ACCESS_RULES,
					false,
					attributes.toArray(IClasspathAttribute[]::new),
					false
				);
			default:
				return null;
		}
	}

	/**
	 * Updates the project source paths.
	 * @param uri Uri of the project.
	 * @param sourceAndOutput A map of source paths and their corresponding output paths.
	 * @throws CoreException
	 * @throws URISyntaxException
	 */
	public static void updateSourcePaths(String uri, Map<String, String> sourceAndOutput) throws CoreException, URISyntaxException {
		IJavaProject javaProject = getJavaProjectFromUri(uri);
		IProject project = javaProject.getProject();
		Map<IPath, IPath> sourceAndOutputWithFullPath = new HashMap<>();
		for (Map.Entry<String, String> entry : sourceAndOutput.entrySet()) {
			IPath path = project.getFolder(entry.getKey()).getFullPath();
			IPath outputLocation = null;
			String output = entry.getValue();
			if (output != null) {
				if (".".equals(output)) {
					outputLocation = project.getFullPath();
				} else {
					outputLocation = project.getFolder(output).getFullPath();
				}
			}
			sourceAndOutputWithFullPath.put(path, outputLocation);
		}
		IClasspathEntry[] newEntries = ProjectUtils.resolveClassPathEntries(javaProject, sourceAndOutputWithFullPath, Collections.emptyList(), null);
		javaProject.setRawClasspath(newEntries, new NullProgressMonitor());
	}

	/**
	 * Gets the classpaths and modulepaths.
	 *
	 * @param uri
	 *                    Uri of the source/class file that needs to be queried.
	 * @param options
	 *                    Query options.
	 * @return <code>ClasspathResult</code> containing both classpaths and
	 *         modulepaths.
	 * @throws CoreException
	 * @throws URISyntaxException
	 */
	public static ClasspathResult getClasspaths(String uri, ClasspathOptions options) throws CoreException, URISyntaxException {
		return getClasspathsFromJavaProject(getJavaProjectFromUri(uri), options);
	}

	public static ClasspathResult getClasspathsFromJavaProject(IJavaProject javaProject, ClasspathOptions options) throws CoreException, URISyntaxException {
		Optional<IBuildSupport> bs = JavaLanguageServerPlugin.getProjectsManager().getBuildSupport(javaProject.getProject());
		if (!bs.isPresent()) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "No BuildSupport for the project: " + javaProject.getElementName()));
		}
		ILaunchConfiguration launchConfig = bs.get().getLaunchConfiguration(javaProject, options.scope);
		JavaLaunchDelegate delegate = new JavaLaunchDelegate();
		ClasspathResult[] result = new ClasspathResult[1];

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		ISchedulingRule currentRule = Job.getJobManager().currentRule();
		ISchedulingRule schedulingRule;
		if (currentRule != null && currentRule.contains(javaProject.getSchedulingRule())) {
			schedulingRule = null;
		} else {
			schedulingRule = javaProject.getSchedulingRule();
		}
		workspace.run((IWorkspaceRunnable) monitor -> {
			String[][] paths = delegate.getClasspathAndModulepath(launchConfig);
			result[0] = new ClasspathResult(javaProject.getProject().getLocationURI(), paths[0], paths[1]);
		}, schedulingRule, IWorkspace.AVOID_UPDATE, new NullProgressMonitor());

		if (result[0] != null) {
			return result[0];
		}

		throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Failed to get the classpaths."));
	}

	/**
	 * Checks if the input uri is a test source file or not.
	 *
	 * @param uri
	 *                Uri of the source file that needs to be queried.
	 * @return <code>true</code> if the input uri is a test file in its belonging
	 *         project, otherwise returns <code>false</code>.
	 * @throws CoreException
	 */
	public static boolean isTestFile(String uri) throws CoreException {
		ICompilationUnit compilationUnit = JDTUtils.resolveCompilationUnit(uri);
		if (compilationUnit == null) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Given URI does not belong to an existing Java source file."));
		}
		IJavaProject javaProject = compilationUnit.getJavaProject();
		if (javaProject == null) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Given URI does not belong to an existing Java project."));
		}
		// Ignore default project
		if (ProjectsManager.DEFAULT_PROJECT_NAME.equals(javaProject.getProject().getName())) {
			return false;
		}
		final IPath compilationUnitPath = compilationUnit.getPath();
		for (IPath testpath : listTestSourcePaths(javaProject)) {
			if (testpath.isPrefixOf(compilationUnitPath)) {
				return true;
			}
		}
		return false;
	}

	public static List<URI> getAllJavaProjects() {
		return getProjectUris(Arrays.stream(ProjectUtils.getJavaProjects())
				.map(IJavaProject::getProject).toArray(IProject[]::new));
	}

	public static List<URI> getAllProjects() {
		return getProjectUris(ProjectUtils.getAllProjects());
	}

	private static List<URI> getProjectUris(IProject[] projects) {
		List<URI> projectUris = new LinkedList<>();
		for (IProject project : projects) {
			projectUris.add(ProjectUtils.getProjectRealFolder(project).toFile().toURI());
		}
		return projectUris;
	}

	public static void importProject(IProgressMonitor monitor) {
		JavaLanguageServerPlugin.getProjectsManager().importProjects(monitor);
	}

	public static void changeImportedProjects(Collection<String> toUpdate, Collection<String> toImport,
			Collection<String> toDelete, IProgressMonitor monitor) {
		JavaLanguageServerPlugin.getProjectsManager().changeImportedProjects(toUpdate, toImport, toDelete, monitor);
	}

	private static IPath[] listTestSourcePaths(IJavaProject project) throws JavaModelException {
		List<IPath> result = new ArrayList<>();
		for (IClasspathEntry entry : project.getRawClasspath()) {
			if (isTestClasspathEntry(entry)) {
				result.add(entry.getPath());
			}
		}
		return result.toArray(new IPath[0]);
	}

	private static boolean isTestClasspathEntry(IClasspathEntry entry) {
		if (entry.getEntryKind() != ClasspathEntry.CPE_SOURCE) {
			return false;
		}

		if (entry.isTest()) {
			return true;
		}

		for (final IClasspathAttribute attribute : entry.getExtraAttributes()) {
			String attributeName = attribute.getName();
			// attribute name could be "maven.scope" for Maven, "gradle_scope" or "gradle_used_by_scope" for Gradle
			if (attributeName.contains("scope")) {
				// the attribute value could be "test" or "integrationTest"
				return attribute.getValue() != null && attribute.getValue().toLowerCase().contains(TEST_SCOPE_VALUE);
			}
		}

		return false;
	}

	/**
	 * public visibility only for test purpose
	 */
	public static IJavaProject getJavaProjectFromUri(String uri) throws CoreException, URISyntaxException {
		ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
		if (typeRoot != null) {
			IJavaProject javaProject = typeRoot.getJavaProject();
			if (javaProject == null) {
				throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Given URI does not belong to an existing Java project."));
			}
			return javaProject;
		}

		// check for project root uri
		IContainer[] containers = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(new URI(uri));
		if (containers == null || containers.length == 0) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Given URI does not belong to any Java project."));
		}

		// For multi-module scenario
		Arrays.sort(containers, (Comparator<IContainer>) (IContainer a, IContainer b) -> {
			return a.getFullPath().segmentCount() - b.getFullPath().segmentCount();
		});

		IJavaElement targetElement = null;
		for (IContainer container : containers) {
			targetElement = JavaCore.create(container.getProject());
			if (targetElement != null && targetElement.exists()) {
				break;
			}
		}

		if (targetElement == null || targetElement.getJavaProject() == null) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Given URI does not belong to any Java project."));
		}

		return targetElement.getJavaProject();
	}

	public static class ClasspathOptions {
		public String scope;
	}

	public static class ClasspathResult {
		public URI projectRoot;
		public String[] classpaths;
		public String[] modulepaths;

		public ClasspathResult(URI projectRoot, String[] classpaths, String[] modulepaths) {
			this.projectRoot = projectRoot;
			this.classpaths = classpaths;
			this.modulepaths = modulepaths;
		}
	}

	public static class GetAllProjectOptions {
		public boolean includeNonJava;
	}

	public static SymbolInformation resolveWorkspaceSymbol(SymbolInformation request) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(request.getLocation().getUri());
		if (unit == null || !unit.exists()) {
			return null;
		}
		Location location = request.getLocation();

		try {
			Deque<IJavaElement> children = new ArrayDeque<>(Arrays.asList(unit.getChildren()));
			while (!children.isEmpty()) {
				IJavaElement child = children.pop();
				if (child instanceof IType) {

					if (request.getName().equals(child.getElementName())) {
						location = JDTUtils.toLocation(child);
						break;
					}
					children.addAll(Arrays.asList(((IParent) child).getChildren()));
				}

			}

		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logError("Problem resolving symbol information for " + unit.getElementName());
		}

		SymbolInformation si = new SymbolInformation();
		si.setName(request.getName());
		si.setContainerName(request.getContainerName());
		si.setLocation(location);
		si.setKind(request.getKind());

		return si;
	}

	public static JdkUpdateResult updateProjectJdk(String projectUri, String jdkPath, IProgressMonitor monitor) throws CoreException, URISyntaxException {
		IJavaProject javaProject = ProjectCommand.getJavaProjectFromUri(projectUri);
		List<IClasspathEntry> newClasspathEntries = new ArrayList<>();
		try {
			for (IClasspathEntry entry : javaProject.getRawClasspath()) {
				if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER &&
						entry.getPath().toString().startsWith(JavaRuntime.JRE_CONTAINER)) {
					newClasspathEntries.add(getNewJdkEntry(javaProject, jdkPath));
				} else {
					newClasspathEntries.add(entry);
				}
			}
			javaProject.setRawClasspath(newClasspathEntries.toArray(IClasspathEntry[]::new), monitor);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
			return new JdkUpdateResult(false, e.getMessage());
		}
		return new JdkUpdateResult(true, jdkPath);
	}

	private static IClasspathEntry getNewJdkEntry(IJavaProject javaProject, String jdkPath) throws CoreException {
		IVMInstall vmInstall = getVmInstallByPath(jdkPath);
		List<IClasspathAttribute> extraAttributes = new ArrayList<>();
		if (vmInstall == null) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "The select JDK path is not valid."));
		}
		if (javaProject.getOwnModuleDescription() != null) {
			extraAttributes.add(JavaCore.newClasspathAttribute(IClasspathAttribute.MODULE, "true"));
		}

		return JavaCore.newContainerEntry(
				JavaRuntime.newJREContainerPath(vmInstall),
				ClasspathEntry.NO_ACCESS_RULES,
				extraAttributes.toArray(IClasspathAttribute[]::new),
				false /*isExported*/
		);
	}

	private static IVMInstall getVmInstallByPath(String path) {
		java.nio.file.Path vmPath = new Path(path).toPath();
		IVMInstallType[] vmInstallTypes = JavaRuntime.getVMInstallTypes();
		for (IVMInstallType vmInstallType : vmInstallTypes) {
			IVMInstall[] vmInstalls = vmInstallType.getVMInstalls();
			for (IVMInstall vmInstall : vmInstalls) {
				if (vmInstall.getInstallLocation().toPath().normalize().compareTo(vmPath) == 0) {
					return vmInstall;
				}
			}
		}

		StandardVMType standardType = (StandardVMType) JavaRuntime.getVMInstallType(StandardVMType.ID_STANDARD_VM_TYPE);
		VMStandin vmStandin = new VMStandin(standardType, path);
		File jdkHomeFile = vmPath.toFile();
		vmStandin.setInstallLocation(jdkHomeFile);
		String name = jdkHomeFile.getName();
		int i = 1;
		while (isDuplicateName(name)) {
			name = jdkHomeFile.getName() + '(' + i++ + ')';
		}
		vmStandin.setName(name);
		IVMInstall install = vmStandin.convertToRealVM();
		if (!(install instanceof IVMInstall2 vm && vm.getJavaVersion() != null)) {
			// worksaround: such VMs may cause issue later
			// https://github.com/eclipse-jdt/eclipse.jdt.debug/issues/248
			standardType.disposeVMInstall(install.getId());
			return null;
		}
		return install;
	}

	private static boolean isDuplicateName(String name) {
		return Stream.of(JavaRuntime.getVMInstallTypes()) //
			.flatMap(vmType -> Arrays.stream(vmType.getVMInstalls())) //
			.map(IVMInstall::getName) //
			.anyMatch(name::equals);
	}

	public static final class JdkUpdateResult {
		public boolean success;
		public String message;

		public JdkUpdateResult(boolean success, String message) {
			this.success = success;
			this.message = message;
		}
	}

	/**
	 * Update the options of the given project.
	 * @param projectUri
	 * @param options
	 * @throws CoreException
	 * @throws URISyntaxException
	 */
	public static void updateProjectSettings(String projectUri, Map<String, Object> options) throws CoreException, URISyntaxException {
		IJavaProject javaProject = getJavaProjectFromUri(projectUri);
		IProject project = javaProject.getProject();
		for (Map.Entry<String, Object> entry : options.entrySet()) {
			switch (entry.getKey()) {
				case M2E_SELECTED_PROFILES:
					IProjectConfigurationManager mavenProjectMgr = MavenPlugin.getProjectConfigurationManager();
					ResolverConfiguration config = (ResolverConfiguration) mavenProjectMgr.getProjectConfiguration(project);
					String selectedProfiles = (String) entry.getValue();
					selectedProfiles = Arrays.stream(selectedProfiles.split(","))
							.map(String::trim)
							.filter(s -> !s.isEmpty())
							.reduce((s1, s2) -> s1 + "," + s2)
							.orElse("");
					if (Objects.equals(config.getSelectedProfiles(), selectedProfiles)) {
						continue;
					}

					config.setSelectedProfiles(selectedProfiles);
					config.setResolveWorkspaceProjects(true);
					boolean isSet = mavenProjectMgr.setResolverConfiguration(project, config);
					if (isSet) {
						JavaLanguageServerPlugin.getProjectsManager().updateProject(project, true);
					}
					break;
				default:
					break;
			}
		}
	}
}
