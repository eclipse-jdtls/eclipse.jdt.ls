/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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

import static org.eclipse.jdt.ls.core.internal.JobHelpers.waitForJobsToComplete;
import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.internal.resources.Resource;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.BuildWorkspaceHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.lsp4j.InitializeParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * @author Fred Bricon
 *
 */
public class ProjectsManagerTest extends AbstractProjectsManagerBasedTest {

	private JavaLanguageClient client = mock(JavaLanguageClient.class);
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
		server.shutdown();
		JavaLanguageServerPlugin.getInstance().setProtocol(null);
		try {
			ProjectsManager.setAutoBuilding(autoBuild);
			preferenceManager.getPreferences().setAutobuildEnabled(autoBuild);
			preferenceManager.getPreferences().setProjectConfigurations(null);
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
		assertEquals(ProjectsManager.getDefaultProject(), result);
		assertTrue("the default project doesn't exist", result.exists());
	}

	@Test
	public void testCleanupDefaultProject() throws Exception {
		projectsManager.initializeProjects(Collections.emptyList(), monitor);
		waitForJobsToComplete();
		IFile file = linkFilesToDefaultProject("singlefile/WithError.java");
		java.io.File physicalFile = new java.io.File(file.getLocationURI());
		physicalFile.delete();
		BuildWorkspaceHandler handler = new BuildWorkspaceHandler(projectsManager);
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
		JobHelpers.waitForJobs(IConstants.UPDATE_WORKSPACE_FOLDERS_FAMILY, new NullProgressMonitor());
		// https://github.com/eclipse/eclipse.jdt.ls/issues/2326
		// Job.getResult() returns null if the job has never finished running.
		// we can verify that the job is gone
		// assertTrue("the init job hasn't been cancelled, status is: " + initWorkspaceJob.getResult().getSeverity(), initWorkspaceJob.getResult().matches(IStatus.CANCEL));
		assertEquals("the init job hasn't been stopped, status is: " + initWorkspaceJob.getState(), 0, Job.getJobManager().find(rootPaths).length);
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
		JobHelpers.waitForJobs(IConstants.UPDATE_WORKSPACE_FOLDERS_FAMILY, new NullProgressMonitor());
		// assertTrue("the update job hasn't been cancelled, status is: " + updateWorkspaceJob.getResult().getSeverity(), updateWorkspaceJob.getResult().matches(IStatus.CANCEL));
		assertEquals("the init job hasn't been stopped, status is: " + updateWorkspaceJob.getState(), 0, Job.getJobManager().find(addedRootPaths).length);
	}

	@Test
	public void testResourceFilters() throws Exception {
		List<String> resourceFilters = preferenceManager.getPreferences().getResourceFilters();
		try {
			String name = "salut";
			importProjects("maven/" + name);
			IProject project = getProject(name);
			assertIsJavaProject(project);
			Resource nodeModules = (Resource) project.getFolder("/node_modules");
			assertFalse(nodeModules.isFiltered());
			Resource git = (Resource) project.getFolder("/.git");
			assertFalse(git.isFiltered());
			Resource src = (Resource) project.getFolder("/src");
			assertFalse(src.isFiltered());
			preferenceManager.getPreferences().setResourceFilters(Arrays.asList("node_modules", "\\.git"));
			projectsManager.configureFilters(new NullProgressMonitor());
			waitForJobsToComplete();
			nodeModules = (Resource) project.getFolder("/node_modules");
			assertTrue(nodeModules.isFiltered());
			git = (Resource) project.getFolder("/.git");
			assertTrue(git.isFiltered());
			src = (Resource) project.getFolder("/src");
			assertFalse(src.isFiltered());
			preferenceManager.getPreferences().setResourceFilters(null);
			projectsManager.configureFilters(new NullProgressMonitor());
			waitForJobsToComplete();
			nodeModules = (Resource) project.getFolder("/node_modules");
			assertFalse(nodeModules.isFiltered());
			git = (Resource) project.getFolder("/.git");
			assertFalse(git.isFiltered());
			src = (Resource) project.getFolder("/src");
			assertFalse(src.isFiltered());
		} finally {
			preferenceManager.getPreferences().setResourceFilters(resourceFilters);
		}
	}

