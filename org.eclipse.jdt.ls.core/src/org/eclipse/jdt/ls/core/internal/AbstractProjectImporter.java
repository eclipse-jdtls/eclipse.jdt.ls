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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

public abstract class AbstractProjectImporter implements IProjectImporter {

	protected File rootFolder;
	protected Collection<java.nio.file.Path> directories;

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
	public abstract void reset();

	protected static Preferences getPreferences() {
		return (JavaLanguageServerPlugin.getPreferencesManager() == null || JavaLanguageServerPlugin.getPreferencesManager().getPreferences() == null) ? new Preferences() : JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
	}

	/**
	 * Find the project base path of the project configurations. Only the applied configurations will be considered.
	 * @param projectConfigurations The collection of project configurations.
	 * @param names The names of the interested configuration file for the importer.
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
