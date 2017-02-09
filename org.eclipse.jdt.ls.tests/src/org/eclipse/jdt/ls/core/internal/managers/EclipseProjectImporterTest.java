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

import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.jdt.ls.core.internal.managers.EclipseProjectImporter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class EclipseProjectImporterTest extends AbstractProjectsManagerBasedTest {

	private EclipseProjectImporter importer;

	@Before
	public void setUp() {
		importer = new EclipseProjectImporter();
	}

	@Test
	public void importSimpleJavaProject() throws Exception {
		String name = "hello";
		importProjects("eclipse/"+name);
		IProject project = getProject(name );
		assertIsJavaProject(project);
	}

	@Test
	public void importMultipleJavaProject() throws Exception {
		List<IProject> projects = importProjects("eclipse/multi");
		assertEquals(3, projects.size());

		IProject bar = getProject("bar");
		assertIsJavaProject(bar);

		IProject foo = getProject("foo");
		assertIsJavaProject(foo);
	}



	@Test
	public void testFindUniqueProject() throws Exception {
		//given
		String name = "project";
		IWorkspaceRoot root = mock(IWorkspaceRoot.class);
		IWorkspace workspace = mock(IWorkspace.class);
		when(workspace.getRoot()).thenReturn(root);
		IProject p0 = mockProject(root, "project", false);

		//when
		IProject p = importer.findUniqueProject(workspace, name);

		//then
		assertSame(p0, p);

		//given
		when(p0.exists()).thenReturn(true);
		IProject p2 = mockProject(root, "project (2)", false);

		//when
		p = importer.findUniqueProject(workspace, name);

		//then
		assertSame(p2, p);

		//given
		when(p0.exists()).thenReturn(true);
		when(p2.exists()).thenReturn(true);
		IProject p3 = mockProject(root, "project (3)", false);

		//when
		p = importer.findUniqueProject(workspace, name);

		//then
		assertSame(p3, p);
	}

	private IProject mockProject(IWorkspaceRoot root, String name, boolean exists) {
		IProject p = mock(IProject.class);
		when(p.getName()).thenReturn(name);
		when(p.exists()).thenReturn(exists);
		when(p.toString()).thenReturn(name);
		when(root.getProject(name)).thenReturn(p);
		return p;
	}

	@After
	public void after() {
		importer = null;
	}



}