	@Test
	public void dontFilterGitLikePackages() throws Exception {
		//See https://github.com/eclipse/eclipse.jdt.ls/issues/2244
		String name = "gitfilter";
		importProjects("eclipse/" + name);
		projectsManager.configureFilters(monitor);
		Thread.sleep(300);//FIXME waitForJobsToComplete is ineffective on its own O_o
		waitForJobsToComplete(monitor);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertNoErrors(project);
	}

	@Test
	public void testImportMavenSubModule() throws IOException, OperationCanceledException, CoreException {
		Path projectDir = copyFiles("maven/multimodule", true).toPath();
		Collection<IPath> configurationPaths = new ArrayList<>();
		Path subModuleConfiguration = projectDir.resolve("module1/pom.xml");
		IPath filePath = ResourceUtils.canonicalFilePathFromURI(subModuleConfiguration.toUri().toString());
		configurationPaths.add(filePath);
		preferenceManager.getPreferences().setProjectConfigurations(configurationPaths);
		projectsManager.initializeProjects(Collections.singleton(new org.eclipse.core.runtime.Path(projectDir.toString())), monitor);
		IProject[] allProjects = ProjectUtils.getAllProjects();
		Set<String> expectedProjects = new HashSet<>(Arrays.asList(
			"module1",
			"childmodule",
			"jdt.ls-java-project"
		));
		assertEquals(3, allProjects.length);
		for (IProject project : allProjects) {
			assertTrue(expectedProjects.contains(project.getName()));
		}
	}

	@Test
	public void testImportMixedProjects() throws IOException, OperationCanceledException, CoreException {
		Path projectDir = copyFiles("mixed", true).toPath();
		Collection<IPath> configurationPaths = new ArrayList<>();
		configurationPaths.add(ResourceUtils.canonicalFilePathFromURI(projectDir.resolve("hello/.project").toUri().toString()));
		configurationPaths.add(ResourceUtils.canonicalFilePathFromURI(projectDir.resolve("simple-gradle/build.gradle").toUri().toString()));
		configurationPaths.add(ResourceUtils.canonicalFilePathFromURI(projectDir.resolve("salut/pom.xml").toUri().toString()));
		preferenceManager.getPreferences().setProjectConfigurations(configurationPaths);
		projectsManager.initializeProjects(Collections.singleton(new org.eclipse.core.runtime.Path(projectDir.toString())), monitor);
		IProject[] allProjects = ProjectUtils.getAllProjects();
		Set<String> expectedProjects = new HashSet<>(Arrays.asList(
			"jdt.ls-java-project",
			"hello",
			"salut",
			"simple-gradle"
		));
		assertEquals(4, allProjects.length);
		for (IProject project : allProjects) {
			assertTrue(expectedProjects.contains(project.getName()));
		}
	}

	@Test
	public void testImportMixedProjectsPartially() throws IOException, OperationCanceledException, CoreException {
		Path projectDir = copyFiles("mixed", true).toPath();
		Collection<IPath> configurationPaths = new ArrayList<>();
		configurationPaths.add(ResourceUtils.canonicalFilePathFromURI(projectDir.resolve("simple-gradle/build.gradle").toUri().toString()));
		configurationPaths.add(ResourceUtils.canonicalFilePathFromURI(projectDir.resolve("salut/pom.xml").toUri().toString()));
		preferenceManager.getPreferences().setProjectConfigurations(configurationPaths);
		projectsManager.initializeProjects(Collections.singleton(new org.eclipse.core.runtime.Path(projectDir.toString())), monitor);
		IProject[] allProjects = ProjectUtils.getAllProjects();
		Set<String> expectedProjects = new HashSet<>(Arrays.asList(
			"jdt.ls-java-project",
			"salut",
			"simple-gradle"
		));
		assertEquals(3, allProjects.length);
		for (IProject project : allProjects) {
			assertTrue(expectedProjects.contains(project.getName()));
		}
	}

