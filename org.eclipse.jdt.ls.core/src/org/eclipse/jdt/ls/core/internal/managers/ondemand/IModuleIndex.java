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

import java.nio.file.Path;
import java.util.List;

import org.eclipse.jdt.ls.core.internal.ResourceUtils;

/**
 * Maps file paths to their containing build module for on-demand project import.
 *
 * <p>When a file is opened in on-demand mode, this index determines which
 * module the file belongs to and which modules need to be imported.
 * Each build system (Maven, Gradle) provides its own implementation.</p>
 *
 * <p>Module discovery is lazy: modules are discovered on first access by
 * walking up the directory tree to find the nearest build file
 * ({@code pom.xml}, {@code build.gradle}, etc.).</p>
 */
public interface IModuleIndex {

	/**
	 * Finds the module directory for the given file path.
	 *
	 * <p>Walks up the directory tree from the file's location to find
	 * the nearest build file and returns the absolute path of the directory
	 * containing that build file.</p>
	 *
	 * <p>Examples:</p>
	 * <pre>
	 * // Maven: file at /workspace/module-a/src/main/java/Foo.java
	 * // walks up to /workspace/module-a/pom.xml
	 * index.findModulePath(Path.of("/workspace/module-a/src/main/java/Foo.java"))
	 *   // returns Path.of("/workspace/module-a")
	 *
	 * // Gradle: file at /workspace/client/src/main/java/Client.java
	 * // walks up to /workspace/client/build.gradle
	 * index.findModulePath(Path.of("/workspace/client/src/main/java/Client.java"))
	 *   // returns Path.of("/workspace/client")
	 * </pre>
	 *
	 * @param filePath the absolute path to a source file
	 * @return the module directory where the build file is stored
	 *         (e.g. {@code /workspace/module-a} where {@code pom.xml} is stored for Maven,
	 *         {@code /workspace/client} where {@code build.gradle} is stored for Gradle),
	 *         or {@code null} if the file is not under any known build module in the workspace
	 */
	Path findModulePath(Path filePath);

	/**
	 * Resolves the list of modules that should be imported when the given module
	 * is first accessed.
	 *
	 * <p>In this initial implementation, returns only the requested module.
	 * Future enhancements (dependency graph via {@code dependsOn}) will include
	 * transitive dependencies to ensure compilation succeeds.</p>
	 *
	 * <p>This method is idempotent: calling it a second time for the same module
	 * returns an empty list (the module is already marked as imported).</p>
	 *
	 * <p>Examples:</p>
	 * <pre>
	 * // Maven: module at /workspace/module-a (contains pom.xml)
	 * index.resolveModulesToImport(Path.of("/workspace/module-a"))
	 *   // returns [Path.of("/workspace/module-a")]       (first call)
	 *   // returns []                                      (second call, already imported)
	 *
	 * // Gradle: module at /workspace/client (contains build.gradle)
	 * index.resolveModulesToImport(Path.of("/workspace/client"))
	 *   // returns [Path.of("/workspace/client")]          (first call)
	 *   // returns []                                      (second call, already imported)
	 * </pre>
	 *
	 * @param modulePath the module directory to import
	 * @return the list of module directories to import, in dependency order;
	 *         empty if the module is already imported or unknown
	 */
	List<Path> resolveModulesToImport(Path modulePath);

	/**
	 * Returns whether the given module has already been imported into
	 * the Eclipse workspace.
	 *
	 * <p>A module is considered imported after {@link #resolveModulesToImport(Path)}
	 * has been called for it.</p>
	 *
	 * <p>Examples:</p>
	 * <pre>
	 * // Maven: module at /workspace/module-a
	 * index.isImported(Path.of("/workspace/module-a"))  // false (not yet imported)
	 * index.resolveModulesToImport(Path.of("/workspace/module-a"));
	 * index.isImported(Path.of("/workspace/module-a"))  // true
	 *
	 * // Gradle: module at /workspace/client
	 * index.isImported(Path.of("/workspace/client"))    // false
	 * index.resolveModulesToImport(Path.of("/workspace/client"));
	 * index.isImported(Path.of("/workspace/client"))    // true
	 * </pre>
	 *
	 * @param modulePath the module directory to check
	 * @return {@code true} if the module is already imported
	 */
	boolean isImported(Path modulePath);

	/**
	 * Clears the imported marker for the given module so it can be retried.
	 *
	 * <p>Called when an import attempt fails or is cancelled, allowing
	 * the next {@code didOpen} for a file in this module to trigger
	 * a fresh import.</p>
	 *
	 * @param modulePath the module directory to un-mark
	 */
	void markNotImported(Path modulePath);

	/**
	 * Returns the number of known modules (discovered lazily).
	 *
	 * <p>Examples:</p>
	 * <pre>
	 * // Maven: single-module project at /workspace/myapp
	 * index.getModuleCount()                              // 0 (no file opened yet)
	 * index.findModulePath(Path.of("/workspace/myapp/src/main/java/Foo.java"));
	 * index.getModuleCount()                              // 1 (module discovered lazily)
	 *
	 * // Gradle: multi-module project, lazy discovery like Maven
	 * index.getModuleCount()                              // 0 (no file opened yet)
	 * index.findModulePath(Path.of("/workspace/client/src/main/java/Bar.java"));
	 * index.getModuleCount()                              // 1 (module discovered lazily)
	 * </pre>
	 *
	 * @return the number of known modules
	 */
	int getModuleCount();

	/**
	 * Finds the module directory for the given file URI.
	 *
	 * <p>Converts the URI to a {@link Path} and delegates to
	 * {@link #findModulePath(Path)}.</p>
	 *
	 * <p>Examples:</p>
	 * <pre>
	 * // Maven: URI for /workspace/module-a/src/main/java/Foo.java
	 * index.findModulePath("file:///workspace/module-a/src/main/java/Foo.java")
	 *   // returns Path.of("/workspace/module-a")
	 *
	 * // Gradle: URI for /workspace/client/src/main/java/Client.java
	 * index.findModulePath("file:///workspace/client/src/main/java/Client.java")
	 *   // returns Path.of("/workspace/client")
	 * </pre>
	 *
	 * @param uri the file URI
	 * @return the module directory where the build file is stored
	 *         (e.g. {@code /workspace/module-a} where {@code pom.xml} is stored for Maven,
	 *         {@code /workspace/client} where {@code build.gradle} is stored for Gradle),
	 *         or {@code null} if the file is not under any known build module in the workspace
	 */
	default Path findModulePath(String uri) {
		org.eclipse.core.runtime.IPath filePath = ResourceUtils.canonicalFilePathFromURI(uri);
		if (filePath == null) {
			return null;
		}
		return findModulePath(Path.of(filePath.toOSString()));
	}
}
