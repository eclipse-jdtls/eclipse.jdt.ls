/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.commands.BuildPathCommand.ListCommandResult;
import org.eclipse.jdt.ls.core.internal.commands.BuildPathCommand.Result;
import org.eclipse.jdt.ls.core.internal.commands.BuildPathCommand.SourcePath;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.GradleBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.MavenBuildSupport;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.junit.Test;

public class BuildPathCommandTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testBuildPathOperationInWorkspaceProject() throws IOException {
		String path = "singlefile/lesson1";
		File file = copyFiles(path, true);
		IPath workspaceRoot = Path.fromOSString(file.getAbsolutePath());
		PreferenceManager manager = JavaLanguageServerPlugin.getPreferencesManager();
		manager.getPreferences().setRootPaths(Arrays.asList(workspaceRoot));

		IPath srcPath = workspaceRoot.append("src");
		Result addSrcResult = BuildPathCommand.addToSourcePath(srcPath.toFile().toURI().toString());
		assertTrue(addSrcResult.status);
		String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(workspaceRoot);
		IProject invisibleProject = ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);
		assertTrue(invisibleProject.exists());
		assertTrue(ProjectUtils.isJavaProject(invisibleProject));

		IPath mainJavaPath = workspaceRoot.append("src/main/java");
		Result addMainJavaResult = BuildPathCommand.addToSourcePath(mainJavaPath.toFile().toURI().toString());
		assertFalse(addMainJavaResult.status);

		IPath samplesPath = workspaceRoot.append("samples");
		Result addSamplesResult = BuildPathCommand.addToSourcePath(samplesPath.toFile().toURI().toString());
		assertTrue(addSamplesResult.status);

		ListCommandResult listResult = (ListCommandResult) BuildPathCommand.listSourcePaths();
		assertTrue(listResult.status);
		SourcePath[] sourcePaths = listResult.data;
		assertNotNull(sourcePaths);
		assertEquals(sourcePaths.length, 2);
		assertEquals(sourcePaths[0].displayPath, new Path("lesson1").append("src").toOSString());
		assertEquals(sourcePaths[1].displayPath, new Path("lesson1").append("samples").toOSString());
	}

	@Test
	public void testBuildPathOperationInEclipseProject() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		PreferenceManager manager = JavaLanguageServerPlugin.getPreferencesManager();
		manager.getPreferences().setRootPaths(Arrays.asList(project.getLocation()));

		ListCommandResult listResult = (ListCommandResult) BuildPathCommand.listSourcePaths();
		assertTrue(listResult.status);
		SourcePath[] sourcePaths = listResult.data;
		assertNotNull(sourcePaths);
		assertEquals(sourcePaths.length, 2);
		assertEquals(sourcePaths[0].displayPath, new Path("hello").append("src").toOSString());
		assertEquals(sourcePaths[1].displayPath, new Path("hello").append("test").toOSString());

		IResource srcJavaResource = project.findMember("src/java");
		Result addSrcJavaResult = BuildPathCommand.addToSourcePath(JDTUtils.getFileURI(srcJavaResource));
		assertFalse(addSrcJavaResult.status);

		IJavaProject javaProject = JavaCore.create(project);
		IResource nopackageResource = project.findMember("nopackage");
		Result addNopackageResult = BuildPathCommand.addToSourcePath(JDTUtils.getFileURI(nopackageResource));
		assertTrue(addNopackageResult.status);
		assertEquals(ProjectUtils.listSourcePaths(javaProject).length, 3);

		Result removeNopackageResult = BuildPathCommand.removeFromSourcePath(JDTUtils.getFileURI(nopackageResource));
		assertTrue(removeNopackageResult.status);
		assertEquals(ProjectUtils.listSourcePaths(javaProject).length, 2);
	}

	@Test
	public void testBuildPathOperationInMavenProject() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		PreferenceManager manager = JavaLanguageServerPlugin.getPreferencesManager();
		manager.getPreferences().setRootPaths(Arrays.asList(project.getLocation()));

		ListCommandResult listResult = (ListCommandResult) BuildPathCommand.listSourcePaths();
		assertTrue(listResult.status);
		SourcePath[] sourcePaths = listResult.data;
		assertNotNull(sourcePaths);
		assertEquals(sourcePaths.length, 6);

		IResource srcResource = project.findMember("src");
		Result addSrcResult = BuildPathCommand.addToSourcePath(JDTUtils.getFileURI(srcResource));
		assertFalse(addSrcResult.status);
		assertEquals(addSrcResult.message, MavenBuildSupport.UNSUPPORTED_ON_MAVEN);

		IResource mainJavaResource = project.findMember("src/main/java");
		Result addMainJavaResult = BuildPathCommand.removeFromSourcePath(JDTUtils.getFileURI(mainJavaResource));
		assertFalse(addMainJavaResult.status);
		assertEquals(addMainJavaResult.message, MavenBuildSupport.UNSUPPORTED_ON_MAVEN);
	}

	@Test
	public void testBuildPathOperationInGradleProject() throws Exception {
		importProjects("gradle/simple-gradle");
		IProject project = WorkspaceHelper.getProject("simple-gradle");
		PreferenceManager manager = JavaLanguageServerPlugin.getPreferencesManager();
		manager.getPreferences().setRootPaths(Arrays.asList(project.getLocation()));

		ListCommandResult listResult = (ListCommandResult) BuildPathCommand.listSourcePaths();
		assertTrue(listResult.status);
		SourcePath[] sourcePaths = listResult.data;
		assertNotNull(sourcePaths);
		assertEquals(sourcePaths.length, 2);

		IResource srcResource = project.findMember("src");
		Result addSrcResult = BuildPathCommand.addToSourcePath(JDTUtils.getFileURI(srcResource));
		assertFalse(addSrcResult.status);
		assertEquals(addSrcResult.message, GradleBuildSupport.UNSUPPORTED_ON_GRADLE);

		IResource mainJavaResource = project.findMember("src/main/java");
		Result addMainJavaResult = BuildPathCommand.removeFromSourcePath(JDTUtils.getFileURI(mainJavaResource));
		assertFalse(addMainJavaResult.status);
		assertEquals(addMainJavaResult.message, GradleBuildSupport.UNSUPPORTED_ON_GRADLE);
	}
}
