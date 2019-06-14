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

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.BuildWorkspaceHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.lsp4j.InitializeParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class ProjectsManagerTest extends AbstractProjectsManagerBasedTest {

	private JavaLanguageClient client = mock(JavaLanguageClient.class);
	private JavaClientConnection javaClient = new JavaClientConnection(client);
	private JDTLanguageServer server;
	private boolean autoBuild;

	@Before
	public void setup() throws Exception {
		autoBuild = preferenceManager.getPreferences().isAutobuildEnabled();
		server = new JDTLanguageServer(projectsManager, preferenceManager);
		server.connectClient(client);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
	}

	@After
	public void tearDown() {
		server.disconnectClient();
		JavaLanguageServerPlugin.getInstance().setProtocol(null);
		try {
			projectsManager.setAutoBuilding(autoBuild);
			preferenceManager.getPreferences().setAutobuildEnabled(autoBuild);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

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

	@Test
	public void testCancelInitJob() throws Exception {
		File workspaceDir = copyFiles("maven/salut", true);
		String rootPathURI = workspaceDir.toURI().toString();
		Collection<IPath> rootPaths = Collections.singleton(ResourceUtils.canonicalFilePathFromURI(rootPathURI));
		InitializeParams params = new InitializeParams();
		params.setRootUri(rootPathURI);
		server.initialize(params);
		Job[] initWorkspaceJobs = Job.getJobManager().find(rootPaths);
		assertEquals(1, initWorkspaceJobs.length);
		Job initWorkspaceJob = initWorkspaceJobs[0];
		assertNotNull(initWorkspaceJob);
		projectsManager.updateWorkspaceFolders(Collections.emptySet(), rootPaths);
		waitForBackgroundJobs();
		assertTrue("the init job hasn't been cancelled, status is: " + initWorkspaceJob.getResult().getSeverity(), initWorkspaceJob.getResult().matches(IStatus.CANCEL));
	}

	@Test
	public void testCancelUpdateJob() throws Exception {
		File workspaceDir = copyFiles("maven/salut", true);
		Collection<IPath> addedRootPaths = Collections.singleton(new org.eclipse.core.runtime.Path(workspaceDir.toString()));
		projectsManager.updateWorkspaceFolders(addedRootPaths, Collections.emptySet());
		Job[] updateWorkspaceJobs = Job.getJobManager().find(addedRootPaths);
		assertEquals(1, updateWorkspaceJobs.length);
		Job updateWorkspaceJob = updateWorkspaceJobs[0];
		assertNotNull(updateWorkspaceJob);
		projectsManager.updateWorkspaceFolders(Collections.emptySet(), addedRootPaths);
		waitForBackgroundJobs();
		assertTrue("the update job hasn't been cancelled, status is: " + updateWorkspaceJob.getResult().getSeverity(), updateWorkspaceJob.getResult().matches(IStatus.CANCEL));
	}

}
