/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author snjeza
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ImportNewProjectsTest extends AbstractProjectsManagerBasedTest {

	protected JDTLanguageServer server;
	@Mock
	private JavaLanguageClient client;
	private Path projectDir;

	@Before
	public void setup() throws Exception {
		server = new JDTLanguageServer(projectsManager, preferenceManager);
		server.connectClient(client);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
		projectDir = Files.createTempDirectory("test");
		Collection<IPath> rootPaths = new ArrayList<>();
		IPath rootPath = new org.eclipse.core.runtime.Path(projectDir.toFile().getAbsolutePath());
		rootPaths.add(rootPath);
		preferenceManager.getPreferences().setRootPaths(rootPaths);
		Job updateJob = projectsManager.updateWorkspaceFolders(Collections.singleton(new org.eclipse.core.runtime.Path(projectDir.toString())), Collections.emptySet());
		updateJob.join(20000, monitor);
	}

	@After
	public void tearDown() {
		server.disconnectClient();
		JavaLanguageServerPlugin.getInstance().setProtocol(null);
		try {
			if (projectDir != null) {
				FileUtils.deleteDirectory(projectDir.toFile());
			}
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	private void waitForJobs() {
		JobHelpers.waitForJobsToComplete();
		JobHelpers.waitForInitializeJobs();
	}

	@Test
	public void testImportNewMavenProjects() throws Exception {
		IWorkspaceRoot wsRoot = WorkspaceHelper.getWorkspaceRoot();
		IWorkspace workspace = wsRoot.getWorkspace();
		IProject[] projects = workspace.getRoot().getProjects();
		assertEquals(0, projects.length);
		File from = new File(getSourceProjectDirectory(), "maven/multimodule");
		File projectBasePath = new File(projectDir.toFile(), "multimodule");
		FileUtils.copyDirectory(from, projectBasePath);
		projects = workspace.getRoot().getProjects();
		assertEquals(0, projects.length);
		projectsManager.importProjects(new NullProgressMonitor());
		waitForJobs();
		projects = workspace.getRoot().getProjects();
		assertEquals(5, projects.length);

		// Add new sub-module
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("multimodule");
		IFile pom = project.getFile("/pom.xml");
		ResourceUtils.setContent(pom, ResourceUtils.getContent(pom).replaceAll("<module>module1</module>", "<module>module1</module>\n<module>module4</module>"));
		File subModulePath = new File(projectBasePath, "module4");
		FileUtils.forceMkdir(subModulePath);
		File buildFile = new File(subModulePath, "pom.xml");
		buildFile.createNewFile();
		BufferedWriter writer = new BufferedWriter(new FileWriter(buildFile, true));
		writer.newLine();
		//@formatter:off
		writer.write(
		"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"" + 
			"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
			"xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
			"<modelVersion>4.0.0</modelVersion>" +
			"<parent>" +
				"<groupId>foo.bar</groupId>" +
				"<artifactId>multimodule</artifactId>" +
				"<version>0.0.1-SNAPSHOT</version>" +
			"</parent>" +
			"<artifactId>module4</artifactId>" +
		"</project>");
		//@formatter:on
		writer.close();

		// Verify no projects imported
		projects = workspace.getRoot().getProjects();
		assertEquals(5, projects.length);

		// Verify import projects
		projectsManager.importProjects(new NullProgressMonitor());
		waitForJobs();
		IProject newProject = workspace.getRoot().getProject("module4");
		assertTrue(newProject.exists());
		projects = workspace.getRoot().getProjects();
		assertEquals(6, projects.length);
	}
	
	@Test
	public void testImportNewGradleProjects() throws Exception {
		IWorkspaceRoot wsRoot = WorkspaceHelper.getWorkspaceRoot();
		IWorkspace workspace = wsRoot.getWorkspace();
		IProject[] projects = workspace.getRoot().getProjects();
		assertEquals(0, projects.length);
		File from = new File(getSourceProjectDirectory(), "gradle/multi-module");
		File projectBasePath = new File(projectDir.toFile(), "multi-module");
		FileUtils.copyDirectory(from, projectBasePath);
		projects = workspace.getRoot().getProjects();
		assertEquals(0, projects.length);
		projectsManager.importProjects(new NullProgressMonitor());
		waitForJobs();
		IProject project = workspace.getRoot().getProject("multi-module");
		assertTrue(project.exists());
		projects = workspace.getRoot().getProjects();
		assertEquals(3, projects.length);
		
		// Add new sub-module
		BufferedWriter writer = new BufferedWriter(new FileWriter(new File(projectBasePath, "settings.gradle"), true));
		writer.newLine();
		writer.write("include 'test'");
		writer.close();
		File subModulePath = new File(projectBasePath, "test");
		FileUtils.forceMkdir(subModulePath);
		File buildFile = new File(subModulePath, "build.gradle");
		buildFile.createNewFile();

		// Verify no projects imported
		projects = workspace.getRoot().getProjects();
		assertEquals(3, projects.length);

		// Verify import projects
		projectsManager.importProjects(new NullProgressMonitor());
		waitForJobs();
		IProject newProject = workspace.getRoot().getProject("test");
		assertTrue(newProject.exists());
		projects = workspace.getRoot().getProjects();
		assertEquals(4, projects.length);
	}
}
