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

import java.net.URI;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
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
		assertEquals("1.7", ProjectUtils.getJavaSourceLevel(project));
	}

}
