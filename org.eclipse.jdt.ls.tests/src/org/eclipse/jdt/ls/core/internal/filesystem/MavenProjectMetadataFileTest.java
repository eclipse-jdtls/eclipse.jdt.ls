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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractMavenBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MavenProjectMetadataFileTest extends AbstractMavenBasedTest {

	private static final String INVALID = "invalid";
	private static final String MAVEN_INVALID = "maven/invalid";

	@Parameter
	public String fsMode;

	@Parameters
	public static Collection<String> data(){
		return Arrays.asList("false", "true");
	}

	@Before
	public void setup() throws Exception {
		System.setProperty(ProjectsManager.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
	}

	@Test
	public void testMetadataFileLocation() throws Exception {
		String name = "salut";
		importProjects("maven/" + name);
		IProject project = WorkspaceHelper.getProject(name);
		assertTrue(project.exists());

		IFile projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath projectDescriptionPath = projectDescription.getLocation();
		assertTrue(projectDescriptionPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(projectDescriptionPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(project.getLocation().append(IProjectDescription.DESCRIPTION_FILE_NAME).toFile().exists(), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(ProjectsManager.generatesMetadataFilesAtProjectRoot(), new File(project.getLocation().toFile(), IProjectDescription.DESCRIPTION_FILE_NAME).exists());

		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		IPath classpathPath = classpath.getLocation();
		assertTrue(classpathPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(classpathPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(ProjectsManager.generatesMetadataFilesAtProjectRoot(), new File(project.getLocation().toFile(), IJavaProject.CLASSPATH_FILE_NAME).exists());

		IFile preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath preferencesPath = preferencesFile.getLocation();
		assertTrue(preferencesPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(preferencesPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(ProjectsManager.generatesMetadataFilesAtProjectRoot(), new File(project.getLocation().toFile(), EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME).exists());
	}

	@Test
	public void testMetadataFileSync() throws Exception {
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

	@Test
	public void testInvalidProject() throws Exception {
		List<IProject> projects = importProjects(MAVEN_INVALID);
		assertEquals(2, projects.size());
		IProject invalid = WorkspaceHelper.getProject(INVALID);
		assertIsMavenProject(invalid);
		IFile projectFile = invalid.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		assertTrue(projectFile.exists());
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		File file = projectFile.getLocation().toFile();
		invalid.close(new NullProgressMonitor());
		assertTrue(file.exists());
		file.delete();
		assertFalse(file.exists());
		projects = importProjects(MAVEN_INVALID);
		assertEquals(2, projects.size());
		invalid = WorkspaceHelper.getProject(INVALID);
		assertIsMavenProject(invalid);
	}

	@Test
	public void testDeleteClasspath() throws Exception {
		String name = "salut";
		importProjects("maven/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertIsMavenProject(project);
		IFile dotClasspath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		File file = dotClasspath.getLocation().toFile();
		assertTrue(file.exists());
		file.delete();
		projectsManager.fileChanged(file.toPath().toUri().toString(), CHANGE_TYPE.DELETED);
		project = getProject(name);
		IFile bin = project.getFile("bin");
		assertFalse(bin.getRawLocation().toFile().exists());
		assertTrue(dotClasspath.exists());
	}

	@Test
	public void testFactoryPathFileLocation() throws Exception {
		String name = "autovalued";
		importProjects("maven/" + name);
		IProject project = WorkspaceHelper.getProject(name);
		assertTrue(project.exists());

		IFile projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath projectDescriptionPath = projectDescription.getLocation();
		assertTrue(projectDescriptionPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(projectDescriptionPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());

		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		IPath classpathPath = classpath.getLocation();
		assertTrue(classpathPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(classpathPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());

		IFile preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		IPath preferencesPath = preferencesFile.getLocation();
		assertTrue(preferencesPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(preferencesPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());

		IFile factoryPathFile = project.getFile(".factorypath");
		IPath factoryPathFilePath = factoryPathFile.getLocation();
		assertTrue(factoryPathFilePath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(factoryPathFilePath), ProjectsManager.generatesMetadataFilesAtProjectRoot());
	}

	@Test
	public void testMultipleMetadataFile() throws Exception {
		if (ProjectsManager.generatesMetadataFilesAtProjectRoot()) {
			return;
		}
		String name = "quickstart2";
		importProjects("maven/" + name);
		IProject project = getProject(name);
		IFile projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		File projectDescriptionFile = projectDescription.getLocation().toFile();
		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		File classpathPathFile = classpath.getLocation().toFile();
		IFile preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		File preferencesPathFile = preferencesFile.getLocation().toFile();
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
