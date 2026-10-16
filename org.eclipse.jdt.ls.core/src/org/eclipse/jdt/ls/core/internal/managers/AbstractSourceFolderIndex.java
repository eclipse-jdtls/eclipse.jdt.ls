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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.ls.core.internal.ISourceFolderIndex;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.mbt.MbtInfo;
import org.eclipse.jdt.ls.core.internal.mbt.MbtTargetInfo;

/**
 * Base implementation of {@link ISourceFolderIndex} with lazy module discovery.
 *
 * <p>Subclasses provide build-system-specific scanning and module discovery.
 * Common data structures (source folder mapping, dependency graph, import
 * tracking) and algorithms (transitive collection, reverse dependencies,
 * lazy walk-up) are handled here.</p>
 */
public abstract class AbstractSourceFolderIndex implements ISourceFolderIndex {

	protected final TreeMap<Path, String> sourceFolderToModule = new TreeMap<>();
	protected volatile Map<String, Set<String>> dependsOn = new LinkedHashMap<>();
	protected volatile Map<String, Set<String>> reverseDependsOn = new LinkedHashMap<>();
	protected volatile Map<String, Path> moduleToDir = new LinkedHashMap<>();
	protected final Set<String> importedModules = new LinkedHashSet<>();
	protected final Path workspacePath;

	protected AbstractSourceFolderIndex(Path workspacePath) {
		this.workspacePath = workspacePath;
	}

