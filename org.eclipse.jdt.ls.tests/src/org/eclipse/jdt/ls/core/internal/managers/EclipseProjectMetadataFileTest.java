/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.managers;

import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.filesystem.JLSFsUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EclipseProjectMetadataFileTest extends AbstractProjectsManagerBasedTest {
	@Parameter
	public String fsMode;

	@Parameters
	public static Collection<String> data(){
		return Arrays.asList("false", "true");
	}

	@Before
	public void setUp() {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
	}

	@Test
	public void testMetadataFileLocation() throws Exception {
		String name = "hello";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);

		IFile projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath projectDescriptionPath = FileUtil.toPath(projectDescription.getLocationURI());
		assertTrue(projectDescriptionPath.toFile().exists());
		assertTrue(project.getLocation().isPrefixOf(projectDescriptionPath));

		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath classpathPath = FileUtil.toPath(classpath.getLocationURI());
		assertTrue(classpathPath.toFile().exists());
		assertTrue(project.getLocation().isPrefixOf(classpathPath));

		IFile preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath preferencesPath = FileUtil.toPath(preferencesFile.getLocationURI());
		assertTrue(preferencesPath.toFile().exists());
		assertTrue(project.getLocation().isPrefixOf(preferencesPath));
	}

	@Test
	public void testDeleteClasspath() throws Exception {
		String name = "classpath2";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertNotNull(project);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IFile dotClasspath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		File file = FileUtil.toPath(dotClasspath.getLocationURI()).toFile();
		assertTrue(file.exists());
		file.delete();
		projectsManager.fileChanged(file.toPath().toUri().toString(), CHANGE_TYPE.DELETED);
		waitForBackgroundJobs();
		project = getProject(name);
		assertFalse(ProjectUtils.isJavaProject(project));
		IFile bin = project.getFile("bin");
		assertFalse(bin.getRawLocation().toFile().exists());
	}
}
