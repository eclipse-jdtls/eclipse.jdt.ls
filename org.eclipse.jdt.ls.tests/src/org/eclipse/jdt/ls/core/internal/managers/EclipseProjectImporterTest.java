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

import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class EclipseProjectImporterTest extends AbstractProjectsManagerBasedTest {

	private static final String BAR_PATTERN = "**/bar";
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
		// a test for https://github.com/redhat-developer/vscode-java/issues/244
		importProjects("eclipse/" + name);
		project = getProject(name);
		assertIsJavaProject(project);
	}

	@Test
	public void ignoreMissingResourceFilters() throws Exception {
		JavaClientConnection javaClient = new JavaClientConnection(client);
		try {
			String name = "ignored-filter";
			importProjects("eclipse/" + name);
			IProject project = getProject(name);
			assertIsJavaProject(project);
			assertNoErrors(project);
			//1 message sent to the platform logger
			assertEquals(logListener.getErrors().toString(), 1, logListener.getErrors().size());
			String error = logListener.getErrors().get(0);
			assertTrue("Unexpected error: " + error, error.startsWith("Missing resource filter type: 'org.eclipse.ui.ide.missingFilter'"));
			//but no message sent to the client
			List<Object> loggedMessages = clientRequests.get("logMessage");
			assertNull("Unexpected logs " + loggedMessages, loggedMessages);
		} finally {
			javaClient.disconnect();
		}
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
	public void testUseReleaseFlagByDefault() throws Exception {
		String name = "java7";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		Map<String, String> options = ProjectUtils.getJavaOptions(project);
		assertEquals(JavaCore.ENABLED, options.get(JavaCore.COMPILER_RELEASE));

		//unfortunately, the fake jdk10 we use doesn't cause the compiler to cause a compilation error
		//when using a real jdk, the error is generated as expected.
		//assertHasErrors(project);
	}

	@Test
	public void testJavaImportExclusions() throws Exception {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		try {
			javaImportExclusions.add(BAR_PATTERN);
			List<IProject> projects = importProjects("eclipse/multi");
			assertEquals(2, projects.size());
			IProject bar = getProject("bar");
			assertNull(bar);
			IProject foo = getProject("foo");
			assertIsJavaProject(foo);
		} finally {
			javaImportExclusions.remove(BAR_PATTERN);
		}

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

	@Test
	public void testPreviewFeaturesDisabledByDefault() throws Exception {
		String name = "java15";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertHasErrors(project, "is a preview feature and disabled by default");
	}

	@Test
	public void testPreviewFeaturesNotAvailable() throws Exception {
		String name = "java12";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertHasErrors(project, "Switch Expressions are supported from", "Arrow in case statement supported from");
	}

	@Test
	public void testClasspath() throws Exception {
		String name = "classpath";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertNull(project);
	}

	@Test
	public void testDeleteClasspath() throws Exception {
		String name = "classpath2";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertNotNull(project);
		IFile dotClasspath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		File file = dotClasspath.getRawLocation().toFile();
		assertTrue(file.exists());
		file.delete();
		projectsManager.fileChanged(file.toPath().toUri().toString(), CHANGE_TYPE.DELETED);
		waitForBackgroundJobs();
		project = getProject(name);
		assertFalse(ProjectUtils.isJavaProject(project));
		IFile bin = project.getFile("bin");
		assertFalse(bin.getRawLocation().toFile().exists());
	}

	@After
	public void after() {
		importer = null;
	}



}
