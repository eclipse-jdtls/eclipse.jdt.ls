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
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.MessageParams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * InvisibleProjectPreferenceChangeListenerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class InvisibleProjectPreferenceChangeListenerTest extends AbstractInvisibleProjectBasedTest {

	@Test
	public void testUpdateOutputPath() throws Exception {
		preferenceManager.getPreferences().setInvisibleProjectOutputPath("");
		IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(String.join("/", "", javaProject.getElementName(), "bin"), javaProject.getOutputLocation().toString());

		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectOutputPath("bin");
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);
		waitForBackgroundJobs();

		assertEquals(String.join("/", "", javaProject.getElementName(), ProjectUtils.WORKSPACE_LINK, "bin"), javaProject.getOutputLocation().toString());
	}

	@Test
	public void testUpdateOutputPathToUnEmptyFolder() throws Exception {
		copyAndImportFolder("singlefile/simple", "src/App.java");
		waitForBackgroundJobs();

		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectOutputPath("lib");

		JavaLanguageClient client = mock(JavaLanguageClient.class);
		ProjectsManager pm = JavaLanguageServerPlugin.getProjectsManager();
		pm.setConnection(client);
		doNothing().when(client).showMessage(any(MessageParams.class));
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);
		waitForBackgroundJobs();

		verify(client, times(1)).showMessage(any(MessageParams.class));
	}

	@Test
	public void testUpdateSourcePaths() throws Exception {
		preferenceManager.getPreferences().setInvisibleProjectOutputPath("");
		IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
		IJavaProject javaProject = JavaCore.create(project);
		IFolder linkFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK);
		
		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(1, sourcePaths.size());
		assertTrue(sourcePaths.contains("src"));

		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectSourcePaths(Arrays.asList("src", "test"));
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);

		waitForBackgroundJobs();

		sourcePaths.clear();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}

		assertEquals(2, sourcePaths.size());
		assertTrue(sourcePaths.contains("src"));
		assertTrue(sourcePaths.contains("test"));
	}

	@Test
	public void testWhenRootPathChanged() throws Exception {
		JavaLanguageClient client = mock(JavaLanguageClient.class);
		ProjectsManager pm = JavaLanguageServerPlugin.getProjectsManager();
		pm.setConnection(client);
		doNothing().when(client).showMessage(any(MessageParams.class));
		copyAndImportFolder("singlefile/simple", "src/App.java");

		List<IPath> rootPaths = new ArrayList<>(preferenceManager.getPreferences().getRootPaths());
		rootPaths.remove(0);
		preferenceManager.getPreferences().setRootPaths(rootPaths);

		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectSourcePaths(Arrays.asList("src", "src2"));
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);
		
		verify(client, times(0)).showMessage(any(MessageParams.class));
	}
}
