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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.junit.After;
import org.junit.Before;

/**
 * @author Fred Bricon
 *
 */
public abstract class AbstractProjectsManagerBasedTest {

	protected IProgressMonitor monitor = new NullProgressMonitor();
	protected ProjectsManager projectsManager;

	@Before
	public void initProjectManager() {
		projectsManager = new ProjectsManager(new PreferenceManager());
	}

	protected List<IProject> importProjects(String path) throws Exception {
		File from = new File(getSourceProjectDirectory(), path);
		File to = new File(getWorkingProjectDirectory(), path);
		if (to.exists()) {
			FileUtils.forceDelete(to);
		}
		FileUtils.copyDirectory(from, to);
		projectsManager.initializeProjects(to.getAbsolutePath(), monitor);
		waitForBackgroundJobs();
		return WorkspaceHelper.getAllProjects();
	}

	protected void waitForBackgroundJobs() throws Exception {
		JobHelpers.waitForJobsToComplete(monitor);
	}

	protected File getSourceProjectDirectory() {
		return new File("projects");
	}

	protected File getWorkingProjectDirectory() throws IOException {
		File dir = new File("target", "workingProjects");
		FileUtils.forceMkdir(dir);
		return dir;
	}

	@After
	public void cleanUp() throws Exception {
		projectsManager = null;
		WorkspaceHelper.deleteAllProjects();
		FileUtils.forceDelete(getWorkingProjectDirectory());
	}

	protected void assertIsJavaProject(IProject project) {
		assertNotNull(project);
		assertTrue(project.getName() +" is missing the Java nature", ProjectUtils.isJavaProject(project));
	}

	protected void assertHasErrors(IProject project) {
		try {
			assertTrue(project.getName() + " has no errors", ResourceUtils.getErrorMarkers(project).size() > 0);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected void assertNoErrors(IProject project) {
		try {
			List<IMarker> markers = ResourceUtils.getErrorMarkers(project);
			assertEquals(project.getName() + " has errors: \n"+ResourceUtils.toString(markers), 0, markers.size());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
