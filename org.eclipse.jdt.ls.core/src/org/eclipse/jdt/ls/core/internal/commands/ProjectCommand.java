/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;

public class ProjectCommand {

	private static final String TEST_SCOPE_VALUE = "test";

	/**
	 * Gets the project settings.
	 * 
	 * @param uri
	 *                        Uri of the source/class file that needs to be queried.
	 * @param settingKeys
	 *                        the settings we want to query, for example:
	 *                        ["org.eclipse.jdt.core.compiler.compliance",
	 *                        "org.eclipse.jdt.core.compiler.source"]
	 * @return A <code>Map<string, string></code> with all the setting keys and
	 *         their values.
	 * @throws CoreException
	 * @throws URISyntaxException
	 */
	public static Map<String, String> getProjectSettings(String uri, List<String> settingKeys) throws CoreException, URISyntaxException {
		IJavaProject javaProject = getJavaProjectFromUri(uri);
		Map<String, String> settings = new HashMap<>();
		for (String key : settingKeys) {
			settings.putIfAbsent(key, javaProject.getOption(key, true));
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
		IJavaProject javaProject = getJavaProjectFromUri(uri);
		Optional<IBuildSupport> bs = JavaLanguageServerPlugin.getProjectsManager().getBuildSupport(javaProject.getProject());
		if (!bs.isPresent()) {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "No BuildSupport for the project: " + javaProject.getElementName()));
		}
		ILaunchConfiguration launchConfig = bs.get().getLaunchConfiguration(javaProject, options.scope);
		JavaLaunchDelegate delegate = new JavaLaunchDelegate();
		String[][] paths = delegate.getClasspathAndModulepath(launchConfig);
		return new ClasspathResult(javaProject.getProject().getLocationURI(), paths[0], paths[1]);
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
		List<URI> javaProjects = new LinkedList<>();
		for (IJavaProject javaProejct : ProjectUtils.getJavaProjects()) {
			if (ResourcesPlugin.getWorkspace().getRoot().getProject(ProjectsManager.DEFAULT_PROJECT_NAME).equals(javaProejct.getProject())) {
				continue;
			}
			javaProjects.add(ProjectUtils.getProjectRealFolder(javaProejct.getProject()).toFile().toURI());
		}
		return javaProjects;
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
			return a.getFullPath().toPortableString().length() - b.getFullPath().toPortableString().length();
		});

		IJavaElement targetElement = null;
		for (IContainer container : containers) {
			targetElement = JavaCore.create(container);
			if (targetElement != null) {
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
}
