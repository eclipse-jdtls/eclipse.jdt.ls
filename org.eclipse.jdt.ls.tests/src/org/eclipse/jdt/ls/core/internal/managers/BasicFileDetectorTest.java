/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class BasicFileDetectorTest {

	@Test
	public void testScanBuildFileAtRootExcludingNestedDirs() throws Exception {
		BasicFileDetector detector = new BasicFileDetector(Paths.get("projects/buildfiles"), "buildfile")
				.includeNested(false);
		Collection<Path> dirs = detector.scan(null);
		assertEquals("Found " + dirs, 1, dirs.size());// .metadata is ignored
		assertEquals(FilenameUtils.separatorsToSystem("projects/buildfiles"), dirs.iterator().next().toString());
	}

	@Test
	public void testScanBuildFileAtRootIncludingNestedDirs() throws Exception {
		BasicFileDetector detector = new BasicFileDetector(Paths.get("projects/buildfiles/"), "buildfile");
		Collection<Path> dirs = detector.scan(null);
		assertEquals("Found " + dirs, 6, dirs.size());

		List<String> missingDirs = separatorsToSystem(list("projects/buildfiles", "projects/buildfiles/parent/1_0/0_2_0",
				"projects/buildfiles/parent/1_0/0_2_1", "projects/buildfiles/parent/1_1",
				"projects/buildfiles/parent/1_1/1_2_0", "projects/buildfiles/parent/1_1/1_2_1"));
		dirs.stream().map(Path::toString).forEach(missingDirs::remove);
		assertEquals("Directories were not detected" + missingDirs, 0, missingDirs.size());
	}

	private List<String> separatorsToSystem(List<String> paths) {
		return paths.stream().map(p -> FilenameUtils.separatorsToSystem(p))
				.collect(Collectors.toList());
	}

	@Test
	public void testScanExcludingNestedBuildFilesDepth3() throws Exception {
		BasicFileDetector detector = new BasicFileDetector(Paths.get("projects/buildfiles/parent"), "buildfile")
				.includeNested(false).maxDepth(3);
		Collection<Path> dirs = detector.scan(null);
		assertEquals("Found " + dirs, 3, dirs.size());
		List<String> missingDirs = separatorsToSystem(list("projects/buildfiles/parent/1_1",
				"projects/buildfiles/parent/1_0/0_2_0", "projects/buildfiles/parent/1_0/0_2_1"));
		dirs.stream().map(Path::toString).forEach(missingDirs::remove);
		assertEquals("Directories were not detected" + missingDirs, 0, missingDirs.size());
	}

	@Test
	public void testInclusions() throws Exception {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		List<String> javaImportInclusions = new LinkedList<>();
		javaImportInclusions.addAll(javaImportExclusions);
		javaImportInclusions.add("**/parent/**");
		javaImportInclusions.add("!**/parent");
		javaImportInclusions.add("!**/parent/1_0");
		javaImportInclusions.add("!**/parent/1_0/*");
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaImportExclusions(javaImportInclusions);
			BasicFileDetector detector = new BasicFileDetector(Paths.get("projects/buildfiles/parent"), "buildfile").includeNested(false).maxDepth(3);
			Collection<Path> dirs = detector.scan(null);
			assertEquals("Found " + dirs + " ,exclusions=" + JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions(), 2, dirs.size());
			List<String> missingDirs = separatorsToSystem(list("projects/buildfiles/parent/1_0/0_2_0", "projects/buildfiles/parent/1_0/0_2_1"));
			dirs.stream().map(Path::toString).forEach(missingDirs::remove);
			assertEquals("Directories were not detected" + missingDirs, 0, missingDirs.size());
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaImportExclusions(javaImportExclusions);
			detector = new BasicFileDetector(Paths.get("projects/buildfiles/parent"), "buildfile").includeNested(false).maxDepth(3);
			dirs = detector.scan(null);
			assertEquals("Found " + dirs, 3, dirs.size());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaImportExclusions(javaImportExclusions);
		}
	}

	@Test
	public void testInclusions2() throws Exception {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		List<String> javaImportInclusions = new LinkedList<>();
		javaImportInclusions.addAll(javaImportExclusions);
		javaImportInclusions.add("**/parent/**");
		javaImportInclusions.add("!**/parent");
		javaImportInclusions.add("!**/parent/1_0");
		javaImportInclusions.add("!**/parent/1_0/0_2_0");
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaImportExclusions(javaImportInclusions);
			BasicFileDetector detector = new BasicFileDetector(Paths.get("projects/buildfiles/parent"), "buildfile").includeNested(false).maxDepth(3);
			Collection<Path> dirs = detector.scan(null);
			assertEquals("Found " + " ,exclusions=" + JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions() + dirs, 1, dirs.size());
			List<String> missingDirs = separatorsToSystem(list("projects/buildfiles/parent/1_0/0_2_0"));
			dirs.stream().map(Path::toString).forEach(missingDirs::remove);
			assertEquals("Directories were not detected" + missingDirs, 0, missingDirs.size());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaImportExclusions(javaImportExclusions);
		}
	}

	@Test
	public void testInclusions3() throws Exception {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		List<String> javaImportInclusions = new LinkedList<>();
		javaImportInclusions.addAll(javaImportExclusions);
		javaImportInclusions.add("!**/parent");
		javaImportInclusions.add("!**/parent/1_0");
		javaImportInclusions.add("!**/parent/1_0/0_2_0");
		javaImportInclusions.add("**/parent/**");
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaImportExclusions(javaImportInclusions);
			BasicFileDetector detector = new BasicFileDetector(Paths.get("projects/buildfiles/parent"), "buildfile").includeNested(false).maxDepth(3);
			Collection<Path> dirs = detector.scan(null);
			assertEquals("Found " + " ,exclusions=" + JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions() + dirs, 0, dirs.size());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaImportExclusions(javaImportExclusions);
		}
	}


	@Test
	public void testScanNestedBuildFilesDepth2() throws Exception {
		BasicFileDetector detector = new BasicFileDetector(Paths.get("projects/buildfiles/parent"), "buildfile")
				.includeNested(false).maxDepth(2);
		Collection<Path> dirs = detector.scan(null);
		assertEquals("Found " + dirs, 1, dirs.size());
		assertEquals(FilenameUtils.separatorsToSystem("projects/buildfiles/parent/1_1"),
				dirs.iterator().next().toString());
	}

    @Test
	public void testScanSymbolicLinks() throws Exception {
		File tempDirectory = new File(System.getProperty("java.io.tmpdir"), "/projects_symbolic_link-"
			+ new Random().nextInt(10000));
		tempDirectory.mkdirs();
		File targetLinkFolder = new File(tempDirectory, "buildfiles");
		try {
			Files.createSymbolicLink(Paths.get(targetLinkFolder.getPath()), Paths.get("projects/buildfiles").toAbsolutePath());
			BasicFileDetector detector = new BasicFileDetector(Paths.get(tempDirectory.getPath()), "buildfile")
			    .includeNested(false).maxDepth(2);

			Collection<Path> dirs = detector.scan(null);
			assertEquals("Found " + dirs, 1, dirs.size());// .metadata is ignored
			assertEquals(targetLinkFolder.getAbsolutePath(), dirs.iterator().next().toString());
		} finally {
			targetLinkFolder.delete();
			FileUtils.deleteDirectory(tempDirectory);
		}
	}

	@Test
	public void testScanCircularSymbolicLinks() throws Exception {
		File originDirectory = new File("projects/buildfiles");
		File tempDirectory = new File(System.getProperty("java.io.tmpdir"), "/circular_symbolic_link_ws-"+
			+ new Random().nextInt(10000));
		File circularSymbolicLink = new File(tempDirectory, "circular_symbolic_link");
		try {
			FileUtils.copyDirectory(originDirectory, tempDirectory);
			Path targetLinkPath = Paths.get(circularSymbolicLink.getPath());
			Files.createSymbolicLink(targetLinkPath, Paths.get(tempDirectory.getAbsolutePath()));
			BasicFileDetector detector = new BasicFileDetector(Paths.get(tempDirectory.getPath()), "buildfile");

			Collection<Path> dirs = detector.scan(null);
			assertEquals("Found " + dirs, 6, dirs.size());

			List<String> missingDirs = separatorsToSystem(list("", "parent/1_0/0_2_0",
					"parent/1_0/0_2_1", "parent/1_1",
					"parent/1_1/1_2_0", "parent/1_1/1_2_1"));
			dirs.stream().map(dir -> tempDirectory.toPath().relativize(dir).toString()).forEach(missingDirs::remove);
			assertEquals("Directories were not detected" + missingDirs, 0, missingDirs.size());
		} finally {
			circularSymbolicLink.delete();
			FileUtils.deleteDirectory(tempDirectory);
		}
	}

	@SafeVarargs
	private final <E> List<E> list(E... elements) {
		return new ArrayList<>(Arrays.asList(elements));
	}
}