	@Override
	public String findTarget(Path filePath) {
		Path normalized = filePath.toAbsolutePath().normalize();
		Map.Entry<Path, String> floor = sourceFolderToModule.floorEntry(normalized);
		if (floor != null && normalized.startsWith(floor.getKey())) {
			return floor.getValue();
		}
		// Lazy: walk up to find build file
		Path current = normalized.getParent();
		while (current != null && current.startsWith(workspacePath)) {
			if (hasBuildFile(current)) {
				return discoverModule(current);
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Returns {@code true} if the directory contains a build file
	 * for this build system (e.g. pom.xml, build.gradle).
	 */
	protected abstract boolean hasBuildFile(Path dir);

	/**
	 * Parses the build file in the given directory to discover and register
	 * the module. Called lazily on first access.
	 *
	 * @return the module ID, or {@code null} if the directory is not a module
	 */
	protected abstract String discoverModule(Path dir);

	@Override
	public List<String> collectTargetsToImport(String targetId) {
		if (targetId == null || importedModules.contains(targetId) || !moduleToDir.containsKey(targetId)) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		collectTransitive(targetId, result, new LinkedHashSet<>());
		return result;
	}

	private void collectTransitive(String targetId, List<String> result, Set<String> visited) {
		if (targetId == null || !visited.add(targetId) || importedModules.contains(targetId)) {
			return;
		}
		if (!moduleToDir.containsKey(targetId)) {
			return;
		}
		Set<String> deps = dependsOn.get(targetId);
		if (deps != null) {
			for (String dep : deps) {
				if (moduleToDir.containsKey(dep)) {
					collectTransitive(dep, result, visited);
				}
			}
		}
		result.add(targetId);
	}

	@Override
	public List<String> collectReverseDependencies(String targetId) {
		if (targetId == null) {
			return Collections.emptyList();
		}
		Set<String> dependents = reverseDependsOn.get(targetId);
		if (dependents == null) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		for (String dep : dependents) {
			if (!importedModules.contains(dep)) {
				result.add(dep);
			}
		}
		return result;
	}

	@Override
	public boolean isImported(String targetId) {
		return importedModules.contains(targetId);
	}

	@Override
	public void markImported(List<String> targetIds) {
		importedModules.addAll(targetIds);
	}

	public Path getModuleDir(String moduleId) {
		return moduleToDir.get(moduleId);
	}

	public Set<String> getAllModuleIds() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(moduleToDir.keySet()));
	}

	public int getModuleCount() {
		return moduleToDir.size();
	}

	protected void registerModule(String moduleId, Path dir) {
		moduleToDir.put(moduleId, dir);
		registerSourceFolder(dir.resolve("src/main/java"), moduleId);
		registerSourceFolder(dir.resolve("src/main/resources"), moduleId);
		registerSourceFolder(dir.resolve("src/test/java"), moduleId);
		registerSourceFolder(dir.resolve("src/test/resources"), moduleId);
	}

	protected void registerSourceFolder(Path sourceFolder, String moduleId) {
		sourceFolderToModule.put(sourceFolder.toAbsolutePath().normalize(), moduleId);
	}

	protected void addDependency(String moduleId, String depId) {
		dependsOn.computeIfAbsent(moduleId, k -> new LinkedHashSet<>()).add(depId);
	}

	protected void buildReverseDependencies() {
		Set<String> knownModules = moduleToDir.keySet();
		for (Map.Entry<String, Set<String>> entry : dependsOn.entrySet()) {
			String moduleId = entry.getKey();
			for (String dep : entry.getValue()) {
				if (knownModules.contains(dep)) {
					reverseDependsOn.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(moduleId);
				}
			}
		}
	}

	private volatile boolean dependenciesLoaded;

	/**
	 * Populates the dependency graph from MBT extraction data.
	 * Called asynchronously from a background job after mbt.json is loaded.
	 *
	 * <p>Maps MBT target IDs (GAV-based) to directory-based module IDs by
	 * resolving each target's source folders to find the containing build
	 * file directory (pom.xml, build.gradle, etc.).</p>
	 */
	public void populateDependencies(MbtInfo mbtInfo) {
		Map<String, String> mbtIdToModuleId = new LinkedHashMap<>();
		Map<String, Path> newModules = new LinkedHashMap<>();
		Map<String, Set<String>> newDependsOn = new LinkedHashMap<>();

		for (Map.Entry<String, MbtTargetInfo> entry : mbtInfo.getNamespaces().entrySet()) {
			String mbtId = entry.getKey();
			String moduleId = resolveModuleDir(entry.getValue());
			if (moduleId != null) {
				mbtIdToModuleId.put(mbtId, moduleId);
				newModules.put(moduleId, Path.of(moduleId));
			}
		}

		for (Map.Entry<String, MbtTargetInfo> entry : mbtInfo.getNamespaces().entrySet()) {
			String moduleId = mbtIdToModuleId.get(entry.getKey());
			if (moduleId == null) {
				continue;
			}
			for (String dep : entry.getValue().getDependsOn()) {
				String depModuleId = mbtIdToModuleId.get(dep);
				if (depModuleId != null && !depModuleId.equals(moduleId)) {
					newDependsOn.computeIfAbsent(moduleId, k -> new LinkedHashSet<>()).add(depModuleId);
				}
			}
		}

		Map<String, Set<String>> newReverseDeps = new LinkedHashMap<>();
		for (Map.Entry<String, Set<String>> entry : newDependsOn.entrySet()) {
			for (String dep : entry.getValue()) {
				if (newModules.containsKey(dep)) {
					newReverseDeps.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(entry.getKey());
				}
			}
		}

		Map<String, Path> mergedModules = new LinkedHashMap<>(moduleToDir);
		for (Map.Entry<String, Path> entry : newModules.entrySet()) {
			mergedModules.putIfAbsent(entry.getKey(), entry.getValue());
		}
		Map<String, Set<String>> mergedDeps = new LinkedHashMap<>(dependsOn);
		mergedDeps.putAll(newDependsOn);
		Map<String, Set<String>> mergedReverse = new LinkedHashMap<>(reverseDependsOn);
		mergedReverse.putAll(newReverseDeps);

		moduleToDir = mergedModules;
		dependsOn = mergedDeps;
		reverseDependsOn = mergedReverse;
		dependenciesLoaded = true;
		JavaLanguageServerPlugin.logInfo("Dependency graph loaded: " + newModules.size()
				+ " modules, reverse deps available for references/rename");
	}

	public boolean isDependenciesLoaded() {
		return dependenciesLoaded;
	}

	private String resolveModuleDir(MbtTargetInfo target) {
		for (String source : target.getSources()) {
			Path sourcePath = workspacePath.resolve(source).normalize();
			Path current = sourcePath;
			while (current != null && current.startsWith(workspacePath)) {
				if (hasBuildFile(current)) {
					return current.toAbsolutePath().normalize().toString();
				}
				current = current.getParent();
			}
		}
		return null;
	}

	/**
	 * Converts a file URI to a path and delegates to {@link #findTarget(Path)}.
	 */
	public String findTargetForUri(String uri) {
		IPath filePath = ResourceUtils.canonicalFilePathFromURI(uri);
		if (filePath == null) {
			return null;
		}
		return findTarget(Path.of(filePath.toOSString()));
	}
}
