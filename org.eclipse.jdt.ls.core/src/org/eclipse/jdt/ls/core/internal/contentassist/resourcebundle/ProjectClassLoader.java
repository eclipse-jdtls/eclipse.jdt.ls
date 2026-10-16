/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.contentassist.resourcebundle;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * Creates a ClassLoader for a Java project that can load resources from the project's classpath.
 * This allows using ResourceBundle.getBundle() API which handles locale fallback automatically.
 */
public class ProjectClassLoader {

	/**
	 * Creates a ClassLoader for the given Java project.
	 * The classloader includes:
	 * - Output folders (compiled classes/resources)
	 * - Source folders (for resources in source)
	 * - Library entries (JARs)
	 *
	 * @param javaProject the Java project
	 * @param monitor the progress monitor
	 * @return a ClassLoader that can load resources from the project's classpath, or null if creation fails
	 */
	public static ClassLoader createClassLoader(IJavaProject javaProject, IProgressMonitor monitor) {
		if (javaProject == null) {
			return null;
		}

		try {
			IProject project = javaProject.getProject();
			if (project == null || !project.exists()) {
				return null;
			}

			List<URL> urls = new ArrayList<>();

			// Get output location (compiled classes/resources)
			IPath outputPath = javaProject.getOutputLocation();
			if (outputPath != null) {
				IPath relativeOutputPath = outputPath.makeRelativeTo(project.getFullPath());
				IFolder outputFolder = project.getFolder(relativeOutputPath);
				if (outputFolder.exists()) {
					IPath location = outputFolder.getLocation();
					if (location != null) {
						URL url = location.toFile().toURI().toURL();
						urls.add(url);
					}
				}
			}

			// Get resolved classpath entries (includes source folders and libraries)
			IClasspathEntry[] classpath = javaProject.getResolvedClasspath(true);
			for (IClasspathEntry entry : classpath) {
				if (monitor.isCanceled()) {
					return null;
				}

				IPath path = entry.getPath();
				if (path == null) {
					continue;
				}

				URL url = null;
				int entryKind = entry.getEntryKind();

				switch (entryKind) {
					case IClasspathEntry.CPE_SOURCE:
						// Source folder: use the source folder location directly
						IPath relativePath = path.makeRelativeTo(project.getFullPath());
						if (!relativePath.isEmpty()) {
							IFolder sourceFolder = project.getFolder(relativePath);
							if (sourceFolder.exists()) {
								IPath location = sourceFolder.getLocation();
								if (location != null) {
									url = location.toFile().toURI().toURL();
								}
							}
						}
						break;

					case IClasspathEntry.CPE_LIBRARY:
						// Library (JAR file): use the file location
						File file = path.toFile();
						if (file.exists()) {
							url = file.toURI().toURL();
						} else {
							// Try relative to project
							IPath relativeLibPath = path.makeRelativeTo(project.getFullPath());
							IFolder libFolder = project.getFolder(relativeLibPath);
							if (libFolder.exists()) {
								IPath location = libFolder.getLocation();
								if (location != null) {
									url = location.toFile().toURI().toURL();
								}
							}
						}
						break;

					case IClasspathEntry.CPE_PROJECT:
						// Project dependency: get its output location
						IProject depProject = project.getWorkspace().getRoot().getProject(path.lastSegment());
						if (depProject != null && depProject.exists()) {
							IJavaProject depJavaProject = org.eclipse.jdt.core.JavaCore.create(depProject);
							if (depJavaProject != null && depJavaProject.exists()) {
								IPath depOutputPath = depJavaProject.getOutputLocation();
								if (depOutputPath != null) {
									IPath relativeDepOutput = depOutputPath.makeRelativeTo(depProject.getFullPath());
									IFolder depOutputFolder = depProject.getFolder(relativeDepOutput);
									if (depOutputFolder.exists()) {
										IPath location = depOutputFolder.getLocation();
										if (location != null) {
											url = location.toFile().toURI().toURL();
										}
									}
								}
							}
						}
						break;

					default:
						// Skip containers and other entry types
						break;
				}

				if (url != null) {
					urls.add(url);
				}
			}

			if (urls.isEmpty()) {
				return null;
			}

			// Create URLClassLoader with parent classloader (to access system classes)
			URL[] urlArray = urls.toArray(new URL[urls.size()]);
			return new URLClassLoader(urlArray, ClassLoader.getSystemClassLoader());

		} catch (MalformedURLException | CoreException e) {
			JavaLanguageServerPlugin.logException("Error creating classloader for project: " + javaProject.getElementName(), e);
			return null;
		}
	}
}
