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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.internal.managers.AbstractSourceFolderIndex;
import org.eclipse.jdt.ls.core.internal.managers.OnDemandImportManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

public abstract class AbstractProjectImporter implements IProjectImporter {

	protected File rootFolder;
	protected Collection<java.nio.file.Path> directories;
	protected AbstractSourceFolderIndex sourceFolderIndex;

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
		if (sourceFolderIndex != null) {
			return true;
		}
		if (directories == null) {
			return false;
		}
		if (file.isDirectory()) {
			return directories.contains(file.toPath());
		}
		return directories.stream().anyMatch((directory) -> {
			return file.toPath().getParent().equals(directory);
		});
	};

	@Override
	public abstract void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	@Override
	public void reset() {
		if (sourceFolderIndex != null) {
			OnDemandImportManager.getInstance().deactivate();
			sourceFolderIndex = null;
		}
	}

	// ── On-demand mode ──────────────────────────────────────────────────

	protected boolean isOnDemandMode() {
		Preferences prefs = getPreferences();
		return "ondemand".equals(prefs.getImportMode());
	}

	/**
	 * Sets up on-demand mode: creates the source folder index and activates
	 * the {@link OnDemandImportManager}.
	 * Called from {@code importToWorkspace} when on-demand mode is enabled.
	 *
	 * <p>Stale projects from a crashed previous session are closed earlier,
	 * in {@link OnDemandImportManager#closeStaleProjects}, before
	 * {@code initializeProjects()} runs.</p>
	 */
	protected void setupOnDemand(IProgressMonitor monitor) throws CoreException {
		sourceFolderIndex = createSourceFolderIndex();
		java.nio.file.Path workspacePath = rootFolder.toPath().toAbsolutePath().normalize();
		OnDemandImportManager.getInstance().activate(this, sourceFolderIndex, workspacePath);
		JavaLanguageServerPlugin.logInfo("On-demand mode activated: " + getClass().getSimpleName()
				+ " (" + sourceFolderIndex.getModuleCount() + " modules indexed)");
	}

	/**
	 * Creates the source folder index for on-demand mode.
	 * Subclasses return their build-system-specific index.
	 */
	protected AbstractSourceFolderIndex createSourceFolderIndex() {
		return null;
	}

	@Override
	public List<IProject> importOnDemand(String uri, IProgressMonitor monitor) throws CoreException {
		if (sourceFolderIndex == null) {
			return List.of();
		}
		String targetId = sourceFolderIndex.findTargetForUri(uri);
		if (targetId == null || sourceFolderIndex.isImported(targetId)) {
			return List.of();
		}
		List<String> toImport = sourceFolderIndex.collectTargetsToImport(targetId);
		if (toImport.isEmpty()) {
			return List.of();
		}
		JavaLanguageServerPlugin.logInfo("On-demand import [" + getClass().getSimpleName() + "]: "
				+ toImport.size() + " module(s) triggered by " + targetId);
		SubMonitor subMonitor = SubMonitor.convert(monitor, toImport.size());
		List<IProject> result = new ArrayList<>();
		for (String id : toImport) {
			if (subMonitor.isCanceled()) {
				break;
			}
			subMonitor.subTask("Importing module: " + id);
			result.addAll(importModule(id, subMonitor.split(1)));
		}
		sourceFolderIndex.markImported(toImport);
		JavaLanguageServerPlugin.logInfo("On-demand import complete: " + result.size() + " project(s)");
		return result;
	}

	@Override
	public boolean importReverseDependencies(String uri, boolean forModification, IProgressMonitor monitor) throws CoreException {
		if (sourceFolderIndex == null) {
			return false;
		}
		String targetId = sourceFolderIndex.findTargetForUri(uri);
		if (targetId == null) {
			return false;
		}
		List<String> reverseDeps = sourceFolderIndex.collectReverseDependencies(targetId);
		if (reverseDeps.isEmpty()) {
			return false;
		}
		Set<String> allTargets = new LinkedHashSet<>();
		for (String depId : reverseDeps) {
			allTargets.addAll(sourceFolderIndex.collectTargetsToImport(depId));
		}
		if (allTargets.isEmpty()) {
			return false;
		}
		JavaLanguageServerPlugin.logInfo("Reverse dep import [" + getClass().getSimpleName() + "]: "
				+ reverseDeps.size() + " deps (" + allTargets.size() + " targets) for " + targetId);
		SubMonitor subMonitor = SubMonitor.convert(monitor, allTargets.size());
		for (String id : allTargets) {
			if (subMonitor.isCanceled()) {
				break;
			}
			subMonitor.subTask("Importing reverse dep: " + id);
			importModule(id, subMonitor.split(1));
		}
		sourceFolderIndex.markImported(new ArrayList<>(allTargets));
		return true;
	}

	/**
	 * Imports a single module. Each build system provides its own
	 * implementation (M2E, Buildship, MbtBuildSupport).
	 *
	 * @param moduleId the module/target ID to import
	 * @param monitor progress monitor
	 * @return the imported projects
	 */
	protected List<IProject> importModule(String moduleId, IProgressMonitor monitor) throws CoreException {
		return List.of();
	}

	// ── Utilities ───────────────────────────────────────────────────────

	protected static Preferences getPreferences() {
		return (JavaLanguageServerPlugin.getPreferencesManager() == null || JavaLanguageServerPlugin.getPreferencesManager().getPreferences() == null) ? new Preferences() : JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
	}

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
