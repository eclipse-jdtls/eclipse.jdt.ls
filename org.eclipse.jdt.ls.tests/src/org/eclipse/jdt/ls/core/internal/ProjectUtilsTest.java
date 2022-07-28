/*******************************************************************************
 * Copyright (c) 2020-2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.junit.Test;

public class ProjectUtilsTest extends AbstractProjectsManagerBasedTest {
	@Test
	public void testGetRealFolderForDefaultProject() throws OperationCanceledException, CoreException {
		IProject project = ProjectsManager.createJavaProject(ProjectsManager.getDefaultProject(), new NullProgressMonitor());
		IPath path = ProjectUtils.getProjectRealFolder(project);
		assertEquals(project.getLocation(), path);
	}


	@Test
	public void testGetMaxProjectProblemSeverity() throws Exception {
		importProjects("gradle/invalid");
		assertEquals(IMarker.SEVERITY_ERROR, ProjectUtils.getMaxProjectProblemSeverity());

		IProject project = WorkspaceHelper.getProject("invalid");
		IFile config = project.getFile("build.gradle");
		String content = ResourceUtils.getContent(config);
		content = content.replaceAll("// id 'java'", "id 'java'");
		ResourceUtils.setContent(config, content);
		projectsManager.updateProject(project, false);
		waitForBackgroundJobs();

		assertEquals(IMarker.SEVERITY_INFO, ProjectUtils.getMaxProjectProblemSeverity());
	}

	@Test
	public void testGetProjectSeverityWhenConfigFileNotExists() throws Exception {
		importProjects("gradle/invalid");
		IProject project = WorkspaceHelper.getProject("invalid");
		IFile config = project.getFile("build.gradle");
		config.delete(true, null);

		ProjectUtils.getMaxProjectProblemSeverity();
	}

	@Test
	public void testGetMaxProjectProblemSeverityForSubProject() throws Exception {
		importProjects("gradle/invalid-subproject");
		assertEquals(IMarker.SEVERITY_ERROR, ProjectUtils.getMaxProjectProblemSeverity());
	}


	@Test
	public void testIsUnmanagedFolder1() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		assertEquals(false, ProjectUtils.isUnmanagedFolder(project));
	}

	@Test
	public void testIsUnmanagedFolder2() throws Exception {
		IProject unmanagedFolder = copyAndImportFolder("singlefile/lesson1", "src/org/samples/HelloWorld.java");
		assertEquals(true, ProjectUtils.isUnmanagedFolder(unmanagedFolder));
	}

}
