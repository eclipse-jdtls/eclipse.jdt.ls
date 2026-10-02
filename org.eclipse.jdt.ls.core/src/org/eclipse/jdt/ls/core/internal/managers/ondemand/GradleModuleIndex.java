/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * Module index for Gradle projects.
 *
 * <p>At startup, scans {@code settings.gradle} / {@code settings.gradle.kts}
 * for {@code include} directives to discover modules upfront. Falls back
 * to recursive {@code build.gradle} scanning if no settings file exists.</p>
 *
 * <p>Lazy discovery via walk-up is also supported: if a file is opened
 * in a module that wasn't discovered during the initial scan, the inherited
 * {@link AbstractModuleIndex#findModulePath(Path)} will walk up and
 * register it.</p>
 */
public class GradleModuleIndex extends AbstractModuleIndex {

	private static final String BUILD_GRADLE = "build.gradle";
	private static final String BUILD_GRADLE_KTS = "build.gradle.kts";
	private static final String SETTINGS_GRADLE = "settings.gradle";
	private static final String SETTINGS_GRADLE_KTS = "settings.gradle.kts";
	private static final int MAX_SCAN_DEPTH = 3;

	/**
	 * Creates a new Gradle module index.
	 *
	 * @param workspacePath the workspace root directory
	 */
	public GradleModuleIndex(Path workspacePath) {
		super(workspacePath);
	}

	/**
	 * Scans the workspace to discover Gradle modules.
	 *
	 * <p>First attempts to parse {@code settings.gradle(.kts)} for
	 * {@code include} directives, which is the most reliable source of
	 * module information. Falls back to recursive directory scanning
	 * if no settings file exists.</p>
	 */
	public void scan() {
		// ex: /workspace/quarkus/settings.gradle exists → parse include directives
		if (!scanSettingsGradle()) {
			// ex: no settings.gradle → scan directories recursively for build.gradle
			scanBuildFilesRecursive(workspacePath, 0);
		}
	}

	@Override
	protected boolean hasBuildFile(Path dir) {
		// ex: /workspace/client/build.gradle exists → true
		// ex: /workspace/client/build.gradle.kts exists → true
		return Files.isRegularFile(dir.resolve(BUILD_GRADLE))
				|| Files.isRegularFile(dir.resolve(BUILD_GRADLE_KTS));
	}

	// ── Settings file parsing ───────────────────────────────────────────

	/**
	 * Parses the Gradle settings file to discover modules.
	 *
	 * <p>Extracts module paths from {@code include 'xxx'} and
	 * {@code include('xxx')} directives. Colons in Gradle subproject paths
	 * (e.g. {@code :extensions:java}) are converted to forward slashes
	 * (e.g. {@code extensions/java}).</p>
	 *
	 * @return {@code true} if a settings file was found and parsed
	 */
	private boolean scanSettingsGradle() {
		// ex: looks for /workspace/settings.gradle or /workspace/settings.gradle.kts
		Path settingsFile = resolveSettingsFile();
		if (settingsFile == null) {
			return false;
		}
		try {
			// ex: reads "include 'client', 'server'" → registers /workspace/client, /workspace/server
			String content = Files.readString(settingsFile);
			parseSettingsIncludes(content);
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException("Failed to parse " + settingsFile, e);
		}
		// ex: /workspace/build.gradle exists → register root as a module too
		if (hasBuildFile(workspacePath)) {
			registerModule(workspacePath);
		}
		return true;
	}

	/**
	 * Finds the Gradle settings file (Groovy or Kotlin DSL).
	 *
	 * @return the path to the settings file, or {@code null} if none exists
	 */
	private Path resolveSettingsFile() {
		// ex: /workspace/settings.gradle
		Path groovy = workspacePath.resolve(SETTINGS_GRADLE);
		if (Files.isRegularFile(groovy)) {
			return groovy;
		}
		// ex: /workspace/settings.gradle.kts
		Path kotlin = workspacePath.resolve(SETTINGS_GRADLE_KTS);
		if (Files.isRegularFile(kotlin)) {
			return kotlin;
		}
		return null;
	}

	/**
	 * Parses Gradle {@code include} directives from settings file content.
	 *
	 * <p>Supports both Groovy ({@code include 'x', 'y'}) and Kotlin
	 * ({@code include("x")}) syntax. Leading colons in subproject paths
	 * are stripped and remaining colons are converted to directory separators.</p>
	 */
	// ex input: "include 'client', 'server'\ninclude(':extensions:java')"
	// ex result: registers /workspace/client, /workspace/server, /workspace/extensions/java
	private void parseSettingsIncludes(String content) {
		int len = content.length();
		int i = 0;
		while (i <= len - 7) {
			// ex: "// include 'commented-out'" → skip entire line
			if (content.startsWith("//", i)) {
				while (i < len && content.charAt(i) != '\n') {
					i++;
				}
				continue;
			}
			// ex: "/* include 'commented-out' */" → skip block
			if (content.startsWith("/*", i)) {
				int end = content.indexOf("*/", i + 2);
				i = (end >= 0) ? end + 2 : len;
				continue;
			}
			// ex: match "include" but not "includeBuild"
			if (!content.startsWith("include", i)) {
				i++;
				continue;
			}
			int j = i + 7;
			if (j < len && Character.isLetterOrDigit(content.charAt(j))) {
				i = j;
				continue;
			}
			// ex: Groovy: include 'client', 'server'
			// ex: Kotlin: include("client")
			boolean hasParen = false;
			while (j < len && content.charAt(j) == ' ') {
				j++;
			}
			if (j < len && content.charAt(j) == '(') {
				hasParen = true;
				j++;
			}
			// Extract each quoted module name until end of statement
			while (j < len) {
				char c = content.charAt(j);
				if (hasParen && c == ')') {
					j++;
					break;
				}
				if (!hasParen && (c == '\n' || c == '\r')) {
					break;
				}
				if (c == '\'' || c == '"') {
					// ex: 'client' → extractIncludedModule registers /workspace/client
					j = extractIncludedModule(content, j, c);
				} else {
					j++;
				}
			}
			i = j;
		}
	}

	/**
	 * Extracts a single module path from a quoted string in the settings file.
	 *
	 * <p>Converts Gradle subproject notation ({@code :extensions:java})
	 * to a directory-relative path ({@code extensions/java}) and registers
	 * the module.</p>
	 *
	 * @param content the settings file content
	 * @param quotePos the position of the opening quote character
	 * @param quote the quote character ({@code '} or {@code "})
	 * @return the position after the closing quote
	 */
	// ex: content = "include ':extensions:java'", quotePos points to opening '
	// ex: strips leading colon → "extensions:java"
	// ex: converts colons to slashes → "extensions/java"
	// ex: registers /workspace/extensions/java
	private int extractIncludedModule(String content, int quotePos, char quote) {
		int len = content.length();
		int start = quotePos + 1;
		// ex: ':client' → skip leading colon → 'client'
		if (start < len && content.charAt(start) == ':') {
			start++;
		}
		StringBuilder moduleName = new StringBuilder();
		int s = start;
		while (s < len && content.charAt(s) != quote) {
			char c = content.charAt(s);
			// ex: ':extensions:java' → 'extensions/java'
			moduleName.append(c == ':' ? '/' : c);
			s++;
		}
		if (s < len && !moduleName.isEmpty()) {
			// ex: registers /workspace/extensions/java
			registerModule(workspacePath.resolve(moduleName.toString()));
		}
		return s + 1;
	}

	// ── Recursive fallback scan ─────────────────────────────────────────

	/**
	 * Recursively scans directories for Gradle build files.
	 *
	 * <p>Used as a fallback when no {@code settings.gradle} is found.
	 * Skips hidden directories, {@code build} output, and {@code buildSrc}
	 * to avoid false positives. Limited to {@value #MAX_SCAN_DEPTH} levels.</p>
	 */
	// ex: /workspace has no settings.gradle → scan recursively up to 3 levels deep
	// ex: finds /workspace/build.gradle, /workspace/client/build.gradle → registers both
	private void scanBuildFilesRecursive(Path dir, int depth) {
		if (depth > MAX_SCAN_DEPTH) {
			return;
		}
		// ex: /workspace/client/build.gradle exists → register /workspace/client
		if (hasBuildFile(dir)) {
			registerModule(dir);
		}
		try (var stream = Files.list(dir)) {
			// Skip .git, .gradle, build output, buildSrc
			stream.filter(Files::isDirectory)
					.filter(d -> !d.getFileName().toString().startsWith("."))
					.filter(d -> !d.getFileName().toString().equals("build"))
					.filter(d -> !d.getFileName().toString().equals("buildSrc"))
					.forEach(d -> scanBuildFilesRecursive(d, depth + 1));
		} catch (IOException e) {
			// Ignore unreadable directories
		}
	}
}
