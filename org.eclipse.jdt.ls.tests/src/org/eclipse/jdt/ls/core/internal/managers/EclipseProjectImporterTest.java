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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.FileSystemWatcher;
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
	public void testPreviewFeatures16() throws Exception {
		String name = "java16";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertHasErrors(project, "The Java feature 'Sealed Types' is only available with source level 17 and above");
	}

	@Test
	public void testPreviewFeaturesDisabledByDefault() throws Exception {
		String name = "java19";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertNoErrors(project);
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
	public void avoidImportDuplicatedProjects() throws Exception {
		importProjects("multi-buildtools");
		EclipseProjectImporter importer = new EclipseProjectImporter();
		File root = new File(getWorkingProjectDirectory(), "multi-buildtools");
		importer.initialize(root);

		Collection<IPath> configurationPaths = new ArrayList<>();
		configurationPaths.add(ResourceUtils.canonicalFilePathFromURI(root.toPath().resolve("build.gradle").toUri().toString()));
		assertFalse(importer.applies(configurationPaths, null));
	}

	// https://github.com/redhat-developer/vscode-java/issues/2436
	@Test
	public void importJavaProjectWithRootSource() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isWorkspaceChangeWatchedFilesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		String name = "projectwithrootsource";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		List<FileSystemWatcher> watchers = projectsManager.registerWatchers();
		assertEquals(11, watchers.size());
		String srcGlobPattern = watchers.get(9).getGlobPattern();
		assertTrue("Unexpected source glob pattern: " + srcGlobPattern, srcGlobPattern.endsWith("projectwithrootsource/**"));
	}

	@After
	public void after() {
		importer = null;
	}



}
