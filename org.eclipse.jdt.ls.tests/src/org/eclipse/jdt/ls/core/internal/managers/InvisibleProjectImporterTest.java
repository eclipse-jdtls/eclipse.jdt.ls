/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.junit.Test;

public class InvisibleProjectImporterTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void importIncompleteFolder() throws Exception {
		IProject invisibleProject = importRootFolder("maven/salut/src/main/java/org/sample", "Bar.java");
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void importCompleteFolder() throws Exception {
		IProject invisibleProject = importRootFolder("singlefile/lesson1", "src/org/samples/HelloWorld.java");
		assertTrue(invisibleProject.exists());
		IPath sourcePath = invisibleProject.getFolder(new Path(ProjectUtils.WORKSPACE_LINK).append("src")).getFullPath();
		assertTrue(ProjectUtils.isOnSourcePath(sourcePath, JavaCore.create(invisibleProject)));
	}

	@Test
	public void importCompleteFolderWithoutTriggerFile() throws Exception {
		IProject invisibleProject = importRootFolder("singlefile/lesson1", null);
		assertFalse(invisibleProject.exists());
	}

	private IProject importRootFolder(String folder, String triggerFile) throws Exception {
		File file = copyFiles(folder, true);
		IPath rootPath = Path.fromOSString(file.getAbsolutePath());
		if (StringUtils.isNotBlank(triggerFile)) {
			IPath triggerFilePath = rootPath.append(triggerFile);
			Preferences preferences = preferenceManager.getPreferences();
			preferences.setTriggerFiles(Arrays.asList(triggerFilePath));
		}
		final List<IPath> roots = Arrays.asList(rootPath);
		IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
			@Override
			public void run(IProgressMonitor monitor) throws CoreException {
				projectsManager.initializeProjects(roots, monitor);
			}
		};
		JavaCore.run(runnable, null, monitor);
		waitForBackgroundJobs();
		String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(rootPath);
		return ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);
	}
}
