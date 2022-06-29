/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.extended.ProjectBuildParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BuildWorkspaceHandlerTest extends AbstractProjectsManagerBasedTest {
	private BuildWorkspaceHandler handler;
	private IProject project;

	@Before
	public void setUp() throws Exception {
		handler = new BuildWorkspaceHandler(projectsManager);
		importProjects("maven/salut2");
		project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut2");
	}

	@Override
	@After
	public void cleanUp() throws Exception {
		super.cleanUp();
		preferences.setMaxBuildCount(1);
	}

	@Test
	public void testSucceedCase() throws Exception {
		BuildWorkspaceStatus result = handler.buildWorkspace(false, monitor);
		assertTrue(String.format("BuildWorkspaceStatus is: %s.", result.toString()), result == BuildWorkspaceStatus.SUCCEED);
	}

	@Test
	public void testFailedCase() throws Exception {
		//@formatter:off
		String codeWithError = "package foo;\n" +
				"	public class Single2 {\n" +
				"	public static void main(String[] args){\n"+
				"		int ss = 1;;\n"+
				"	}\n"+
				"}";
		//@formatter:on
		IFile file = project.getFile("src/main/java/foo/Bar.java");
		try (InputStream stream = new ByteArrayInputStream(codeWithError.getBytes(StandardCharsets.UTF_8))) {
			file.setContents(stream, true, false, monitor);
			BuildWorkspaceStatus result = handler.buildWorkspace(false, monitor);
			assertEquals(result, BuildWorkspaceStatus.WITH_ERROR);
		}
	}

	@Test
	public void testCanceledCase() throws Exception {
		monitor.setCanceled(true);
		BuildWorkspaceStatus result = handler.buildWorkspace(false, monitor);
		assertEquals(result, BuildWorkspaceStatus.CANCELLED);
	}

	@Test
	public void testParallelBuildForEclipseProjects() throws Exception
	{
		preferences.setMaxBuildCount(4);

		List<IProject> projects = importProjects("eclipse/multi");
		assertEquals(3, projects.size());

		BuildWorkspaceStatus result = handler.buildWorkspace(false, monitor);
		assertEquals(String.format("BuildWorkspaceStatus is: %s.", result.toString()), result, BuildWorkspaceStatus.SUCCEED);
	}

	@Test
	public void testParallelBuildSupport() throws Exception {
		preferences.setMaxBuildCount(4);

		List<IProject> projects = importProjects("maven/multimodule");
		assertEquals(6, projects.size());

		BuildWorkspaceStatus result = handler.buildWorkspace(false, monitor);
		assertEquals(String.format("BuildWorkspaceStatus is: %s.", result.toString()), result, BuildWorkspaceStatus.SUCCEED);
	}

	@Test
	public void testBuildProjects() throws Exception {
		List<IProject> projects = importProjects("maven/multimodule");
		List<TextDocumentIdentifier> identifiers = projects.stream().map(p -> {
			return new TextDocumentIdentifier(p.getLocationURI().toString());
		}).collect(Collectors.toList());
		ProjectBuildParams params = new ProjectBuildParams(identifiers, true);
		BuildWorkspaceStatus result = handler.buildProjects(params, monitor);
		assertEquals(String.format("BuildWorkspaceStatus is: %s.", result.toString()), result, BuildWorkspaceStatus.SUCCEED);
	}
}
