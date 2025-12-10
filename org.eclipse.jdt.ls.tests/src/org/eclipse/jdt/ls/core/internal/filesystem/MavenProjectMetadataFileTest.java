/*******************************************************************************
 * Copyright (c) 2021-2022 Microsoft Corporation and others.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractMavenBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MavenProjectMetadataFileTest extends AbstractMavenBasedTest {

	private static final String INVALID = "invalid";
	private static final String MAVEN_INVALID = "maven/invalid";

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
		String name = "salut";
		importProjects("maven/" + name);
		IProject project = WorkspaceHelper.getProject(name);
		assertTrue(project.exists());

		IFile projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath projectDescriptionPath = FileUtil.toPath(projectDescription.getLocationURI());
		assertTrue(projectDescriptionPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(projectDescriptionPath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());

		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath classpathPath = FileUtil.toPath(classpath.getLocationURI());
		assertTrue(classpathPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(classpathPath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());

		IFile preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath preferencesPath = FileUtil.toPath(preferencesFile.getLocationURI());
		assertTrue(preferencesPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(preferencesPath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());
	}

	@ParameterizedTest
	@MethodSource("data")
	void testMetadataFileSync(String fsMode) throws Exception {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
		String name = "quickstart2";
		importProjects("maven/" + name);
		IProject project = WorkspaceHelper.getProject(name);
		assertTrue(project.exists());

		IFile pom = project.getFile("pom.xml");
		String content = ResourceUtils.getContent(pom);
		content = content.replaceAll(">11<", ">1.8<");
		ResourceUtils.setContent(pom, content);
		projectsManager.updateProject(project, false);
		waitForBackgroundJobs();

		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		String classpathContent = ResourceUtils.getContent(classpath);
		assertTrue(classpathContent.contains("StandardVMType/JavaSE-1.8"));
	}

	@ParameterizedTest
	@MethodSource("data")
	void testInvalidProject(String fsMode) throws Exception {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
		List<IProject> projects = importProjects(MAVEN_INVALID);
		assertEquals(1, projects.size());
		IProject invalid = WorkspaceHelper.getProject(INVALID);
		assertIsMavenProject(invalid);
		IFile projectFile = invalid.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		assertTrue(projectFile.exists());
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		File file = FileUtil.toPath(projectFile.getLocationURI()).toFile();
		invalid.close(new NullProgressMonitor());
		assertTrue(file.exists());
		file.delete();
		assertFalse(file.exists());
		projects = importProjects(MAVEN_INVALID);
		assertEquals(1, projects.size());
		invalid = WorkspaceHelper.getProject(INVALID);
		assertIsMavenProject(invalid);
	}

	@ParameterizedTest
	@MethodSource("data")
	void testDeleteClasspath(String fsMode) throws Exception {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
		String name = "salut";
		importProjects("maven/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertIsMavenProject(project);
		IFile dotClasspath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		File file = FileUtil.toPath(dotClasspath.getLocationURI()).toFile();
		assertTrue(file.exists());
		file.delete();
		projectsManager.fileChanged(file.toPath().toUri().toString(), CHANGE_TYPE.DELETED);
		project = getProject(name);
		IFile bin = project.getFile("bin");
		assertFalse(bin.getRawLocation().toFile().exists());
		assertTrue(dotClasspath.exists());
	}

	@ParameterizedTest
	@MethodSource("data")
	void testFactoryPathFileLocation(String fsMode) throws Exception {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
		String name = "autovalued";
		importProjects("maven/" + name);
		IProject project = WorkspaceHelper.getProject(name);
		assertTrue(project.exists());

		IFile projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath projectDescriptionPath = FileUtil.toPath(projectDescription.getLocationURI());
		assertTrue(projectDescriptionPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(projectDescriptionPath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());

		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath classpathPath = FileUtil.toPath(classpath.getLocationURI());
		assertTrue(classpathPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(classpathPath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());

		IFile preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath preferencesPath = FileUtil.toPath(preferencesFile.getLocationURI());
		assertTrue(preferencesPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(preferencesPath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());

		IFile factoryPathFile = project.getFile(JLSFsUtils.FACTORY_PATH);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath factoryPathFilePath = FileUtil.toPath(factoryPathFile.getLocationURI());
		assertTrue(factoryPathFilePath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(factoryPathFilePath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());
	}

	@ParameterizedTest
	@MethodSource("data")
	void testMultipleMetadataFile(String fsMode) throws Exception {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
		if (JLSFsUtils.generatesMetadataFilesAtProjectRoot()) {
			return;
		}
		String name = "quickstart2";
		importProjects("maven/" + name);
		IProject project = getProject(name);
		IFile projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		File projectDescriptionFile = FileUtil.toPath(projectDescription.getLocationURI()).toFile();
		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		File classpathPathFile = FileUtil.toPath(classpath.getLocationURI()).toFile();
		IFile preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		File preferencesPathFile = FileUtil.toPath(preferencesFile.getLocationURI()).toFile();
		FileUtils.copyFile(projectDescriptionFile, project.getLocation().append(IProjectDescription.DESCRIPTION_FILE_NAME).toFile());
		FileUtils.copyFile(classpathPathFile, project.getLocation().append(IJavaProject.CLASSPATH_FILE_NAME).toFile());
		FileUtils.copyDirectory(preferencesPathFile, project.getLocation().append(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME).toFile());

		projectsManager.updateProject(project, true);
		waitForBackgroundJobs();
		assertNoErrors(project);

		IFile pom = project.getFile("pom.xml");
		String content = ResourceUtils.getContent(pom);
		content = content.replaceAll(">11<", ">1.8<");
		content = content.replace(">11<", ">1.8<");
		ResourceUtils.setContent(pom, content);
		projectsManager.updateProject(project, false);
		waitForBackgroundJobs();

		// if the metadata file stores both at project root & workspace, the file at project root wins.
		String newContent = Files.readString(project.getLocation().append(IJavaProject.CLASSPATH_FILE_NAME).toPath());
		assertTrue(newContent.contains("StandardVMType/JavaSE-1.8"));
	}
}
