/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.TestVMType;
import org.eclipse.jdt.ls.core.internal.filesystem.JLSFsUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvisibleProjectMetadataFileTest extends AbstractProjectsManagerBasedTest {
	@Parameter
	public String fsMode;

	@Parameters
	public static Collection<String> data(){
		return Arrays.asList("false", "true");
	}

	@Before
	public void setUp() {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, fsMode);
	}

	@Test
	public void testMetadataFileLocation() throws Exception {
		IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
		assertTrue(project.exists());

		IFile projectDescription = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath projectDescriptionPath = FileUtil.toPath(projectDescription.getLocationURI());
		assertTrue(projectDescriptionPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(projectDescriptionPath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());

		IFile classpath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath classpathPath = FileUtil.toPath(classpath.getLocationURI());
		assertTrue(classpathPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(classpathPath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());

		IFile preferencesFile = project.getFile(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
		// workaround to get the correct path, see: https://github.com/eclipse/eclipse.jdt.ls/pull/1900
		IPath preferencesPath = FileUtil.toPath(preferencesFile.getLocationURI());
		assertTrue(preferencesPath.toFile().exists());
		assertEquals(project.getLocation().isPrefixOf(preferencesPath), JLSFsUtils.generatesMetadataFilesAtProjectRoot());
	}

	@Test
	public void testProjectSettings() throws Exception {
		IProject invisibleProject = copyAndImportFolder("singlefile/lesson1", "src/org/samples/HelloWorld.java");
		assertTrue(invisibleProject.exists());
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		String option = javaProject.getOption(JavaCore.COMPILER_PB_MISSING_SERIAL_VERSION, true);
		assertEquals(JavaCore.IGNORE, option);
		assertNoErrors(invisibleProject);
	}

	// https://github.com/eclipse/eclipse.jdt.ls/pull/1863#issuecomment-924395431
	@Test
	public void testPreviewFeaturesSettingsDisabled() throws Exception {
		String defaultJVM = JavaRuntime.getDefaultVMInstall().getId();
		try {
			TestVMType.setTestJREAsDefault("17");
			IProject invisibleProject = copyAndImportFolder("singlefile/java17a", "foo/bar/Foo.java");
			assertTrue(invisibleProject.exists());
			assertNoErrors(invisibleProject);
			IJavaProject javaProject = JavaCore.create(invisibleProject);
			assertEquals(JavaCore.DISABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		} finally {
			TestVMType.setTestJREAsDefault(defaultJVM);
		}
	}

	// https://github.com/eclipse/eclipse.jdt.ls/pull/1863#issuecomment-924395431
	@Test
	public void testPreviewFeaturesSettingEnabled() throws Exception {
		String defaultJVM = JavaRuntime.getDefaultVMInstall().getId();
		try {
			TestVMType.setTestJREAsDefault("17");
			IProject invisibleProject = copyAndImportFolder("singlefile/java17b", "foo/bar/Foo.java");
			assertTrue(invisibleProject.exists());
			assertNoErrors(invisibleProject);
			IJavaProject javaProject = JavaCore.create(invisibleProject);
			assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		} finally {
			TestVMType.setTestJREAsDefault(defaultJVM);
		}
	}
}
