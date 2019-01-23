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
import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class GradleProjectImporterTest extends AbstractGradleBasedTest{

	private static final String GRADLE1_PATTERN = "**/gradle1";

	@Test
	public void importSimpleGradleProject() throws Exception {
		importSimpleJavaProject();
		assertTaskCompleted(GradleProjectImporter.IMPORTING_GRADLE_PROJECTS);
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
	public void testDisableGradle() throws Exception {
		boolean enabled = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isImportGradleEnabled();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleEnabled(false);
			List<IProject> projects = importProjects("eclipse/eclipsegradle");
			assertEquals(2, projects.size());//default + 1 eclipse projects
			IProject eclipse = WorkspaceHelper.getProject("eclipse");
			assertNotNull(eclipse);
			assertFalse(eclipse.getName() + " has the Gradle nature", ProjectUtils.isGradleProject(eclipse));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleEnabled(enabled);
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

	@Test
	public void testWorkspaceSettings() throws Exception {
		Map<String, String> env = new HashMap<>();
		Properties sysprops = new Properties();
		File file = null;
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			File rootFile = root.getLocation().toFile();
			file = new File(rootFile, "fakeGradleHome");
			sysprops.setProperty(GradleProjectImporter.GRADLE_HOME, file.getAbsolutePath());
			boolean overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertFalse(overrideWorkspaceSettings);
			file.mkdir();
			file.deleteOnExit();
			overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertTrue(overrideWorkspaceSettings);
		} finally {
			if (file != null) {
				file.delete();
			}
		}
	}

	@Test
	public void testBuildFile() throws Exception {
		IProject project = importSimpleJavaProject();
		IFile file = project.getFile("/bin/build.gradle");
		assertFalse(projectsManager.isBuildFile(file));
		importProjects("gradle/gradle-withoutjava");
		project = getProject("gradle-withoutjava");
		file = project.getFile("/build.gradle");
		assertTrue(projectsManager.isBuildFile(file));
	}

	@Test
	public void testGradlePropertiesFile() throws Exception {
		IProject project = importSimpleJavaProject();
		IFile file = project.getFile("/bin/gradle.properties");
		assertFalse(projectsManager.isBuildFile(file));
		importProjects("gradle/gradle-withoutjava");
		project = getProject("gradle-withoutjava");
		file = project.getFile("/gradle.properties");
		assertTrue(projectsManager.isBuildFile(file));
	}

	@Test
	public void testJava11Project() throws Exception {
		IProject project = importGradleProject("gradle-11");
		assertIsJavaProject(project);
		assertEquals("11", getJavaSourceLevel(project));
		assertNoErrors(project);
	}
}
