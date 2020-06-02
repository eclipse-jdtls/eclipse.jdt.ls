/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Helper methods to set up a IJavaProject.
 *
 * Contains bits copied from org.eclipse.jdt.testplugin.JavaProjectHelper.java
 */
public class JavaProjectHelper {

	/**
	 * Adds a source container to a IJavaProject.
	 *
	 * @param jproject
	 *                             The parent project
	 * @param containerName
	 *                             The name of the new source container
	 * @param inclusionFilters
	 *                             Inclusion filters to set
	 * @param exclusionFilters
	 *                             Exclusion filters to set
	 * @param outputLocation
	 *                             The location where class files are written to,
	 *                             <b>null</b> for project output folder
	 * @param attributes
	 *                             The classpath attributes to set
	 * @return The handle to the new source container
	 * @throws CoreException
	 *                           Creation failed
	 */
	public static IPackageFragmentRoot addSourceContainer(IJavaProject jproject, String containerName, IPath[] inclusionFilters, IPath[] exclusionFilters, String outputLocation, IClasspathAttribute[] attributes) throws CoreException {
		IProject project = jproject.getProject();
		IContainer container = null;
		if (containerName == null || containerName.length() == 0) {
			container = project;
		} else {
			IFolder folder = project.getFolder(containerName);
			if (!folder.exists()) {
				JDTUtils.createFolders(folder, null);
			}
			container = folder;
		}
		IPackageFragmentRoot root = jproject.getPackageFragmentRoot(container);

		IPath outputPath = null;
		if (outputLocation != null) {
			IFolder folder = project.getFolder(outputLocation);
			if (!folder.exists()) {
				JDTUtils.createFolders(folder, null);
			}
			outputPath = folder.getFullPath();
		}
		IClasspathEntry cpe = JavaCore.newSourceEntry(root.getPath(), inclusionFilters, exclusionFilters, outputPath, attributes);
		addToClasspath(jproject, cpe);
		return root;
	}

	public static void addToClasspath(IJavaProject jproject, IClasspathEntry cpe) throws JavaModelException {
		IClasspathEntry[] oldEntries = jproject.getRawClasspath();
		for (int i = 0; i < oldEntries.length; i++) {
			if (oldEntries[i].equals(cpe)) {
				return;
			}
		}
		int nEntries = oldEntries.length;
		IClasspathEntry[] newEntries = new IClasspathEntry[nEntries + 1];
		System.arraycopy(oldEntries, 0, newEntries, 0, nEntries);
		newEntries[nEntries] = cpe;
		jproject.setRawClasspath(newEntries, null);
	}

	public static String toString(IClasspathEntry[] classpath) {
		return Arrays.stream(classpath).map(cpe -> " - " + cpe.getPath().toString()).collect(Collectors.joining("\n"));
	}

	public static IClasspathEntry findJarEntry(IJavaProject jproject, String jarName) throws JavaModelException {
		return Stream.of(jproject.getRawClasspath()).filter(cpe -> cpe.getEntryKind() == IClasspathEntry.CPE_LIBRARY || cpe.getPath() != null && Objects.equals(jarName, cpe.getPath().lastSegment())).findFirst().orElse(null);
	}
}
