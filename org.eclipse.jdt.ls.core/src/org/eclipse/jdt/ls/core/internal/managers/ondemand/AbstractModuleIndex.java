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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Base implementation of {@link IModuleIndex} with lazy module discovery.
 *
 * <p>Modules are discovered lazily on first access: when {@link #findModulePath(Path)}
 * is called, the index walks up the directory tree from the file's location
 * to find the nearest build file ({@code pom.xml}, {@code build.gradle}, etc.).
 * Once discovered, the module directory is registered in {@link #knownModules}
 * for fast subsequent lookups.</p>
 *
 * <p>Subclasses provide build-system-specific behavior via
 * {@link #hasBuildFile(Path)}, which checks whether a directory contains
 * a build file.</p>
 *
 * <p>Thread safety: this class is accessed from multiple threads (LSP request
 * handlers for {@code didOpen}, initialization, shutdown). The {@link #knownModules}
 * and {@link #importedModules} fields use synchronized collections.</p>
 */
public abstract class AbstractModuleIndex implements IModuleIndex {

	/**
	 * Known module directories (absolute, normalized).
	 * Populated lazily by {@link #findModulePath(Path)}.
	 */
	protected final Set<Path> knownModules = Collections.synchronizedSet(new LinkedHashSet<>());

	/**
	 * Tracks which modules have already been imported into the Eclipse workspace.
	 * Prevents duplicate imports when the same module is accessed multiple times.
	 */
	protected final Set<Path> importedModules = Collections.synchronizedSet(new LinkedHashSet<>());

	/**
	 * The workspace root directory. Used to ensure walk-up discovery stays
	 * within the workspace boundaries.
	 */
	protected final Path workspacePath;

	/**
	 * Creates a new index for the given workspace root.
	 *
	 * @param workspacePath the workspace root directory
	 */
	protected AbstractModuleIndex(Path workspacePath) {
		this.workspacePath = workspacePath;
	}

	@Override
	public Path findModulePath(Path filePath) {
		// ex: /workspace/module-a/src/main/java/Foo.java
		Path current = filePath.toAbsolutePath().normalize();
		// Start from the directory, not the file itself
		// ex: /workspace/module-a/src/main/java
		if (Files.isRegularFile(current)) {
			current = current.getParent();
		}
		// Walk up until we find a build file or leave the workspace
		// ex: .../src/main/java → .../src/main → .../src → .../module-a (pom.xml found)
		while (current != null && current.startsWith(workspacePath)) {
			if (hasBuildFile(current)) {
				// ex: /workspace/module-a contains pom.xml → register and return
				registerModule(current);
				return current;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Returns {@code true} if the given directory contains a build file
	 * recognized by this build system.
	 *
	 * <p>Examples:</p>
	 * <ul>
	 *   <li>Maven: {@code pom.xml}</li>
	 *   <li>Gradle: {@code build.gradle} or {@code build.gradle.kts}</li>
	 * </ul>
	 *
	 * @param dir the directory to check
	 * @return {@code true} if a build file exists in the directory
	 */
	protected abstract boolean hasBuildFile(Path dir);

	@Override
	public List<Path> resolveModulesToImport(Path modulePath) {
		// ex: modulePath = /workspace/module-a
		if (modulePath == null) {
			return Collections.emptyList();
		}
		Path normalized = normalize(modulePath);
		if (!knownModules.contains(normalized)) {
			return Collections.emptyList();
		}
		// Atomic add: first call returns true → import needed
		// ex: importedModules.add(/workspace/module-a) → true
		// Second call for same module returns false → already imported
		// ex: importedModules.add(/workspace/module-a) → false
		if (!importedModules.add(normalized)) {
			return Collections.emptyList();
		}
		// ex: returns [/workspace/module-a]
		List<Path> result = new ArrayList<>();
		result.add(normalized);
		return result;
	}

	@Override
	public boolean isImported(Path modulePath) {
		return importedModules.contains(normalize(modulePath));
	}

	@Override
	public void markNotImported(Path modulePath) {
		importedModules.remove(normalize(modulePath));
	}

	// ── Module registry queries ─────────────────────────────────────────

	@Override
	public int getModuleCount() {
		return knownModules.size();
	}

	// ── Registration helpers ────────────────────────────────────────────

	/**
	 * Registers a module in the index.
	 *
	 * <p>The directory is normalized on registration so that all path
	 * comparisons work reliably.</p>
	 *
	 * @param dir the module's root directory
	 */
	protected void registerModule(Path dir) {
		knownModules.add(normalize(dir));
	}

	private static Path normalize(Path path) {
		return path.toAbsolutePath().normalize();
	}

}
