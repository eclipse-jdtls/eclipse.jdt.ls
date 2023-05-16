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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractGradleBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GradleProjectMetadataFileTest extends AbstractGradleBasedTest {
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
		String name = "sample";
		importProjects("gradle/" + name);
		IProject project = WorkspaceHelper.getProject(name);
		assertTrue(project.exists());

		// first verify the root module
		IFile projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath projectDescriptionPath = FileUtil.toPath(projectDescription.getLocationURI());
		assertTrue(projectDescriptionPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(projectDescriptionPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(ProjectsManager.generatesMetadataFilesAtProjectRoot(), new File(project.getLocation().toFile(), IProjectDescription.DESCRIPTION_FILE_NAME).exists());

		IFile preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		IPath preferencesPath = preferencesFile.getLocation();
		assertTrue(preferencesPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(preferencesPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(ProjectsManager.generatesMetadataFilesAtProjectRoot(), new File(project.getLocation().toFile(), EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME).exists());

		// then we check the sub-module
		project = WorkspaceHelper.getProject("app");
		projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		projectDescriptionPath = projectDescription.getLocation();
		assertTrue(projectDescriptionPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(projectDescriptionPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(project.getLocation().append(IProjectDescription.DESCRIPTION_FILE_NAME).toFile().exists(), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(ProjectsManager.generatesMetadataFilesAtProjectRoot(), new File(project.getLocation().toFile(), IProjectDescription.DESCRIPTION_FILE_NAME).exists());

		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		IPath classpathPath = classpath.getLocation();
		assertTrue(classpathPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(classpathPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(ProjectsManager.generatesMetadataFilesAtProjectRoot(), new File(project.getLocation().toFile(), IJavaProject.CLASSPATH_FILE_NAME).exists());

		preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		preferencesPath = preferencesFile.getLocation();
		assertTrue(preferencesPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(preferencesPath), ProjectsManager.generatesMetadataFilesAtProjectRoot());
		assertEquals(ProjectsManager.generatesMetadataFilesAtProjectRoot(), new File(project.getLocation().toFile(), EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME).exists());
	}

	@Test
	public void testMetadataFileLocation2() throws Exception {
		String name = "metadata";
		importProjects("gradle/" + name);
		IProject project = WorkspaceHelper.getProject(name);
		projectsManager.updateProject(project, true);
		waitForBackgroundJobs();

		List<IMarker> markers = ResourceUtils.findMarkers(project, IMarker.SEVERITY_ERROR);
		assertEquals(Collections.emptyList(), markers);
	}

	@Test
	public void testSettingsGradle() throws Exception {
		List<IProject> projects = importProjects("gradle/sample");
		assertEquals(3, projects.size());//default, app, sample
		IProject root = WorkspaceHelper.getProject("sample");
		assertIsGradleProject(root);
		IProject project = WorkspaceHelper.getProject("app");
		assertIsGradleProject(project);
		assertIsJavaProject(project);
		IJavaProject javaProject = JavaCore.create(project);
		IType type = javaProject.findType("org.apache.commons.lang3.StringUtils");
		assertNull(type);
		IFile build2 = project.getFile("/build.gradle2");
		InputStream contents = build2.getContents();
		IFile build = project.getFile("/build.gradle");
		build.setContents(contents, true, false, null);
		projectsManager.updateProject(project, false);
		waitForBackgroundJobs();
		type = javaProject.findType("org.apache.commons.lang3.StringUtils");
		assertNotNull(type);
	}

	@Test
	public void testDeleteClasspath() throws Exception {
		FeatureStatus status = preferenceManager.getPreferences().getUpdateBuildConfigurationStatus();
		try {
			preferenceManager.getPreferences().setUpdateBuildConfigurationStatus(FeatureStatus.automatic);
			IProject project = importSimpleJavaProject();
			assertIsJavaProject(project);
			assertIsGradleProject(project);
			IFile dotClasspath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
			File file = dotClasspath.getLocation().toFile();
			assertTrue(file.exists());
			file.delete();
			projectsManager.fileChanged(file.toPath().toUri().toString(), CHANGE_TYPE.DELETED);
			waitForBackgroundJobs();
			Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
			project = WorkspaceHelper.getProject("simple-gradle");
			assertIsGradleProject(project);
			assertIsJavaProject(project);
			IFile bin = project.getFile("bin");
			assertFalse(bin.getRawLocation().toFile().exists());
			assertTrue(dotClasspath.exists());
		} finally {
			preferenceManager.getPreferences().setUpdateBuildConfigurationStatus(status);
		}
	}
}
