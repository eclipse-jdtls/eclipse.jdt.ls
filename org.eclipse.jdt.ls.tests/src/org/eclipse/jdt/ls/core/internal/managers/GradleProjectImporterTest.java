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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.junit.After;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class GradleProjectImporterTest extends AbstractGradleBasedTest{

	private static final String GRADLE1_PATTERN = "**/gradle1";

	@Test
	public void importSimpleGradleProject() throws Exception {
		importSimpleJavaProject();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest#cleanUp()
	 */
	@Override
	@After
	public void cleanUp() throws Exception {
		super.cleanUp();
		Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
	}

	@Test
	public void importNestedGradleProject() throws Exception {
		List<IProject> projects = importProjects("gradle/nested");
		assertEquals(3, projects.size());//default + 2 gradle projects
		IProject gradle1 = WorkspaceHelper.getProject("gradle1");
		assertIsGradleProject(gradle1);
		IProject gradle2 = WorkspaceHelper.getProject("gradle2");
		assertIsGradleProject(gradle2);
	}

	@Test
	public void testJavaImportExclusions() throws Exception {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		try {
			javaImportExclusions.add(GRADLE1_PATTERN);
			List<IProject> projects = importProjects("gradle/nested");
			assertEquals(2, projects.size());//default + 1 gradle projects
			IProject gradle1 = WorkspaceHelper.getProject("gradle1");
			assertNull(gradle1);
			IProject gradle2 = WorkspaceHelper.getProject("gradle2");
			assertIsGradleProject(gradle2);
		} finally {
			javaImportExclusions.remove(GRADLE1_PATTERN);
		}
	}

	@Test
	public void testGradlePersistence() throws Exception {
		importProjects("gradle/nested");
		List<IProject> projects = ProjectUtils.getGradleProjects();
		for (IProject project : projects) {
			assertTrue(GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()));
		}
		Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
		GradleBuildSupport.saveModels();
		for (IProject project : projects) {
			assertFalse(GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()));
		}
		IProject project = WorkspaceHelper.getProject("gradle1");
		File gradleBuild = new File(project.getLocation().toFile(), "build.gradle");
		gradleBuild.setLastModified(System.currentTimeMillis() + 1000);
		assertTrue(GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()));
	}

}
