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
package org.eclipse.jdt.ls.core.internal.mbt;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Maps source folder paths to their mbt.json target IDs for on-demand module
 * loading.
 *
 * <p>Built once at {@code initialize} from a lightweight parse of {@code mbt.json}.
 * When a file is opened ({@code textDocument/didOpen}), the index is consulted
 * to determine which target the file belongs to and which transitive
 * {@code dependsOn} targets should also be loaded.</p>
 */
public class MbtSourceFolderIndex implements org.eclipse.jdt.ls.core.internal.ISourceFolderIndex {

	private final TreeMap<Path, String> sourceFolderToTarget;
	private final Map<String, Set<String>> reverseDependsOn;
	private final MbtInfo mbtInfo;
	private final Path workspacePath;
	private final Set<String> importedTargets = new LinkedHashSet<>();
	private final Set<String> libraryTargets = new LinkedHashSet<>();

	/**
	 * Builds the index from the given {@link MbtInfo}.
	 *
	 * @param mbtInfo the parsed mbt.json
	 * @param workspacePath the workspace root used to resolve relative paths
	 */
	public MbtSourceFolderIndex(MbtInfo mbtInfo, Path workspacePath) {
		this.mbtInfo = mbtInfo;
		this.workspacePath = workspacePath;
		this.sourceFolderToTarget = new TreeMap<>();
		this.reverseDependsOn = new LinkedHashMap<>();
		for (Map.Entry<String, MbtTargetInfo> entry : mbtInfo.getNamespaces().entrySet()) {
			String targetId = entry.getKey();
			MbtTargetInfo target = entry.getValue();
			for (String source : target.getSources()) {
				Path sourcePath = workspacePath.resolve(source).normalize();
				sourceFolderToTarget.put(sourcePath, targetId);
			}
			for (String dep : target.getDependsOn()) {
				reverseDependsOn.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(targetId);
			}
		}
	}

	/**
	 * Finds the target ID for the given file path.
	 *
	 * @param filePath the absolute path to a source file
	 * @return the target ID, or {@code null} if the file is not under any known
	 *         source folder
	 */
	@Override
	public String findTarget(Path filePath) {
		Path normalized = filePath.toAbsolutePath().normalize();
		Map.Entry<Path, String> floor = sourceFolderToTarget.floorEntry(normalized);
		if (floor != null && normalized.startsWith(floor.getKey())) {
			return floor.getValue();
		}
		return null;
	}

	/**
	 * Collects the given target and all its transitive {@code dependsOn} targets
	 * that have not yet been imported.
	 *
	 * @param targetId the starting target ID
	 * @return the list of target IDs to import (including the given one), in
	 *         dependency order; empty if all are already imported
	 */
	@Override
	public List<String> collectTargetsToImport(String targetId) {
		if (targetId == null || importedTargets.contains(targetId)) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		collectTransitive(targetId, result, new LinkedHashSet<>());
		return result;
	}

	private void collectTransitive(String targetId, List<String> result, Set<String> visited) {
		if (targetId == null || !visited.add(targetId)
				|| importedTargets.contains(targetId) || libraryTargets.contains(targetId)) {
			return;
		}
		MbtTargetInfo target = mbtInfo.getNamespaces().get(targetId);
		if (target != null) {
			for (String dep : target.getDependsOn()) {
				collectTransitive(dep, result, visited);
			}
		}
		result.add(targetId);
	}

	/**
	 * Collects all targets that transitively depend on the given target
	 * (reverse dependsOn) and have not yet been imported.
	 *
	 * @param targetId the target whose dependents to find
	 * @return the list of not-yet-imported dependent target IDs
	 */
	@Override
	public List<String> collectReverseDependencies(String targetId) {
		if (targetId == null) {
			return Collections.emptyList();
		}
		// Direct reverse deps only — transitive would pull in the entire repo for core modules
		Set<String> dependents = reverseDependsOn.get(targetId);
		if (dependents == null) {
			return Collections.emptyList();
		}
		// Include library targets: they may need promotion (Rename) or library-mode search (Find References)
		List<String> result = new ArrayList<>();
		for (String dep : dependents) {
			if (!importedTargets.contains(dep)) {
				result.add(dep);
			}
		}
		return result;
	}

	/**
	 * Marks the given targets as imported so they are not imported again.
	 *
	 * @param targetIds the target IDs to mark as imported
	 */
	@Override
	public void markImported(List<String> targetIds) {
		importedTargets.addAll(targetIds);
	}

	/**
	 * Returns whether the given target has already been imported
	 * (as a project or as a library).
	 */
	@Override
	public boolean isImported(String targetId) {
		return importedTargets.contains(targetId) || libraryTargets.contains(targetId);
	}

	/**
	 * Returns whether the given target was imported as a project (with IProject + JDT build).
	 */
	public boolean isImportedAsProject(String targetId) {
		return importedTargets.contains(targetId);
	}

	/**
	 * Returns whether the given target was imported as a library
	 * (CPE_LIBRARY from target/classes, no IProject created).
	 */
	public boolean isImportedAsLibrary(String targetId) {
		return libraryTargets.contains(targetId);
	}

	/**
	 * Marks the given targets as imported via library references
	 * (CPE_LIBRARY from target/classes). These targets can later be
	 * promoted to full projects when the user opens a file from them.
	 */
	public void markImportedAsLibrary(List<String> targetIds) {
		libraryTargets.addAll(targetIds);
	}

	/**
	 * Promotes a library target to a full project. Called when the user
	 * opens a file from a dependency that was previously imported as a library.
	 */
	public void promoteToProject(String targetId) {
		libraryTargets.remove(targetId);
		importedTargets.add(targetId);
	}

	/**
	 * Returns all target IDs that directly depend on the given target.
	 */
	public Set<String> getDirectDependents(String targetId) {
		Set<String> dependents = reverseDependsOn.get(targetId);
		return dependents != null ? Collections.unmodifiableSet(dependents) : Collections.emptySet();
	}

	/**
	 * Returns the parsed {@link MbtInfo}.
	 */
	public MbtInfo getMbtInfo() {
		return mbtInfo;
	}

	/**
	 * Returns the workspace root path.
	 */
	public Path getWorkspacePath() {
		return workspacePath;
	}

	/**
	 * Returns the set of targets imported as projects.
	 */
	public Set<String> getImportedTargets() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(importedTargets));
	}

	/**
	 * Returns the set of targets imported as libraries.
	 */
	public Set<String> getLibraryTargets() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(libraryTargets));
	}

	/**
	 * Returns all target IDs in the index.
	 */
	public Set<String> getAllTargetIds() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(mbtInfo.getNamespaces().keySet()));
	}

	/**
	 * Dynamically registers a new target in the index. Used when per-module
	 * MBT generation adds targets on the fly during on-demand import.
	 */
	public void addTarget(String targetId, MbtTargetInfo target, Path workspacePath) {
		for (String source : target.getSources()) {
			Path sourcePath = workspacePath.resolve(source).normalize();
			sourceFolderToTarget.put(sourcePath, targetId);
		}
		for (String dep : target.getDependsOn()) {
			reverseDependsOn.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(targetId);
		}
	}
}
