/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers.ondemand;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.ondemand.ModuleInfo.BuildType;

/**
 * Scans the workspace to discover all build modules and collect minimal info.
 *
 * <p>The scan is designed to run in a background {@code Job}. Results are
 * stored in a thread-safe list that can be queried at any time — partial
 * results are available while the scan is still running.</p>
 */
public class ModuleScanner {

	private static final String POM_XML = "pom.xml";
	private static final String BUILD_GRADLE = "build.gradle";
	private static final String BUILD_GRADLE_KTS = "build.gradle.kts";

	private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile(
			"<artifactId>\\s*([^<]+?)\\s*</artifactId>");
	private static final Pattern SOURCE_DIR_PATTERN = Pattern.compile(
			"<sourceDirectory>\\s*([^<]+?)\\s*</sourceDirectory>");
	private static final Pattern TEST_SOURCE_DIR_PATTERN = Pattern.compile(
			"<testSourceDirectory>\\s*([^<]+?)\\s*</testSourceDirectory>");

	private final List<ModuleInfo> modules = new CopyOnWriteArrayList<>();
	private volatile boolean scanComplete;

	/**
	 * Scans the workspace root for all modules containing build files.
	 * Thread-safe: results are added to {@link #modules} as they are found.
	 */
	public void scan(Path workspacePath) {
		long start = System.currentTimeMillis();
		scanDirectory(workspacePath);
		scanComplete = true;
		JavaLanguageServerPlugin.logInfo("Module scan completed: " + modules.size()
				+ " module(s) found in " + (System.currentTimeMillis() - start) + "ms");
	}

	private static final String[] STANDARD_SOURCE_FOLDERS = {
		"src/main/java", "src/test/java"
	};

	private void scanDirectory(Path dir) {
		Path pomPath = dir.resolve(POM_XML);
		if (Files.isRegularFile(pomPath)) {
			String artifactId = extractMavenArtifactId(pomPath);
			if (artifactId == null) {
				artifactId = dir.getFileName().toString();
			}
			List<Path> sourceFolders = detectSourceFolders(dir);
			if (sourceFolders.isEmpty()) {
				extractMavenSourceDirectories(pomPath, dir, sourceFolders);
			}
			modules.add(new ModuleInfo(dir.toAbsolutePath().normalize(), artifactId, BuildType.MAVEN, sourceFolders));
		} else if (Files.isRegularFile(dir.resolve(BUILD_GRADLE)) || Files.isRegularFile(dir.resolve(BUILD_GRADLE_KTS))) {
			modules.add(new ModuleInfo(dir.toAbsolutePath().normalize(), dir.getFileName().toString(), BuildType.GRADLE, detectSourceFolders(dir)));
		}

		File[] children = dir.toFile().listFiles(File::isDirectory);
		if (children != null) {
			for (File child : children) {
				String name = child.getName();
				if (name.startsWith(".") || name.equals("target") || name.equals("build")
						|| name.equals("node_modules") || name.equals("bin")) {
					continue;
				}
				scanDirectory(child.toPath());
			}
		}
	}

	/**
	 * Extracts the first {@code <artifactId>} from a pom.xml without full XML parsing.
	 * Reads only until the first match is found (typically within the first 20 lines).
	 */
	private static String extractMavenArtifactId(Path pomPath) {
		try (BufferedReader reader = Files.newBufferedReader(pomPath)) {
			// Skip parent's artifactId: read until </parent> or first artifactId outside parent
			boolean inParent = false;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains("<parent>")) {
					inParent = true;
				} else if (line.contains("</parent>")) {
					inParent = false;
				} else if (!inParent) {
					Matcher m = ARTIFACT_ID_PATTERN.matcher(line);
					if (m.find()) {
						return m.group(1);
					}
				}
			}
		} catch (IOException e) {
			// Fall back to directory name
		}
		return null;
	}

	/**
	 * Parses {@code <sourceDirectory>} and {@code <testSourceDirectory>} from a pom.xml.
	 * Called only when no standard source folders exist in the module directory.
	 */
	private static void extractMavenSourceDirectories(Path pomPath, Path moduleDir, List<Path> result) {
		try (BufferedReader reader = Files.newBufferedReader(pomPath)) {
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = SOURCE_DIR_PATTERN.matcher(line);
				if (m.find()) {
					addIfDirectory(moduleDir, m.group(1), result);
				}
				m = TEST_SOURCE_DIR_PATTERN.matcher(line);
				if (m.find()) {
					addIfDirectory(moduleDir, m.group(1), result);
				}
			}
		} catch (IOException e) {
			// ignore
		}
	}

	private static void addIfDirectory(Path moduleDir, String dirPath, List<Path> result) {
		Path resolved = Path.of(dirPath);
		if (!resolved.isAbsolute()) {
			resolved = moduleDir.resolve(resolved);
		}
		resolved = resolved.toAbsolutePath().normalize();
		if (Files.isDirectory(resolved)) {
			result.add(resolved);
		}
	}

	private static List<Path> detectSourceFolders(Path moduleDir) {
		List<Path> found = new ArrayList<>();
		for (String srcFolder : STANDARD_SOURCE_FOLDERS) {
			Path candidate = moduleDir.resolve(srcFolder);
			if (Files.isDirectory(candidate)) {
				found.add(candidate.toAbsolutePath().normalize());
			}
		}
		return found;
	}

	public List<ModuleInfo> getModules() {
		return modules;
	}

	public boolean isScanComplete() {
		return scanComplete;
	}
}
