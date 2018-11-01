/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
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

	protected void testNonStandardCompilerId(String projectName) throws Exception {
		IProject project = importMavenProject(projectName);
		assertIsJavaProject(project);
		assertEquals("1.8", getJavaSourceLevel(project));
		assertNoErrors(project);
	}
}
