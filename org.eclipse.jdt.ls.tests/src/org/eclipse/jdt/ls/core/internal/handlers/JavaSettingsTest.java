/*******************************************************************************
 * Copyright (c) 2017-2021 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.Hashtable;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerTestPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.managers.StandardProjectsManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.Bundle;

/**
 * @author snjeza
 */
@RunWith(MockitoJUnitRunner.class)
public class JavaSettingsTest extends AbstractCompilationUnitBasedTest {

	private static final String MISSING_SERIAL_VERSION = JavaCore.COMPILER_PB_MISSING_SERIAL_VERSION;
	private static final String STATIC_ACCESS_RECEIVER = JavaCore.COMPILER_PB_STATIC_ACCESS_RECEIVER;
	private IJavaProject javaProject;
	private Hashtable<String, String> options;

	@Before
	public void setUp() throws Exception {
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
		options = JavaCore.getOptions();
		importProjects("eclipse/hello");
		IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject("hello");
		javaProject = JavaCore.create(p);
	}

	@Override
	@After
	public void cleanUp() throws Exception {
		super.cleanUp();
		JavaCore.setOptions(options);
		JavaLanguageServerPlugin.getInstance().setProtocol(null);
		JobHelpers.waitForJobsToComplete();
	}

	@Test
	public void testFilePath() throws Exception {
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
		assertEquals("ignore", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		testMarkers(0);
		try {
			Bundle bundle = Platform.getBundle(JavaLanguageServerTestPlugin.PLUGIN_ID);
			URL settingsUrl = bundle.getEntry("/formatter/settings.prefs");
			URL url = FileLocator.resolve(settingsUrl);
			File file = ResourceUtils.toFile(URIUtil.toURI(url));
			assertTrue(file.exists());
			preferences.setSettingsUrl(file.getAbsolutePath());
			StandardProjectsManager.configureSettings(preferences);
			assertTrue(preferences.getSettingsAsURI().isAbsolute());
			JobHelpers.waitForJobsToComplete();
			assertEquals("warning", JavaCore.getOption(MISSING_SERIAL_VERSION));
			assertEquals("warning", javaProject.getOption(MISSING_SERIAL_VERSION, true));
			testMarkers(1);
		} finally {
			JavaCore.setOptions(options);
			preferences.setSettingsUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
		JobHelpers.waitForJobsToComplete();
		testMarkers(0);
	}

	private void testMarkers(int count) throws JavaModelException, CoreException {
		IType type = javaProject.findType("org.sample.TestSerial");
		IMarker[] markers = type.getResource().findMarkers(null, true, IResource.DEPTH_ZERO);
		assertEquals(count, markers.length);
	}

	@Test
	public void testRelativeFilePath() throws Exception {
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
		assertEquals("ignore", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		try {
			String settingsUrl = "../../formatter/settings.prefs";
			preferences.setSettingsUrl(settingsUrl);
			StandardProjectsManager.configureSettings(preferences);
			assertTrue(preferences.getSettingsAsURI().isAbsolute());
			assertEquals("warning", JavaCore.getOption(MISSING_SERIAL_VERSION));
			assertEquals("warning", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		} finally {
			JavaCore.setOptions(options);
			preferences.setSettingsUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
	}

	@Test
	public void testFileChanged() throws Exception {
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
		assertEquals("ignore", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		try {
			String settingsUrl = "../../formatter/settings.prefs";
			preferences.setSettingsUrl(settingsUrl);
			projectsManager.fileChanged(preferences.getSettingsAsURI().toURL().toString(), CHANGE_TYPE.CHANGED);
			waitForBackgroundJobs();
			assertEquals("warning", JavaCore.getOption(MISSING_SERIAL_VERSION));
			assertEquals("warning", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		} finally {
			JavaCore.setOptions(options);
			preferences.setSettingsUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
	}

	// https://github.com/redhat-developer/vscode-java/issues/1944
	@Test
	public void testFileChangedOnWindows() throws Exception {
		if (Platform.OS_WIN32.equals(Platform.getOS())) {
			assertEquals(DefaultCodeFormatterConstants.END_OF_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
			try {
				String formatterUrl = "..\\\\..\\\\formatter\\\\test.xml";
				preferences.setFormatterUrl(formatterUrl);
				projectsManager.fileChanged(preferences.getFormatterAsURI().toURL().toString(), CHANGE_TYPE.CHANGED);
				waitForBackgroundJobs();
				assertEquals(DefaultCodeFormatterConstants.NEXT_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
			} finally {
				JavaCore.setOptions(options);
				preferences.setSettingsUrl(null);
				preferences.setFormatterUrl(null);
				StandardProjectsManager.configureSettings(preferences);
			}
			assertEquals(DefaultCodeFormatterConstants.END_OF_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
		}
	}

	@Test
	public void testFormatter() throws Exception {
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
		assertEquals("ignore", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		assertEquals(DefaultCodeFormatterConstants.END_OF_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
		try {
			String settingsUrl = "../../formatter/settings.prefs";
			preferences.setSettingsUrl(settingsUrl);
			String formatterUrl = "../../formatter/test.xml";
			preferences.setFormatterUrl(formatterUrl);
			projectsManager.fileChanged(preferences.getFormatterAsURI().toURL().toString(), CHANGE_TYPE.CHANGED);
			waitForBackgroundJobs();
			assertEquals("warning", JavaCore.getOption(MISSING_SERIAL_VERSION));
			assertEquals("warning", javaProject.getOption(MISSING_SERIAL_VERSION, true));
			assertEquals(DefaultCodeFormatterConstants.NEXT_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
		} finally {
			JavaCore.setOptions(options);
			preferences.setSettingsUrl(null);
			preferences.setFormatterUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
		assertEquals(DefaultCodeFormatterConstants.END_OF_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
	}

	// https://github.com/redhat-developer/vscode-java/issues/1939
	@Test
	public void testSettingsV3() throws Exception {
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
		assertEquals("warning", JavaCore.getOption(STATIC_ACCESS_RECEIVER));
		assertEquals("ignore", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		assertEquals("warning", javaProject.getOption(STATIC_ACCESS_RECEIVER, true));
		try {
			String settingsUrl = "../../formatter/settings2.prefs";
			preferences.setSettingsUrl(settingsUrl);
			projectsManager.fileChanged(preferences.getSettingsAsURI().toURL().toString(), CHANGE_TYPE.CHANGED);
			waitForBackgroundJobs();
			assertEquals("warning", JavaCore.getOption(MISSING_SERIAL_VERSION));
			assertEquals("ignore", JavaCore.getOption(STATIC_ACCESS_RECEIVER));
			assertEquals("warning", javaProject.getOption(MISSING_SERIAL_VERSION, true));
			assertEquals("ignore", javaProject.getOption(STATIC_ACCESS_RECEIVER, true));
		} finally {
			JavaCore.setOptions(options);
			preferences.setSettingsUrl(null);
			StandardProjectsManager.configureSettings(preferences);
			waitForBackgroundJobs();
		}
		assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
		assertEquals("warning", JavaCore.getOption(STATIC_ACCESS_RECEIVER));
		assertEquals("ignore", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		assertEquals("warning", javaProject.getOption(STATIC_ACCESS_RECEIVER, true));
	}

}
