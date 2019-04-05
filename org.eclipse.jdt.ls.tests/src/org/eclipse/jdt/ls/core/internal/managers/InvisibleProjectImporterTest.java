/*******************************************************************************
 * Copyright (c) 2018-2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.TestVMType;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.junit.Test;

public class InvisibleProjectImporterTest extends AbstractInvisibleProjectBasedTest {

	@Test
	public void importIncompleteFolder() throws Exception {
		IProject invisibleProject = copyAndImportFolder("maven/salut/src/main/java/org/sample", "Bar.java");
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void importCompleteFolder() throws Exception {
		IProject invisibleProject = copyAndImportFolder("singlefile/lesson1", "src/org/samples/HelloWorld.java");
		assertTrue(invisibleProject.exists());
		IPath sourcePath = invisibleProject.getFolder(new Path(ProjectUtils.WORKSPACE_LINK).append("src")).getFullPath();
		assertTrue(ProjectUtils.isOnSourcePath(sourcePath, JavaCore.create(invisibleProject)));
	}

	@Test
	public void importCompleteFolderWithoutTriggerFile() throws Exception {
		IProject invisibleProject = copyAndImportFolder("singlefile/lesson1", null);
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void automaticJarDetection() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isWorkspaceChangeWatchedFilesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		File projectFolder = createSourceFolderWithLibs("automaticJarDetection");

		IProject invisibleProject = importRootFolder(projectFolder, "Test.java");
		assertNoErrors(invisibleProject);

		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
		assertEquals("foo.jar", classpath[2].getPath().lastSegment());
		assertEquals("foo-sources.jar", classpath[2].getSourceAttachmentPath().lastSegment());

		List<FileSystemWatcher> watchers = projectsManager.registerWatchers();
		watchers.sort((a, b) -> a.getGlobPattern().compareTo(b.getGlobPattern()));
		assertEquals(10, watchers.size());
		String srcGlobPattern = watchers.get(7).getGlobPattern();
		assertTrue("Unexpected source glob pattern: " + srcGlobPattern, srcGlobPattern.equals("**/src/**"));
		String libGlobPattern = watchers.get(9).getGlobPattern();
		assertTrue("Unexpected lib glob pattern: " + libGlobPattern, libGlobPattern.endsWith(projectFolder.getName() + "/lib/**"));
	}

	@Test
	public void getPackageNameFromRelativePathOfEmptyFile() throws Exception {
		File projectFolder = copyFiles("singlefile", true);
		IProject invisibleProject = importRootFolder(projectFolder, "lesson1/Test.java");
		assertTrue(invisibleProject.exists());

		IPath workspaceRoot = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath javaFile = workspaceRoot.append("lesson1/Test.java");
		String packageName = InvisibleProjectImporter.getPackageName(javaFile, workspaceRoot, JavaCore.create(invisibleProject));
		assertEquals("lesson1", packageName);
	}

	@Test
	public void getPackageNameFromNearbyNonEmptyFile() throws Exception {
		File projectFolder = copyFiles("singlefile", true);
		IProject invisibleProject = importRootFolder(projectFolder, "lesson1/samples/Empty.java");
		assertTrue(invisibleProject.exists());

		IPath workspaceRoot = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath javaFile = workspaceRoot.append("lesson1/samples/Empty.java");
		String packageName = InvisibleProjectImporter.getPackageName(javaFile, workspaceRoot, JavaCore.create(invisibleProject));
		assertEquals("samples", packageName);
	}

	@Test
	public void getPackageNameInSrcEmptyFile() throws Exception {
		File projectFolder = copyFiles("singlefile", true);
		IProject invisibleProject = importRootFolder(projectFolder, "lesson1/src/main/java/demosamples/Empty1.java");
		assertTrue(invisibleProject.exists());

		IPath workspaceRoot = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath javaFile = workspaceRoot.append("lesson1/src/main/java/demosamples/Empty1.java");
		String packageName = InvisibleProjectImporter.getPackageName(javaFile, workspaceRoot, JavaCore.create(invisibleProject));
		assertEquals("main.java.demosamples", packageName);
	}

	@Test
	public void getPackageName() throws Exception {
		File projectFolder = copyFiles("singlefile", true);
		IProject invisibleProject = importRootFolder(projectFolder, "Single.java");
		assertTrue(invisibleProject.exists());

		IPath workspaceRoot = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath javaFile = workspaceRoot.append("Single.java");
		String packageName = InvisibleProjectImporter.getPackageName(javaFile, workspaceRoot, JavaCore.create(invisibleProject));
		assertEquals("", packageName);
	}

	@Test
	public void testPreviewFeaturesEnabledByDefault() throws Exception {
		String defaultJVM = JavaRuntime.getDefaultVMInstall().getId();
		try {
			TestVMType.setTestJREAsDefault("12");
			IProject invisibleProject = copyAndImportFolder("singlefile/java12", "foo/bar/Foo.java");
			assertTrue(invisibleProject.exists());
			assertNoErrors(invisibleProject);
			IJavaProject javaProject = JavaCore.create(invisibleProject);
			assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		} finally {
			TestVMType.setTestJREAsDefault(defaultJVM);
		}
	}
}
