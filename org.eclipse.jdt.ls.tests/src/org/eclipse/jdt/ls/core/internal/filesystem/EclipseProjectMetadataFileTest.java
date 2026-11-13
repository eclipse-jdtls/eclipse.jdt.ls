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

package org.eclipse.jdt.ls.core.internal.filesystem;

import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class EclipseProjectMetadataFileTest extends AbstractProjectsManagerBasedTest {

	static Stream<Arguments> data() {
		return Stream.of(
			Arguments.of("false"),
			Arguments.of("true")
		);
	}

	@ParameterizedTest
	@MethodSource("data")
	void testMetadataFileLocation(String fsMode) throws Exception {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
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
	void testMetadataAreaWithoutProject() throws Exception {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, "false");
		String name = "deleted";
		File metadataFolder = ResourcesPlugin.getPlugin().getStateLocation().append(".projects").append(name).toFile();
		File location = ResourcesPlugin.getWorkspace().getRoot().getLocation().append(name).toFile();
		try {
			assertTrue(metadataFolder.mkdirs());
			assertTrue(new File(metadataFolder, IProjectDescription.DESCRIPTION_FILE_NAME).createNewFile());
			assertTrue(location.mkdirs());
			JLSFile store = new JLSFile(location);
			assertFalse(Arrays.asList(store.childNames(EFS.NONE, null)).contains(IProjectDescription.DESCRIPTION_FILE_NAME));
			assertFalse(Stream.of(store.childInfos(EFS.NONE, null)).anyMatch(info -> IProjectDescription.DESCRIPTION_FILE_NAME.equals(info.getName())));
		} finally {
			FileUtils.deleteDirectory(metadataFolder);
			FileUtils.deleteDirectory(location);
		}
	}

	@ParameterizedTest
	@MethodSource("data")
	void testDeleteClasspath(String fsMode) throws Exception {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
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

	@ParameterizedTest
	@MethodSource("data")
	void testDeleteNonMetadataClasspath(String fsMode) throws Exception {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
		String name = "classpath3";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertNotNull(project);
		IFile dotClasspath = project.getFile(new Path("resources/.classpath"));
		File file = FileUtil.toPath(dotClasspath.getLocationURI()).toFile();
		assertTrue(file.exists());
		file.delete();
		projectsManager.fileChanged(file.toPath().toUri().toString(), CHANGE_TYPE.DELETED);
		waitForBackgroundJobs();
		project = getProject(name);
		assertTrue(ProjectUtils.isJavaProject(project));
	}
}
