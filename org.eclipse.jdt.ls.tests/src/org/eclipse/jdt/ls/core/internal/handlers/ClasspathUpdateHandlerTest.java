/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.ls.core.internal.DependencyUtil;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.InvisibleProjectBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClasspathUpdateHandlerTest extends AbstractInvisibleProjectBasedTest {
	@Mock
	private JavaClientConnection connection;

	private ClasspathUpdateHandler handler;

	@BeforeClass
	public static void download() throws FileNotFoundException, CoreException {
		File commonsLang3Archive = DependencyUtil.getSources("org.apache.commons", "commons-lang3", "3.6");
		assertNotNull("commons-lang-3.6-sources.jar not found", commonsLang3Archive);
		commonsLang3Archive = DependencyUtil.getSources("org.apache.commons", "commons-lang3", "3.5");
		assertNotNull("commons-lang-3.5-sources.jar not found", commonsLang3Archive);
	}

	@Before
	public void setup() throws Exception {
		handler = new ClasspathUpdateHandler(connection);
		handler.addElementChangeListener();
		preferences.setUpdateBuildConfigurationStatus(FeatureStatus.automatic);
	}

	@After
	@Override
	public void cleanUp() throws Exception {
		super.cleanUp();
		handler.removeElementChangeListener();
	}

	@Test
	public void testClasspathUpdateForMaven() throws Exception {
		importProjects("maven/salut");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut");
		IFile pom = project.getFile("/pom.xml");
		assertTrue(pom.exists());
		ResourceUtils.setContent(pom, ResourceUtils.getContent(pom).replaceAll("<version>3.5</version>", "<version>3.6</version>"));

		projectsManager.fileChanged(pom.getLocationURI().toString(), CHANGE_TYPE.CHANGED);
		waitForBackgroundJobs();

		ArgumentCaptor<EventNotification> argument = ArgumentCaptor.forClass(EventNotification.class);
		verify(connection, times(1)).sendEventNotification(argument.capture());
		assertEquals(EventType.ClasspathUpdated, argument.getValue().getType());
		// Use Paths.get() to normalize the URI: ignore the tailing slash, "/project/path" and "/project/path/" should be the same.
		// Paths.get(URI) doesn't work on Windows
		assertEquals(Paths.get(project.getLocation().toOSString()), Paths.get(getData(argument)));
	}

	private String getData(ArgumentCaptor<EventNotification> argument) {
		return new org.eclipse.core.runtime.Path(URI.create((String) argument.getValue().getData()).getPath()).toOSString();
	}

	@Test
	public void testClasspathUpdateForGradle() throws Exception {
		importProjects("gradle/simple-gradle");
		JobHelpers.waitForJobs(CorePlugin.GRADLE_JOB_FAMILY, monitor);
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("simple-gradle");
		IFile buildGradle = project.getFile("/build.gradle");
		assertTrue(buildGradle.exists());
		ResourceUtils.setContent(buildGradle, ResourceUtils.getContent(buildGradle).replaceAll("org.slf4j:slf4j-api:1.7.21", "org.slf4j:slf4j-api:1.7.20"));

		projectsManager.fileChanged(buildGradle.getLocationURI().toString(), CHANGE_TYPE.CHANGED);
		waitForBackgroundJobs();
		JobHelpers.waitForJobs(CorePlugin.GRADLE_JOB_FAMILY, monitor);

		ArgumentCaptor<EventNotification> argument = ArgumentCaptor.forClass(EventNotification.class);
		verify(connection, times(1)).sendEventNotification(argument.capture());
		assertEquals(EventType.ClasspathUpdated, argument.getValue().getType());
		assertEquals(Paths.get(project.getLocation().toOSString()), Paths.get(getData(argument)));
	}

	@Test
	public void testClasspathUpdateForEclipse() throws Exception {
		importProjects("eclipse/updatejar");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("updatejar");
		IFile classpath = project.getFile("/.classpath");
		assertTrue(classpath.exists());
		ResourceUtils.setContent(classpath, ResourceUtils.getContent(classpath).replaceAll("<classpathentry kind=\"lib\" path=\"lib/foo.jar\"/>", ""));

		projectsManager.fileChanged(classpath.getLocationURI().toString(), CHANGE_TYPE.CHANGED);
		waitForBackgroundJobs();

		ArgumentCaptor<EventNotification> argument = ArgumentCaptor.forClass(EventNotification.class);
		verify(connection, times(1)).sendEventNotification(argument.capture());
		assertEquals(EventType.ClasspathUpdated, argument.getValue().getType());
		assertEquals(Paths.get(project.getLocation().toOSString()), Paths.get(getData(argument)));
	}

	@Test
	public void testClasspathUpdateForInvisble() throws Exception {
		File projectFolder = createSourceFolderWithMissingLibs("dynamicLibDetection");
		importRootFolder(projectFolder, "Test.java");

		//Add jars to fix compilation errors
		addLibs(projectFolder.toPath());
		Path libPath = projectFolder.toPath().resolve(InvisibleProjectBuildSupport.LIB_FOLDER);

		Path jar = libPath.resolve("foo.jar");
		projectsManager.fileChanged(jar.toUri().toString(), CHANGE_TYPE.CREATED);
		waitForBackgroundJobs();

		ArgumentCaptor<EventNotification> argument = ArgumentCaptor.forClass(EventNotification.class);
		verify(connection, times(1)).sendEventNotification(argument.capture());
		assertEquals(EventType.ClasspathUpdated, argument.getValue().getType());
		assertEquals(Paths.get(projectFolder.getAbsolutePath()), Paths.get(getData(argument)));
		// assertEquals(projectFolder.toURI(), URI.create((String) argument.getValue().getData()));
	}
}