/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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

import static org.eclipse.jdt.ls.core.internal.ResourceUtils.getContent;
import static org.eclipse.jdt.ls.core.internal.ResourceUtils.setContent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class GradleBuildSupportTest extends AbstractGradleBasedTest {

	@Test
	public void testUpdate() throws Exception {
		IProject project = importSimpleJavaProject();

		IFile gradle = project.getFile("build.gradle");
		URI gradleUri = gradle.getRawLocationURI();

		String originalGradle = getContent(gradleUri);

		URI newGradleUri = project.getFile("build2.gradle").getRawLocationURI();

		//Remove dependencies to cause compilation errors
		String newGradle = getContent(newGradleUri);
		setContent(gradleUri, newGradle);
		waitForBackgroundJobs();
		//Contents changed outside the workspace, so should not change
		assertNoErrors(project);

		//Giving a nudge, so that errors show up
		projectsManager.updateProject(project, false);

		waitForBackgroundJobs();
		assertHasErrors(project);
		assertEquals("1.8", ProjectUtils.getJavaSourceLevel(project));

		//Fix gradle file, trigger build
		setContent(gradleUri, originalGradle);
		projectsManager.updateProject(project, false);
		waitForBackgroundJobs();
		assertNoErrors(project);
		assertEquals("1.8", ProjectUtils.getJavaSourceLevel(project));
	}

	// https://github.com/redhat-developer/vscode-java/issues/3893
	@Test
	public void testUpdateModule() throws Exception {
		FeatureStatus oldSettings = preferenceManager.getPreferences().getUpdateBuildConfigurationStatus();
		try {
			preferenceManager.getPreferences().setUpdateBuildConfigurationStatus(FeatureStatus.disabled);
			List<IProject> projects = importProjects("gradle/sample");
			assertEquals(2, projects.size()); // app, sample
			IProject root = WorkspaceHelper.getProject("sample");
			assertIsGradleProject(root);
			IProject project = WorkspaceHelper.getProject("app");
			assertIsGradleProject(project);
			assertIsJavaProject(project);
			IJavaProject javaProject = JavaCore.create(project);
			IType type = javaProject.findType("org.apache.commons.lang3.StringUtils");
			assertNull(type);
			IFile build2 = project.getFile("/build.gradle2");
			InputStream contents = build2.getContents();
			IFile build = project.getFile("/build.gradle");
			build.setContents(contents, true, false, null);
			projectsManager.fileChanged(build.getRawLocation().toPath().toUri().toString(), CHANGE_TYPE.CHANGED);
			waitForBackgroundJobs();
			type = javaProject.findType("org.apache.commons.lang3.StringUtils");
			assertNull(type);
			projectsManager.updateProject(project, true);
			waitForBackgroundJobs();
			type = javaProject.findType("org.apache.commons.lang3.StringUtils");
			assertNotNull(type);
		} finally {
			preferenceManager.getPreferences().setUpdateBuildConfigurationStatus(oldSettings);
		}
	}

}
