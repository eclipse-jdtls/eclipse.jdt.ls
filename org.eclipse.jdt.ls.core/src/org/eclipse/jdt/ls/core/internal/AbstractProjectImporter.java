/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.internal.managers.ondemand.IModuleIndex;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

public abstract class AbstractProjectImporter implements IProjectImporter {

	protected File rootFolder;
	protected Collection<Path> directories;

	/**
	 * The module index for on-demand mode. {@code null} in full mode
	 * or before the first {@code didOpen} triggers lazy creation via
	 * {@link #createModuleIndex()}.
	 */
	protected volatile IModuleIndex moduleIndex;

	@Override
	public void initialize(File rootFolder) {
		if (!Objects.equals(this.rootFolder, rootFolder)) {
			reset();
		}
		this.rootFolder = rootFolder;
	}

	@Override
	public abstract boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	@Override
	public boolean isResolved(File file) throws OperationCanceledException, CoreException {
		if (directories == null) {
			return false;
		}
		// if input is a directory (usually the root folder),
		// check if the importer has imported it.
		if (file.isDirectory()) {
			return directories.contains(file.toPath());
		}

		// if the input is a file, check if the parent directory is imported.
		return directories.stream().anyMatch((directory) -> {
			return file.toPath().getParent().equals(directory);
		});
	};

	@Override
	public abstract void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	@Override
	public void reset() {
		moduleIndex = null;
	}

	// ── On-demand mode ──────────────────────────────────────────────────

	/**
	 * Creates the module index for on-demand mode.
	 *
	 * <p>Subclasses override this to return their build-system-specific
	 * index implementation (e.g. {@link org.eclipse.jdt.ls.core.internal.managers.ondemand.MavenModuleIndex},
	 * {@link org.eclipse.jdt.ls.core.internal.managers.ondemand.GradleModuleIndex}).</p>
	 *
	 * @return the module index, or {@code null} if the subclass
	 *         does not support on-demand mode
	 */
	protected IModuleIndex createModuleIndex() {
		return null;
	}

	/**
	 * Imports the module containing the given file URI on-demand.
	 *
	 * <p>The module index is created lazily on the first call.
	 * It walks up from the file to find the nearest build file
	 * ({@code pom.xml}, {@code build.gradle}). If the file does not belong
	 * to this build system, the index returns {@code null} and the manager
	 * tries the next importer.</p>
	 *
	 * <h3>Use cases</h3>
	 * <ul>
	 *   <li><b>First file opened in a Maven module</b> — user opens
	 *       {@code myapp/core/src/main/java/Foo.java}. The index walks up
	 *       from {@code Foo.java}, finds {@code myapp/core/pom.xml}, resolves
	 *       the module path. Since this module has not been imported yet,
	 *       M2E imports it and returns the Eclipse project.</li>
	 *   <li><b>Second file in the same module</b> — user opens
	 *       {@code myapp/core/src/main/java/Bar.java}. The index resolves the
	 *       same module path, which is already marked as imported → returns
	 *       immediately (no duplicate import).</li>
	 *   <li><b>File in a Gradle module</b> — user opens a file under a
	 *       {@code build.gradle} project. The Maven importer's index returns
	 *       {@code null} (no {@code pom.xml} found), so
	 *       {@link org.eclipse.jdt.ls.core.internal.managers.ondemand.OnDemandImportManager}
	 *       tries the Gradle importer, which handles it.</li>
	 *   <li><b>File outside any build system</b> — all importers return
	 *       empty, the file is handled as a standalone file by the
	 *       invisible project importer.</li>
	 * </ul>
	 *
	 * @param uri the file URI that triggered the import
	 * @param monitor progress monitor
	 * @return the list of imported projects (empty if this importer
	 *         does not handle the file)
	 */
	@Override
	public List<IProject> importOnDemand(String uri, IProgressMonitor monitor) throws CoreException {
		// ex: uri = "file:///workspace/myapp/core/src/main/java/Foo.java"

		// Get or lazily create the module index
		// ex: MavenProjectImporter → creates MavenModuleIndex
		// ex: InvisibleProjectImporter → returns null (no on-demand support)
		IModuleIndex index = getOrCreateIndex();
		if (index == null) {
			return List.of();
		}

		// Walk up from the file to find the nearest build file
		// ex: .../core/src/main/java/Foo.java → walks up → finds .../core/pom.xml
		// ex: modulePath = /workspace/myapp/core
		Path modulePath = index.findModulePath(uri);
		if (modulePath == null) {
			return List.of();
		}

		// Resolve which modules to import (idempotent: returns [] if already imported)
		// ex: first call → [/workspace/myapp/core]
		// ex: second call for same module → [] (already imported)
		List<Path> toImport = index.resolveModulesToImport(modulePath);
		if (toImport.isEmpty()) {
			return List.of();
		}

		// Import each module: try reopening a closed project first (~450ms),
		// fall back to full import via M2E/Buildship (~6s)
		long start = System.currentTimeMillis();
		SubMonitor subMonitor = SubMonitor.convert(monitor, toImport.size() * 2);
		List<IProject> result = new ArrayList<>();
		for (Path moduleDir : toImport) {
			if (subMonitor.isCanceled()) {
				index.markNotImported(moduleDir);
				continue;
			}
			subMonitor.subTask("Importing module: " + moduleDir);
			try {
				// ex: previous session had myapp-core open → reopen it (fast, preserves JDT cache)
				IProject reopened = tryReopenClosedProject(moduleDir, subMonitor.split(1));
				if (reopened != null) {
					result.add(reopened);
				} else {
					// ex: first time ever → full M2E import of /workspace/myapp/core/pom.xml
					List<IProject> imported = importModule(moduleDir, subMonitor.split(1));
					if (imported.isEmpty()) {
						index.markNotImported(moduleDir);
					} else {
						result.addAll(imported);
					}
				}
			} catch (CoreException e) {
				index.markNotImported(moduleDir);
				JavaLanguageServerPlugin.logException("Failed to import module " + moduleDir, e);
			} catch (RuntimeException e) {
				index.markNotImported(moduleDir);
				throw e;
			}
		}
		JavaLanguageServerPlugin.logInfo("On-demand import [" + getClass().getSimpleName() + "]: "
				+ toImport.size() + " module(s), " + result.size() + " project(s) in "
				+ (System.currentTimeMillis() - start) + "ms");
		return result;
	}

