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

import static org.eclipse.jdt.ls.core.internal.ResourceUtils.getContent;
import static org.eclipse.jdt.ls.core.internal.ResourceUtils.setContent;

import java.net.URI;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class MavenBuildSupportTest extends AbstractMavenBasedTest {

	@Test
	public void testUpdate() throws Exception {
		IProject project = importSimpleJavaProject();

		IFile pom = project.getFile("pom.xml");
		URI pomUri = pom.getRawLocationURI();

		//Remove dependencies to cause compilation errors
		String originalPom = getContent(pomUri);
		String dependencyLessPom = comment(originalPom, "<dependencies>", "</dependencies>");
		setContent(pomUri, dependencyLessPom);
		waitForBackgroundJobs();
		//Contents changed outside the workspace, so should not change
		assertNoErrors(project);

		projectsManager.updateProject(project);

		//Giving a nudge, so that errors show up
		waitForBackgroundJobs();
		assertHasErrors(project);

		//Fix pom, trigger build
		setContent(pomUri, originalPom);
		projectsManager.updateProject(project);
		waitForBackgroundJobs();
		assertNoErrors(project);
	}

}
