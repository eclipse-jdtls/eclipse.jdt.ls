/*******************************************************************************
 * Copyright (c) 2018-2021 Microsoft Corporation and others.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TestVMType;
import org.eclipse.jdt.ls.core.internal.managers.InvisibleProjectImporter.JavaFileDetector;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.RelativePattern;
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
		assertTrue(invisibleProject.hasNature(UnmanagedFolderNature.NATURE_ID));
		IPath sourcePath = invisibleProject.getFolder(new Path(ProjectUtils.WORKSPACE_LINK).append("src")).getFullPath();
		assertTrue(ProjectUtils.isOnSourcePath(sourcePath, JavaCore.create(invisibleProject)));
	}

	@Test
	public void importCompleteFolderWithoutTriggerFile() throws Exception {
		IProject invisibleProject = copyAndImportFolder("singlefile/lesson1", null);
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void importPartialMavenFolder() throws Exception {
		File projectFolder = copyFiles("maven/salut-java11", true);
		IPath projectFullPath = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath rootPath = projectFullPath.append("src");
		IProject invisibleProject = importRootFolder(rootPath, "main/java/org/sample/Bar.java");
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void importPartialGradleFolder() throws Exception {
		File projectFolder = copyFiles("gradle/gradle-11", true);
		IPath projectFullPath = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath rootPath = projectFullPath.append("src");
		IProject invisibleProject = importRootFolder(rootPath, "main/java/foo/bar/Foo.java");
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void automaticJarDetectionLibUnderSource() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isWorkspaceChangeWatchedFilesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		File projectFolder = createSourceFolderWithLibs("automaticJarDetectionLibUnderSource");

		IProject invisibleProject = importRootFolder(projectFolder, "Test.java");
		assertNoErrors(invisibleProject);

		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
		assertEquals("foo.jar", classpath[2].getPath().lastSegment());
		assertEquals("foo-sources.jar", classpath[2].getSourceAttachmentPath().lastSegment());

		List<FileSystemWatcher> watchers = projectsManager.registerWatchers();
		//watchers.sort((a, b) -> a.getGlobPattern().compareTo(b.getGlobPattern()));
		assertEquals(12, watchers.size()); // basic(9) + project(1) + library(1)
		String srcGlobPattern = watchers.stream().map(FileSystemWatcher::getGlobPattern).map(globPattern -> globPattern.map(Function.identity(), RelativePattern::getPattern)).filter("**/src/**"::equals).findFirst().get();
		assertTrue("Unexpected source glob pattern: " + srcGlobPattern, srcGlobPattern.equals("**/src/**"));
		String projGlobPattern = watchers.stream().map(FileSystemWatcher::getGlobPattern).map(globPattern -> globPattern.map(Function.identity(), RelativePattern::getPattern)).filter(w -> w.endsWith(projectFolder.getName() + "/**"))
				.findFirst().get();
		assertTrue("Unexpected project glob pattern: " + projGlobPattern, projGlobPattern.endsWith(projectFolder.getName() + "/**"));
		String libGlobPattern = watchers.stream().map(FileSystemWatcher::getGlobPattern).map(globPattern -> globPattern.map(Function.identity(), RelativePattern::getPattern)).filter(w -> w.endsWith(projectFolder.getName() + "/lib/**"))
				.findFirst().get();
		assertTrue("Unexpected library glob pattern: " + libGlobPattern, libGlobPattern.endsWith(projectFolder.getName() + "/lib/**"));
	}

	public void automaticJarDetection() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isWorkspaceChangeWatchedFilesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		File projectFolder = createSourceFolderWithLibs("automaticJarDetection", "src", true);

		IProject invisibleProject = importRootFolder(projectFolder, "Test.java");
		assertNoErrors(invisibleProject);

		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
		assertEquals("foo.jar", classpath[2].getPath().lastSegment());
		assertEquals("foo-sources.jar", classpath[2].getSourceAttachmentPath().lastSegment());

		List<FileSystemWatcher> watchers = projectsManager.registerWatchers();
		watchers.sort((a, b) -> a.getGlobPattern().map(Function.identity(), RelativePattern::getPattern).compareTo(b.getGlobPattern().map(Function.identity(), RelativePattern::getPattern)));
		assertEquals(10, watchers.size());
		String srcGlobPattern = watchers.get(7).getGlobPattern().map(Function.identity(), RelativePattern::getPattern);
		assertTrue("Unexpected source glob pattern: " + srcGlobPattern, srcGlobPattern.equals("**/src/**"));
		String libGlobPattern = watchers.get(9).getGlobPattern().map(Function.identity(), RelativePattern::getPattern);
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
			TestVMType.setTestJREAsDefault("20");
			IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
			assertTrue(invisibleProject.exists());
			assertNoErrors(invisibleProject);
			IJavaProject javaProject = JavaCore.create(invisibleProject);
			assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
			assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		} finally {
			TestVMType.setTestJREAsDefault(defaultJVM);
		}
	}

	@Test
	public void testPreviewFeaturesDisabledForNotLatestJDK() throws Exception {
		String defaultJVM = JavaRuntime.getDefaultVMInstall().getId();
		try {
			String secondToLastJDK = JavaCore.getAllVersions().get(JavaCore.getAllVersions().size() - 2);
			TestVMType.setTestJREAsDefault(secondToLastJDK);
			IProject invisibleProject = copyAndImportFolder("singlefile/lesson1", "src/org/samples/HelloWorld.java");
			assertTrue(invisibleProject.exists());
			assertNoErrors(invisibleProject);
			IJavaProject javaProject = JavaCore.create(invisibleProject);
			assertEquals(JavaCore.DISABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, true));
		} finally {
			TestVMType.setTestJREAsDefault(defaultJVM);
		}
	}

	@Test
	public void testSpecifyingOutputPath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath("output");
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		assertEquals(String.join("/", "", javaProject.getElementName(), ProjectUtils.WORKSPACE_LINK, "output"), javaProject.getOutputLocation().toString());
	}

	@Test
	public void testSpecifyingOutputPathInsideSourcePath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath("output");
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		boolean isOutputExcluded = false;
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
				continue;
			}
			for (IPath excludePath : entry.getExclusionPatterns()) {
				if (excludePath.toString().equals("output/")) {
					isOutputExcluded = true;
					break;
				}
			}
		}
		assertTrue("Output path should be excluded from source path", isOutputExcluded);
	}

	@Test
	public void testSpecifyingOutputPathEqualToSourcePath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath("src");
		copyAndImportFolder("singlefile/simple2", "src/App.java");
		waitForBackgroundJobs();
	}

	@Test(expected = CoreException.class)
	public void testSpecifyingAbsoluteOutputPath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath(new File("projects").getAbsolutePath());
		copyAndImportFolder("singlefile/simple", "src/App.java");
		waitForBackgroundJobs();
	}

	@Test
	public void testSpecifyingEmptyOutputPath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath("");
		IProject invisibleProject = copyAndImportFolder("singlefile/simple", "src/App.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		assertEquals(String.join("/", "", javaProject.getElementName(), "bin"), javaProject.getOutputLocation().toString());
	}

	@Test
	public void testSpecifyingSourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList("foo", "bar"));
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(1, sourcePaths.size());
		assertTrue(sourcePaths.contains("foo"));
	}

	@Test
	public void testSpecifyingEmptySourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Collections.emptyList());
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(0, sourcePaths.size());
	}

	@Test
	public void testSpecifyingNestedSourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList("foo", "foo/bar"));
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(2, sourcePaths.size());
		assertTrue(sourcePaths.contains("foo"));
		assertTrue(sourcePaths.contains("foo/bar"));
	}

	@Test
	public void testSpecifyingDuplicatedSourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList("foo", "foo"));
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(1, sourcePaths.size());
		assertTrue(sourcePaths.contains("foo"));
	}

	@Test
	public void testSpecifyingRootAsSourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList(""));
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(1, sourcePaths.size());
		assertTrue(sourcePaths.contains(""));
	}

	@Test(expected = CoreException.class)
	public void testSpecifyingAbsoluteSourcePath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList(new File("projects").getAbsolutePath()));
		copyAndImportFolder("singlefile/simple", "src/App.java");
		waitForBackgroundJobs();
	}

	@Test
	public void testSpecifyingSourcePathsContainingOutputPath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList(""));
		preferences.setInvisibleProjectOutputPath("bin");
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);

		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				assertEquals("bin/", entry.getExclusionPatterns()[0].toString());
			}
		}
	}

	@Test
	public void testInferSourceRoot() throws Exception {
		preferenceManager.getPreferences().setJavaImportExclusions(Arrays.asList("**/excluded"));
		IProject invisibleProject = copyAndImportFolder("singlefile/inferSourceRoot", "lesson1/Lesson1.java");
		waitForBackgroundJobs();

		IFolder projectFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);
		IPath workspaceRoot = projectFolder.getLocation();
		preferenceManager.getPreferences().setRootPaths(Arrays.asList(workspaceRoot));

		IJavaProject javaProject = JavaCore.create(invisibleProject);
		long sourceRootsCount = Arrays.stream(javaProject.getRawClasspath())
				.filter(cp -> cp.getEntryKind() == IClasspathEntry.CPE_SOURCE)
				.count();
		assertEquals(3, sourceRootsCount);

		IFile unDiscoveredFile = invisibleProject.getFile("_/a/very/deep/path/Source.java");
		InvisibleProjectImporter.inferSourceRoot(javaProject, unDiscoveredFile.getLocation());
		waitForBackgroundJobs();
		sourceRootsCount = Arrays.stream(javaProject.getRawClasspath())
				.filter(cp -> cp.getEntryKind() == IClasspathEntry.CPE_SOURCE)
				.count();
		assertEquals(4, sourceRootsCount);

		List<IMarker> markers = ResourceUtils.getErrorMarkers(invisibleProject);
		assertEquals(0, markers.size());
	}

	@Test
	public void testInferSourceRoot2() throws Exception {
		preferenceManager.getPreferences().setJavaImportExclusions(Arrays.asList("**/excluded"));
		IProject invisibleProject = copyAndImportFolder("singlefile/inferSourceRoot", "Main.java");
		waitForBackgroundJobs();

		IFolder projectFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);
		IPath workspaceRoot = projectFolder.getLocation();
		preferenceManager.getPreferences().setRootPaths(Arrays.asList(workspaceRoot));

		IJavaProject javaProject = JavaCore.create(invisibleProject);
		long sourceRootsCount = Arrays.stream(javaProject.getRawClasspath())
				.filter(cp -> cp.getEntryKind() == IClasspathEntry.CPE_SOURCE)
				.count();
		assertEquals(3, sourceRootsCount);

		IFile unDiscoveredFile = invisibleProject.getFile("_/a/very/deep/path/Source.java");
		InvisibleProjectImporter.inferSourceRoot(javaProject, unDiscoveredFile.getLocation());
		waitForBackgroundJobs();
		sourceRootsCount = Arrays.stream(javaProject.getRawClasspath())
				.filter(cp -> cp.getEntryKind() == IClasspathEntry.CPE_SOURCE)
				.count();
		assertEquals(4, sourceRootsCount);

		List<IMarker> markers = ResourceUtils.getErrorMarkers(invisibleProject);
		assertEquals(0, markers.size());
	}

	@Test
	public void javaFileDetectorTest() throws Exception {
		createMockProject();
		preferenceManager.getPreferences().setJavaImportExclusions(Arrays.asList("**/excluded"));
		File root = new File(getSourceProjectDirectory(), "singlefile/invisibleFileDetector");
		List<File> foldersToSearch = new ArrayList<>();
		for (File folder : root.listFiles()) {
			if (folder.isDirectory()) {
				foldersToSearch.add(folder);
			}
		}

		JavaFileDetector detector = new JavaFileDetector(null);
		for (File file : foldersToSearch) {
			Files.walkFileTree(file.toPath(), EnumSet.noneOf(FileVisitOption.class), 3 /*maxDepth*/, detector);
		}
		Set<IPath> triggerFiles = detector.getTriggerFiles();

		assertEquals(0, triggerFiles.size());
	}

	private void createMockProject() throws CoreException {
		IProgressMonitor monitor = new NullProgressMonitor();
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("mock");
		if (!project.exists()) {
			IProjectDescription description = ResourcesPlugin.getWorkspace().newProjectDescription("mock");
			project.create(description, monitor);
			project.open(monitor);
			description.setNatureIds(new String[] { JavaCore.NATURE_ID });
			project.setDescription(description, monitor);
			IFolder folder = project.getFolder("_");
			if (!folder.exists()) {
				folder.create(true, true, monitor);
			}
			IFile fakeFile = project.getFile("_/Other.java");
			File file = new File(getSourceProjectDirectory(), "singlefile/invisibleFileDetector/other-project/Other.java");
			fakeFile.createLink(file.toURI(), IResource.REPLACE, monitor);
		}
	}
}