	/**
	 * Returns the module index, creating it lazily on first access.
	 * Thread-safe via double-checked locking.
	 */
	private IModuleIndex getOrCreateIndex() {
		IModuleIndex index = moduleIndex;
		if (index == null) {
			synchronized (this) {
				index = moduleIndex;
				if (index == null) {
					index = createModuleIndex();
					moduleIndex = index;
					if (index != null) {
						JavaLanguageServerPlugin.logInfo("On-demand index created: "
								+ getClass().getSimpleName()
								+ " (" + index.getModuleCount() + " modules indexed)");
					}
				}
			}
		}
		return index;
	}

	/**
	 * Imports a single module into the Eclipse workspace.
	 *
	 * <p>Each build system provides its own implementation:
	 * Maven delegates to M2E, Gradle to Buildship.</p>
	 *
	 * @param modulePath the module directory to import
	 * @param monitor progress monitor
	 * @return the imported Eclipse projects
	 * @throws CoreException if the import fails
	 */
	protected List<IProject> importModule(Path modulePath, IProgressMonitor monitor) throws CoreException {
		return List.of();
	}

	/**
	 * Tries to find a closed project at the module's location and reopen it.
	 * Reopening preserves JDT index cache from the previous session.
	 *
	 * @return the reopened project, or {@code null} if no matching closed project exists
	 */
	private IProject tryReopenClosedProject(Path modulePath, IProgressMonitor monitor) {
		Path normalizedModuleDir = modulePath.toAbsolutePath().normalize();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (project.isOpen() || project.getLocation() == null) {
				continue;
			}
			Path projectPath = project.getLocation().toFile().toPath().toAbsolutePath().normalize();
			if (projectPath.equals(normalizedModuleDir)) {
				try {
					project.open(monitor);
					JavaLanguageServerPlugin.logInfo("On-demand reopen: " + project.getName());
					return project;
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Failed to reopen project " + project.getName(), e);
				}
			}
		}
		return null;
	}

	// ── Utilities ───────────────────────────────────────────────────────

	/**
	 * Returns the workspace root directory as a normalized absolute path.
	 */
	protected Path getWorkspacePath() {
		return rootFolder.toPath().toAbsolutePath().normalize();
	}

	protected static Preferences getPreferences() {
		return (JavaLanguageServerPlugin.getPreferencesManager() == null || JavaLanguageServerPlugin.getPreferencesManager().getPreferences() == null) ? new Preferences() : JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
	}

	/**
	 * Finds the project base paths from project configuration files.
	 *
	 * @param projectConfigurations the collection of project configurations
	 * @param names the build file names to look for (e.g. "pom.xml")
	 * @param includeNested whether to include nested projects
	 * @return the matching project paths
	 */
	protected Collection<Path> findProjectPathByConfigurationName(Collection<IPath> projectConfigurations, List<String> names, boolean includeNested) {
		Set<Path> set = new HashSet<>();
		for (IPath path : projectConfigurations) {
			boolean matched = names.stream().anyMatch((name -> {
				return path.lastSegment().endsWith(name);
			}));

			if (matched) {
				set.add(path.removeLastSegments(1).toFile().toPath());
			}
		}

		List<Path> filteredPaths = set.stream().sorted().collect(Collectors.toList());

		if (includeNested) {
			return filteredPaths;
		}

		return eliminateNestedPaths(filteredPaths);
	}

	protected List<Path> eliminateNestedPaths(List<Path> filteredPaths) {
		Path parentDir = null;
		List<Path> result = new LinkedList<>();
		for (Path path : filteredPaths) {
			if (parentDir == null) {
				result.add(path);
				parentDir = path;
			} else if (path.startsWith(parentDir)) {
				continue;
			} else {
				result.add(path);
				parentDir = path;
			}
		}
		return result;
	}
}
