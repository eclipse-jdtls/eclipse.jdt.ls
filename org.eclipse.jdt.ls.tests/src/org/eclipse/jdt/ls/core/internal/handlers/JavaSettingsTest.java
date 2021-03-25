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
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerTestPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
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

	private static final String MISSING_SERIAL_VERSION = "org.eclipse.jdt.core.compiler.problem.missingSerialVersion";
	private IJavaProject javaProject;
	private Hashtable<String, String> options;

	@Before
	public void setUp() throws Exception {
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
		JobHelpers.waitForJobsToComplete();
	}

	@Test
	public void testFilePath() throws Exception {
		assertEquals("warning", JavaCore.getOption(MISSING_SERIAL_VERSION));
		assertEquals("warning", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		testMarkers(1);
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
			assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
			assertEquals("ignore", javaProject.getOption(MISSING_SERIAL_VERSION, true));
			testMarkers(0);
		} finally {
			JavaCore.setOptions(options);
			preferences.setSettingsUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
		assertEquals("warning", JavaCore.getOption(MISSING_SERIAL_VERSION));
		JobHelpers.waitForJobsToComplete();
		testMarkers(1);
	}

	private void testMarkers(int count) throws JavaModelException, CoreException {
		IType type = javaProject.findType("org.sample.TestSerial");
		IMarker[] markers = type.getResource().findMarkers(null, true, IResource.DEPTH_ZERO);
		assertEquals(count, markers.length);
	}

	@Test
	public void testRelativeFilePath() throws Exception {
		assertEquals("warning", JavaCore.getOption(MISSING_SERIAL_VERSION));
		assertEquals("warning", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		try {
			String settingsUrl = "../../formatter/settings.prefs";
			preferences.setSettingsUrl(settingsUrl);
			StandardProjectsManager.configureSettings(preferences);
			assertTrue(preferences.getSettingsAsURI().isAbsolute());
			assertEquals("ignore", JavaCore.getOption(MISSING_SERIAL_VERSION));
			assertEquals("ignore", javaProject.getOption(MISSING_SERIAL_VERSION, true));
		} finally {
			JavaCore.setOptions(options);
			preferences.setSettingsUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
		assertEquals("warning", JavaCore.getOption(MISSING_SERIAL_VERSION));
	}

}
