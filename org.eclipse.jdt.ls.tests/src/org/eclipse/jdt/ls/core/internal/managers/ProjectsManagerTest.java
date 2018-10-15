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

import static org.eclipse.jdt.ls.core.internal.JobHelpers.waitForJobsToComplete;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.BuildWorkspaceHandler;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class ProjectsManagerTest extends AbstractProjectsManagerBasedTest {

	private JavaLanguageClient client = mock(JavaLanguageClient.class);
	private JavaClientConnection javaClient = new JavaClientConnection(client);

	@Test
	public void testCreateDefaultProject() throws Exception {
		projectsManager.initializeProjects(Collections.emptyList(), monitor);
		waitForJobsToComplete();
		List<IProject> projects = WorkspaceHelper.getAllProjects();
		assertEquals(1, projects.size());
		IProject result = projects.get(0);
		assertNotNull(result);
		assertEquals(projectsManager.getDefaultProject(), result);
		assertTrue("the default project doesn't exist", result.exists());
	}

	@Test
	public void testCleanupDefaultProject() throws Exception {
		projectsManager.initializeProjects(Collections.emptyList(), monitor);
		waitForJobsToComplete();
		IFile file = linkFilesToDefaultProject("singlefile/WithError.java");
		java.io.File physicalFile = new java.io.File(file.getLocationURI());
		physicalFile.delete();

		BuildWorkspaceHandler handler = new BuildWorkspaceHandler(javaClient, projectsManager);
		BuildWorkspaceStatus result = handler.buildWorkspace(true, monitor);

		waitForBackgroundJobs();
		assertEquals(String.format("BuildWorkspaceStatus is: %s.", result.toString()), BuildWorkspaceStatus.SUCCEED, result);
	}

}