	@Test
	public void testSendingOKProjectStatus() throws Exception {
		importProjects("gradle/simple-gradle");
		IProject project = WorkspaceHelper.getProject("simple-gradle");
		JDTLanguageServer server = mock(JDTLanguageServer.class);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
		doNothing().when(server).sendStatus(any(), any());
		projectsManager.updateProject(project, false);
		waitForBackgroundJobs();
		ArgumentCaptor<ServiceStatus> status = ArgumentCaptor.forClass(ServiceStatus.class);
		ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
		verify(server, times(2)).sendStatus(status.capture(), msg.capture());
		assertEquals(ServiceStatus.ProjectStatus, status.getValue());
		assertEquals("OK", msg.getValue());
	}

	@Test
	public void testSendingWarningProjectStatus() throws Exception {
		importProjects("gradle/invalid");
		IProject project = WorkspaceHelper.getProject("invalid");
		JDTLanguageServer server = mock(JDTLanguageServer.class);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
		doNothing().when(server).sendStatus(any(), any());
		projectsManager.updateProject(project, false);
		waitForBackgroundJobs();
		ArgumentCaptor<ServiceStatus> status = ArgumentCaptor.forClass(ServiceStatus.class);
		ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
		verify(server, times(2)).sendStatus(status.capture(), msg.capture());
		assertEquals(ServiceStatus.ProjectStatus, status.getValue());
		assertEquals("WARNING", msg.getValue());
	}

	@Test
	public void testReloadMavenProjectMarker() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		IFile pom = project.getFile("pom.xml");
		IMarker[] markers = pom.findMarkers(ProjectsManager.BUILD_FILE_MARKER_TYPE, false, IResource.DEPTH_ZERO);
		assertEquals(0, markers.length);

		URI pomUri = pom.getRawLocationURI();
		String originalPom = ResourceUtils.getContent(pomUri);
		ResourceUtils.setContent(pomUri, originalPom + "\n");
		projectsManager.fileChanged(pomUri.toString(), CHANGE_TYPE.CHANGED);
		waitForBackgroundJobs();
		markers = pom.findMarkers(ProjectsManager.BUILD_FILE_MARKER_TYPE, false, IResource.DEPTH_ZERO);
		assertEquals(1, markers.length);

		projectsManager.updateProject(project, true);
		waitForBackgroundJobs();
		markers = pom.findMarkers(ProjectsManager.BUILD_FILE_MARKER_TYPE, false, IResource.DEPTH_ZERO);
		assertEquals(0, markers.length);
	}

	@Test
	public void testReloadGradleProjectMarker() throws Exception {
		importProjects("gradle/sample");
		IProject project = WorkspaceHelper.getProject("sample");
		IFile gradle = project.getFile("settings.gradle");
		IMarker[] markers = gradle.findMarkers(ProjectsManager.BUILD_FILE_MARKER_TYPE, false, IResource.DEPTH_ZERO);
		assertEquals(0, markers.length);

		URI gradleUri = gradle.getRawLocationURI();
		String originalGradle = ResourceUtils.getContent(gradleUri);
		ResourceUtils.setContent(gradleUri, originalGradle + "\n");
		projectsManager.fileChanged(gradleUri.toString(), CHANGE_TYPE.CHANGED);
		waitForBackgroundJobs();
		markers = gradle.findMarkers(ProjectsManager.BUILD_FILE_MARKER_TYPE, false, IResource.DEPTH_ZERO);
		assertEquals(1, markers.length);

		projectsManager.updateProject(project, true);
		waitForBackgroundJobs();
		markers = gradle.findMarkers(ProjectsManager.BUILD_FILE_MARKER_TYPE, false, IResource.DEPTH_ZERO);
		assertEquals(0, markers.length);
	}
}
