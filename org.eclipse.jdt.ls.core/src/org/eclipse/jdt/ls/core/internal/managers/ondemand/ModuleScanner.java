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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.BuildSupportManager;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;

/**
 * Scans the workspace to discover all build modules and collect minimal info.
 *
 * <p>The scan is designed to run in a background {@code Job}. Results are
 * stored in a thread-safe list that can be queried at any time — partial
 * results are available while the scan is still running.</p>
 */
public class ModuleScanner {

	private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
		"target", "build", "node_modules", "bin"
	);

	private static final String[] STANDARD_SOURCE_FOLDERS = {
		"src/main/java", "src/test/java"
	};

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

	/**
	 * Recursively scans a directory tree, delegating module detection
	 * to registered {@link IBuildSupport} implementations.
	 *
	 * @param dir the directory to scan
	 */
	private void scanDirectory(Path dir) {
		for (IBuildSupport buildSupport : BuildSupportManager.obtainBuildSupports()) {
			ModuleInfo module = buildSupport.scanModule(dir);
			if (module != null) {
				modules.add(module);
				break;
			}
		}

		File[] children = dir.toFile().listFiles(File::isDirectory);
		if (children != null) {
			for (File child : children) {
				if (!isExcludedDirectory(child.getName())) {
					scanDirectory(child.toPath());
				}
			}
		}
	}

	/**
	 * Returns whether the given directory name should be skipped during scanning.
	 *
	 * @param name the directory name
	 * @return {@code true} if the directory should be excluded
	 */
	private static boolean isExcludedDirectory(String name) {
		return name.startsWith(".") || EXCLUDED_DIRECTORIES.contains(name);
	}

	/**
	 * Returns the standard source folders ({@code src/main/java}, {@code src/test/java})
	 * that exist on disk under the given module directory.
	 *
	 * @param moduleDir the module root directory
	 * @return a mutable list of existing source folder paths (absolute, normalized)
	 */
	public static List<Path> detectSourceFolders(Path moduleDir) {
		List<Path> found = new ArrayList<>();
		for (String srcFolder : STANDARD_SOURCE_FOLDERS) {
			Path candidate = moduleDir.resolve(srcFolder);
			if (Files.isDirectory(candidate)) {
				found.add(candidate.toAbsolutePath().normalize());
			}
		}
		return found;
	}

	/**
	 * Resolves {@code dirPath} against {@code moduleDir} and adds it to
	 * {@code result} if the resolved path is an existing directory and
	 * not already present in the list.
	 *
	 * @param moduleDir the module root used to resolve relative paths
	 * @param dirPath   the directory path (absolute or relative)
	 * @param result    the list to append to
	 */
	public static void addIfDirectory(Path moduleDir, String dirPath, List<Path> result) {
		Path resolved = Path.of(dirPath);
		if (!resolved.isAbsolute()) {
			resolved = moduleDir.resolve(resolved);
		}
		resolved = resolved.toAbsolutePath().normalize();
		if (Files.isDirectory(resolved) && !result.contains(resolved)) {
			result.add(resolved);
		}
	}

	/**
	 * Returns the modules discovered so far. The list may grow while
	 * the scan is still running.
	 *
	 * @return an unmodifiable view of discovered modules
	 */
	public List<ModuleInfo> getModules() {
		return modules;
	}

	/**
	 * Returns whether the full workspace scan has finished.
	 *
	 * @return {@code true} once {@link #scan(Path)} has completed
	 */
	public boolean isScanComplete() {
		return scanComplete;
	}
}
