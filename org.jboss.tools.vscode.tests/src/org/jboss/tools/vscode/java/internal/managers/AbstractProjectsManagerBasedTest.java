/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.managers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.jboss.tools.vscode.java.internal.ProjectUtils;
import org.jboss.tools.vscode.java.internal.WorkspaceHelper;
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
		projectsManager = new ProjectsManager();
	}

	protected List<IProject> importProjects(String path) throws IOException {

		File from = new File(getSourceProjectDirectory(), path);
		File to = new File(getWorkingProjectDirectory(), path);
		if (to.exists()) {
			FileUtils.forceDelete(to);
		}
		FileUtils.copyDirectory(from, to);

		List<IProject> resultingProjects = new ArrayList<>();
		projectsManager.createProject(to.getAbsolutePath(), resultingProjects , monitor);
		return resultingProjects;
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

}
