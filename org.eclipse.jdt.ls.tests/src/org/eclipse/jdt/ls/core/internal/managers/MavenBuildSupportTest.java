/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import static org.eclipse.jdt.ls.core.internal.ProjectUtils.getJavaSourceLevel;
import static org.eclipse.jdt.ls.core.internal.ResourceUtils.getContent;
import static org.eclipse.jdt.ls.core.internal.ResourceUtils.setContent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.BinaryType;
import org.eclipse.jdt.ls.core.internal.DependencyUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.SourceContentProvider;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class MavenBuildSupportTest extends AbstractMavenBasedTest {

	@Test
	public void testUpdate() throws Exception {
		IProject project = importSimpleJavaProject();

		IFile pom = project.getFile("pom.xml");
		URI pomUri = pom.getRawLocationURI();

		//Remove dependencies to cause compilation errors
		String originalPom = getContent(pomUri);
		String dependencyLessPom = comment(originalPom, "<dependencies>", "</dependencies>");
		setContent(pomUri, dependencyLessPom);
		waitForBackgroundJobs();
		//Contents changed outside the workspace, so should not change
		assertNoErrors(project);

		projectsManager.updateProject(project, false);

		//Giving a nudge, so that errors show up
		waitForBackgroundJobs();
		assertHasErrors(project);

		//Fix pom, trigger build
		setContent(pomUri, originalPom);
		projectsManager.updateProject(project, false);
		waitForBackgroundJobs();
		assertNoErrors(project);
	}

	@Test
	public void testCompileWithErrorProne() throws Exception {
		testNonStandardCompilerId("compile-with-error-prone");
	}

	@Test
	public void testCompileWithEclipse() throws Exception {
		testNonStandardCompilerId("compile-with-eclipse");
	}

	@Test
	public void testCompileWithEclipseTychoJdt() throws Exception {
		testNonStandardCompilerId("compile-with-tycho-jdt");
	}

	@Test
	public void testInvalidProjects() throws Exception {
		IProject project = importMavenProject("multimodule2");
		Set<IProject> projects = new LinkedHashSet<>();
		new MavenBuildSupport().collectProjects(projects, project, new NullProgressMonitor());
		assertEquals(projects.size(), 1);
	}

	@Test
	public void testMultipleProjects() throws Exception {
		IProject project = importMavenProject("multimodule");
		Set<IProject> projects = new LinkedHashSet<>();
		new MavenBuildSupport().collectProjects(projects, project, new NullProgressMonitor());
		assertEquals(projects.size(), 4);
		for (IProject p : projects) {
			if ("module3".equals(p.getName())) {
				fail("module3 exists");
			}
		}
	}

	@Test
	public void testIgnoreInnerPomChanges() throws Exception {
		IProject project = importMavenProject("archetyped");
		assertEquals("The inner pom should not have been imported", 2, WorkspaceHelper.getAllProjects().size());

		IFile innerPom = project.getFile("src/main/resources/archetype-resources/pom.xml");

		preferences.setUpdateBuildConfigurationStatus(FeatureStatus.automatic);
		boolean[] updateTriggered = new boolean[1];
		IJobChangeListener listener = new JobChangeAdapter() {
			@Override
			public void scheduled(IJobChangeEvent event) {
				if (event.getJob().getName().contains("Update project")) {
					updateTriggered[0] = true;
				}
			}
		};
		try {
			Job.getJobManager().addJobChangeListener(listener);
			projectsManager.fileChanged(innerPom.getRawLocationURI().toString(), CHANGE_TYPE.CHANGED);
			waitForBackgroundJobs();
			assertFalse("Update project should not have been triggered", updateTriggered[0]);
		} finally {
			Job.getJobManager().removeJobChangeListener(listener);
		}
	}

	@Test
	public void testBuildHelperSupport() throws Exception {
		IProject project = importMavenProject("buildhelped");
		project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		assertIsJavaProject(project);
		assertNoErrors(project);
	}

	@Test
	public void testDownloadSources() throws Exception {
		File file = DependencyUtil.getSources("org.apache.commons", "commons-lang3", "3.5");
		FileUtils.deleteDirectory(file.getParentFile());
		boolean mavenDownloadSources = preferences.isMavenDownloadSources();
		try {
			preferences.setMavenDownloadSources(false);
			IProject project = importMavenProject("salut");
			waitForBackgroundJobs();
			assertTrue(!file.exists());
			IJavaProject javaProject = JavaCore.create(project);
			IType type = javaProject.findType("org.apache.commons.lang3.StringUtils");
			IClassFile classFile = ((BinaryType) type).getClassFile();
			assertNull(classFile.getBuffer());
			String source = new SourceContentProvider().getSource(classFile, new NullProgressMonitor());
			if (source == null) {
				JobHelpers.waitForDownloadSourcesJobs(JobHelpers.MAX_TIME_MILLIS);
				source = new SourceContentProvider().getSource(classFile, new NullProgressMonitor());
			}
			assertNotNull("Couldn't find source for " + type.getFullyQualifiedName() + "(" + file.getAbsolutePath() + (file.exists() ? " exists)" : " is missing)"), source);
		} finally {
			preferences.setMavenDownloadSources(mavenDownloadSources);
		}
	}

	@Test
	public void testUpdateSnapshots() throws Exception {
		boolean updateSnapshots = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isMavenUpdateSnapshots();
		FeatureStatus status = preferenceManager.getPreferences().getUpdateBuildConfigurationStatus();
		try {
			IProject project = importMavenProject("salut3");
			waitForBackgroundJobs();
			IJavaProject javaProject = JavaCore.create(project);
			IType type = javaProject.findType("org.apache.commons.lang3.StringUtils");
			assertNull(type);
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setMavenUpdateSnapshots(false);
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setUpdateBuildConfigurationStatus(FeatureStatus.automatic);
			IFile pom = project.getFile("pom.xml");
			String content = ResourceUtils.getContent(pom);
			//@formatter:off
			content = content.replace("<dependencies></dependencies>",
					"<dependencies>\n"
						+ "<dependency>\n"
						+ "   <groupId>org.apache.commons</groupId>\n"
						+ "   <artifactId>commons-lang3</artifactId>\n"
						+ "   <version>3.9</version>\n"
						+ "</dependency>"
						+ "</dependencies>");
			//@formatter:on
			ResourceUtils.setContent(pom, content);
			URI uri = pom.getRawLocationURI();
			projectsManager.fileChanged(uri.toString(), CHANGE_TYPE.CHANGED);
			waitForBackgroundJobs();
			type = javaProject.findType("org.apache.commons.lang3.StringUtils");
			assertNotNull(type);
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setMavenUpdateSnapshots(updateSnapshots);
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setUpdateBuildConfigurationStatus(status);
		}
	}

	@Test
	public void testBatchImport() throws Exception {
		IProject project = importMavenProject("batch");
		waitForBackgroundJobs();
		assertTrue(ProjectUtils.isMavenProject(project));
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		assertEquals(root.getProjects().length, 14);
		project = root.getProject("batchchild");
		assertTrue(ProjectUtils.isMavenProject(project));
	}

	protected void testNonStandardCompilerId(String projectName) throws Exception {
		IProject project = importMavenProject(projectName);
		assertIsJavaProject(project);
		assertEquals("1.8", getJavaSourceLevel(project));
		assertNoErrors(project);
	}
}
