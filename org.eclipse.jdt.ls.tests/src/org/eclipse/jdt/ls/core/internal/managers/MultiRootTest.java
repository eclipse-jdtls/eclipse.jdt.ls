/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.junit.Test;


public class MultiRootTest extends AbstractProjectsManagerBasedTest {

	private static String EclipseFolder = "eclipse/hello";
	private static String MavenFolder = "maven/salut";
	private static String MavenMultiFolder = "maven/multi";
	private static String GradleFolder = "gradle/simple-gradle";

	@Test
	public void testInitializeWithMultiFolders() throws Exception {
		{
			Collection<String> folders = Arrays.asList(EclipseFolder, MavenFolder);
			importProjects(folders);
			assertEquals(3, WorkspaceHelper.getAllProjects().size()); // includes the default project
			assertNotNull(WorkspaceHelper.getProject("hello"));
			assertNotNull(WorkspaceHelper.getProject("salut"));
		}
		// simulate a new start with a different set of projects
		{
			Collection<String> folders = Arrays.asList(MavenMultiFolder, EclipseFolder);
			importProjects(folders);

			assertEquals(4, WorkspaceHelper.getAllProjects().size()); // includes the default project
			assertNotNull(WorkspaceHelper.getProject("hello"));
			assertNull(WorkspaceHelper.getProject("salut"));
			assertNotNull(WorkspaceHelper.getProject("project1"));
			assertNotNull(WorkspaceHelper.getProject("project2"));
		}
	}

	@Test
	public void testUpdateMultiFolders() throws Exception {
		{
			Collection<String> folders = Arrays.asList(EclipseFolder, MavenFolder);
			importProjects(folders);
			assertEquals(3, WorkspaceHelper.getAllProjects().size()); // includes the default project
			assertNotNull(WorkspaceHelper.getProject("hello"));
			assertNotNull(WorkspaceHelper.getProject("salut"));
		}
		{
			// add a folder that contains 2 projects
			Collection<String> toAdd = Arrays.asList(MavenMultiFolder);
			Collection<String> toRemove = Arrays.asList(MavenFolder);
			updateProjects(toAdd, toRemove);

			assertEquals(4, WorkspaceHelper.getAllProjects().size()); // includes the default project
			assertNotNull(WorkspaceHelper.getProject("hello"));
			assertNull(WorkspaceHelper.getProject("salut"));
			assertNotNull(WorkspaceHelper.getProject("project1"));
			assertNotNull(WorkspaceHelper.getProject("project2"));
		}
		{
			// add a folder that existed before
			Collection<String> toAdd = Arrays.asList(MavenFolder);
			// remove a folder that contains 2 projects
			Collection<String> toRemove = Arrays.asList(MavenMultiFolder);
			updateProjects(toAdd, toRemove);

			assertEquals(3, WorkspaceHelper.getAllProjects().size()); // includes the default project
			assertNotNull(WorkspaceHelper.getProject("hello"));
			assertNotNull(WorkspaceHelper.getProject("salut"));
			assertNull(WorkspaceHelper.getProject("project1"));
			assertNull(WorkspaceHelper.getProject("project2"));
		}
		{
			// add a gradle folder
			Collection<String> toAdd = Arrays.asList(GradleFolder);
			// remove a folder that contains 2 projects
			Collection<String> toRemove = Arrays.asList(EclipseFolder, MavenFolder);
			updateProjects(toAdd, toRemove);

			assertEquals(2, WorkspaceHelper.getAllProjects().size()); // includes the default project
			assertNull(WorkspaceHelper.getProject("hello"));
			assertNull(WorkspaceHelper.getProject("salut"));
			assertNotNull(WorkspaceHelper.getProject("simple-gradle"));
		}
	}

	private void updateProjects(final Collection<String> added, final Collection<String> removed) throws Exception {
		final ArrayList<IPath> addedRootPaths = new ArrayList<>();
		for (String a : added) {
			File file = copyFiles(a, false);
			addedRootPaths.add(Path.fromOSString(file.getAbsolutePath()));
		}
		final ArrayList<IPath> removedRootPaths = new ArrayList<>();
		for (String r : removed) {
			File file = new File(getWorkingProjectDirectory(), r);
			removedRootPaths.add(Path.fromOSString(file.getAbsolutePath()));
		}
		Job job = projectsManager.updateWorkspaceFolders(addedRootPaths, removedRootPaths);
		job.join();
		waitForBackgroundJobs();

	}

}
