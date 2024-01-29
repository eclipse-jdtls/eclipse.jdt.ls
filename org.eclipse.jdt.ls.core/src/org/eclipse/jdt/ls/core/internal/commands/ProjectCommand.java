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
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
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

public class ProjectCommand {

	public static final String NATURE_IDS = IConstants.PLUGIN_ID + ".natureIds";
	public static final String VM_LOCATION = IConstants.PLUGIN_ID + ".vm.location";
	public static final String SOURCE_PATHS = IConstants.PLUGIN_ID + ".sourcePaths";
	public static final String OUTPUT_PATH = IConstants.PLUGIN_ID + ".outputPath";
	public static final String REFERENCED_LIBRARIES = IConstants.PLUGIN_ID + ".referencedLibraries";
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
				default:
					settings.putIfAbsent(key, javaProject.getOption(key, true));
					break;
			}
		}
		return settings;
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
		IClasspathEntry[] originalClasspathEntries = javaProject.getRawClasspath();
		IClasspathAttribute[] extraAttributes = null;
		List<IClasspathEntry> newClasspathEntries = new ArrayList<>();
		for (IClasspathEntry entry : originalClasspathEntries) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER &&
					entry.getPath().toString().startsWith("org.eclipse.jdt.launching.JRE_CONTAINER")) {
				extraAttributes = entry.getExtraAttributes();
			} else {
				newClasspathEntries.add(entry);
			}
		}

		IVMInstall vmInstall = getVmInstallByPath(jdkPath);
		if (vmInstall == null) {
			JavaLanguageServerPlugin.log(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "The select JDK path is not valid."));
			return new JdkUpdateResult(false, "The selected JDK path is not valid.");
		}
		newClasspathEntries.add(JavaCore.newContainerEntry(
				JavaRuntime.newJREContainerPath(vmInstall),
				ClasspathEntry.NO_ACCESS_RULES,
				extraAttributes,
				false /*isExported*/
		));
		javaProject.setRawClasspath(newClasspathEntries.toArray(IClasspathEntry[]::new), monitor);
		return new JdkUpdateResult(true, vmInstall.getInstallLocation().getAbsolutePath());
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
}
