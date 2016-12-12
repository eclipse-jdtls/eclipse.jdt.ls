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

import static org.jboss.tools.vscode.java.internal.ProjectUtils.getJavaSourceLevel;
import static org.jboss.tools.vscode.java.internal.ResourceUtils.getContent;
import static org.jboss.tools.vscode.java.internal.ResourceUtils.setContent;
import static org.jboss.tools.vscode.java.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.jboss.tools.vscode.java.internal.ProjectUtils;
import org.jboss.tools.vscode.java.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Fred Bricon
 */
public class MavenProjectImporterTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testImportSimpleJavaProject() throws Exception {
		importSimpleJavaProject();
	}

	protected IProject importSimpleJavaProject() throws Exception {
		String name = "salut";
		importProjects("maven/"+name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertIsMavenProject(project);
		assertEquals("1.7", getJavaSourceLevel(project));
		assertNoErrors(project);
		return project;
	}

	@Ignore(value="Test is currently unstable and fails randomly")
	@Test
	public void pomChangedTriggersBuild() throws Exception {
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

		//Giving a nudge, so that errors show up
		projectsManager.fileChanged(pomUri.toString(), CHANGE_TYPE.CHANGED);
		waitForBackgroundJobs();
		assertHasErrors(project);

		//Fix pom, trigger build
		setContent(pomUri, originalPom);
		projectsManager.fileChanged(pomUri.toString(), CHANGE_TYPE.CHANGED);
		waitForBackgroundJobs();
		assertNoErrors(project);
	}

	protected void assertIsMavenProject(IProject project) {
		assertNotNull(project);
		assertTrue(project.getName() +" is missing the Maven nature", ProjectUtils.isMavenProject(project));
	}

	private String comment(String s, String from, String to) {
		String result = s.replace(from, "<!--"+from).replace(to, to+"-->");
		return result;
	}

}