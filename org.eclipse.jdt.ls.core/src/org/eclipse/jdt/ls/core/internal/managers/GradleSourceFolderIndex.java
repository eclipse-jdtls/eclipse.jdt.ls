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
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * Source folder index for Gradle projects.
 *
 * <p>Module discovery: parses {@code settings.gradle} / {@code settings.gradle.kts}
 * for {@code include} directives, or falls back to recursive {@code build.gradle}
 * scanning.</p>
 *
 * <p>Dependency graph: extracts inter-project dependencies by scanning
 * {@code build.gradle} / {@code build.gradle.kts} for {@code project(':xxx')}
 * references.</p>
 *
 * <p>Module ID format: colon-separated subproject path without leading colon
 * (e.g. {@code extensions/java} or {@code core}).</p>
 */
public class GradleSourceFolderIndex extends AbstractSourceFolderIndex {

	public GradleSourceFolderIndex(Path workspacePath) {
		super(workspacePath);
	}

	public void scan() {
		scanSettingsGradle(workspacePath);
		if (moduleToDir.isEmpty()) {
			scanBuildFilesRecursive(workspacePath, 0);
		}
		scanBuildGradleForDependencies();
		buildReverseDependencies();
	}

	@Override
	protected boolean hasBuildFile(Path dir) {
		return Files.isRegularFile(dir.resolve("build.gradle"))
				|| Files.isRegularFile(dir.resolve("build.gradle.kts"));
	}

	@Override
	protected String discoverModule(Path dir) {
		if (!hasBuildFile(dir)) {
			return null;
		}
		String moduleId = dir.equals(workspacePath)
				? dir.getFileName().toString()
				: workspacePath.relativize(dir).toString().replace('\\', '/');
		registerModule(moduleId, dir);
		return moduleId;
	}

	private void scanSettingsGradle(Path dir) {
		Path settingsGradle = dir.resolve("settings.gradle");
		Path settingsGradleKts = dir.resolve("settings.gradle.kts");
		Path settingsFile = Files.isRegularFile(settingsGradle) ? settingsGradle
				: Files.isRegularFile(settingsGradleKts) ? settingsGradleKts : null;

		if (settingsFile == null) {
			return;
		}

		try {
			String content = Files.readString(settingsFile);
			parseSettingsIncludes(content, dir);
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException("Failed to parse " + settingsFile, e);
		}

		registerRootModule(dir);
	}

	private static final char[] INCLUDE = "include".toCharArray();

	private void parseSettingsIncludes(String content, Path baseDir) {
		int len = content.length();
		int i = 0;
		outer:
		while (i <= len - 10) {
			for (int k = 0; k < INCLUDE.length; k++) {
				if (content.charAt(i + k) != INCLUDE[k]) {
					i++;
					continue outer;
				}
			}
			int j = i + INCLUDE.length;
			if (j < len && Character.isLetter(content.charAt(j))) {
				i = j;
				continue;
			}
			boolean hasParen = false;
			while (j < len && content.charAt(j) == ' ') {
				j++;
			}
			if (j < len && content.charAt(j) == '(') {
				hasParen = true;
				j++;
			}
			while (j < len) {
				char c = content.charAt(j);
				if (hasParen) {
					if (c == ')') {
						j++;
						break;
					}
				} else {
					if (c == '\n' || c == '\r') {
						break;
					}
				}
				if (c == '\'' || c == '"') {
					j = extractOneModule(content, j, c, baseDir);
				} else {
					j++;
				}
			}
			i = j;
		}
	}

	private int extractOneModule(String content, int quotePos, char q, Path baseDir) {
		int len = content.length();
		int start = quotePos + 1;
		if (start < len && content.charAt(start) == ':') {
			start++;
		}
		StringBuilder modulePath = new StringBuilder();
		int s = start;
		while (s < len && content.charAt(s) != q) {
			char c = content.charAt(s);
			modulePath.append(c == ':' ? '/' : c);
			s++;
		}
		if (s < len && modulePath.length() > 0) {
			String mp = modulePath.toString();
			registerModule(mp, baseDir.resolve(mp));
		}
		return s + 1;
	}

	private void scanBuildGradleForDependencies() {
		for (Map.Entry<String, Path> entry : moduleToDir.entrySet()) {
			String moduleId = entry.getKey();
			Path dir = entry.getValue();

			Path buildGradle = dir.resolve("build.gradle");
			Path buildGradleKts = dir.resolve("build.gradle.kts");
			Path buildFile = Files.isRegularFile(buildGradle) ? buildGradle
					: Files.isRegularFile(buildGradleKts) ? buildGradleKts : null;
			if (buildFile == null) {
				continue;
			}

			try {
				String content = Files.readString(buildFile);
				extractProjectDependencies(moduleId, content);
			} catch (IOException e) {
				// ignore unreadable build files
			}
		}
	}

	private static final char[] PROJECT_PAREN = "project(".toCharArray();

	private void extractProjectDependencies(String moduleId, String content) {
		int len = content.length();
		int i = 0;
		outer:
		while (i <= len - 12) {
			for (int k = 0; k < PROJECT_PAREN.length; k++) {
				if (content.charAt(i + k) != PROJECT_PAREN[k]) {
					i++;
					continue outer;
				}
			}
			int j = i + PROJECT_PAREN.length;
			while (j < len && content.charAt(j) == ' ') {
				j++;
			}
			if (j >= len) {
				break;
			}
			char q = content.charAt(j);
			if (q != '\'' && q != '"') {
				i = j;
				continue;
			}
			j++;
			int start = j;
			if (start < len && content.charAt(start) == ':') {
				start++;
			}
			StringBuilder depId = new StringBuilder();
			int s = start;
			while (s < len && content.charAt(s) != q) {
				char c = content.charAt(s);
				depId.append(c == ':' ? '/' : c);
				s++;
			}
			if (s < len && depId.length() > 0) {
				String dep = depId.toString();
				if (moduleToDir.containsKey(dep) && !dep.equals(moduleId)) {
					addDependency(moduleId, dep);
				}
			}
			i = s + 1;
		}
	}

	private void scanBuildFilesRecursive(Path dir, int depth) {
		if (depth > 3) {
			return;
		}
		if (hasBuildFile(dir)) {
			String moduleId = dir.equals(workspacePath)
					? dir.getFileName().toString()
					: workspacePath.relativize(dir).toString().replace('\\', '/');
			registerModule(moduleId, dir);
		}

		try (var stream = Files.list(dir)) {
			stream.filter(Files::isDirectory)
					.filter(d -> !d.getFileName().toString().startsWith("."))
					.filter(d -> !d.getFileName().toString().equals("build"))
					.filter(d -> !d.getFileName().toString().equals("buildSrc"))
					.forEach(d -> scanBuildFilesRecursive(d, depth + 1));
		} catch (IOException e) {
			// ignore
		}
	}

	private void registerRootModule(Path dir) {
		String moduleId = dir.getFileName().toString();
		Path srcMainJava = dir.resolve("src/main/java");
		Path srcTestJava = dir.resolve("src/test/java");
		if (Files.isDirectory(srcMainJava) || Files.isDirectory(srcTestJava)) {
			registerModule(moduleId, dir);
		}
	}
}
