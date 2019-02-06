/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
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

/**
 * Base class for Invisible project tests.
 *
 * @author Fred Bricon
 *
 */
public abstract class AbstractInvisibleProjectBasedTest extends AbstractProjectsManagerBasedTest {

	protected IProject copyAndImportFolder(String folder, String triggerFile) throws Exception {
		File projectFolder = copyFiles(folder, true);
		return importRootFolder(projectFolder, triggerFile);
	}

	/**
	 * Creates a temporary folder prefixed with <code>name</code> containing sources
	 * and jars under a lib directory.
	 */
	protected File createSourceFolderWithLibs(String name) throws Exception {
		return createSourceFolderWithLibs(name, true);
	}

	/**
	 * Creates a temporary folder prefixed with <code>name</code> containing sources
	 * but without its required jars.
	 */
	protected File createSourceFolderWithMissingLibs(String name) throws Exception {
		return createSourceFolderWithLibs(name, false);
	}

	protected File createSourceFolderWithLibs(String name, boolean addLibs) throws Exception {
		java.nio.file.Path projectPath = Files.createTempDirectory(name);
		File projectFolder = projectPath.toFile();
		FileUtils.copyDirectory(new File(getSourceProjectDirectory(), "eclipse/source-attachment/src"), projectFolder);
		if (addLibs) {
			addLibs(projectPath);
		}
		return projectFolder;
	}

	protected void addLibs(java.nio.file.Path projectPath) throws Exception {
		java.nio.file.Path libPath = Files.createDirectories(projectPath.resolve(InvisibleProjectBuildSupport.LIB_FOLDER));
		File libFile = libPath.toFile();
		FileUtils.copyFileToDirectory(new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo.jar"), libFile);
		FileUtils.copyFileToDirectory(new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo-sources.jar"), libFile);
	}

	protected IProject importRootFolder(File projectFolder, String triggerFile) throws Exception {
		IPath rootPath = Path.fromOSString(projectFolder.getAbsolutePath());
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
