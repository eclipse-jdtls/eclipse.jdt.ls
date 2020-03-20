/*******************************************************************************
 * Copyright (c) 2017-2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.WorkspaceFolder;

public class WorkspaceFolderChangeHandler {

	private ProjectsManager projectManager;
	private PreferenceManager preferenceManager;

	public WorkspaceFolderChangeHandler(ProjectsManager projectManager, PreferenceManager preferenceManager) {
		this.projectManager = projectManager;
		this.preferenceManager = preferenceManager;
	}

	public void update(DidChangeWorkspaceFoldersParams params) {
		final Collection<IPath> addedRootPaths = new ArrayList<>();
		final Collection<IPath> removedRootPaths = new ArrayList<>();
		for (WorkspaceFolder folder : params.getEvent().getAdded()) {
			IPath rootPath = ResourceUtils.canonicalFilePathFromURI(folder.getUri());
			if (rootPath != null) {
				addedRootPaths.add(rootPath);
			}
		}
		for (WorkspaceFolder folder : params.getEvent().getRemoved()) {
			IPath rootPath = ResourceUtils.canonicalFilePathFromURI(folder.getUri());
			if (rootPath != null) {
				removedRootPaths.add(rootPath);
			}
		}
		updateRootPaths(addedRootPaths, removedRootPaths);
		projectManager.updateWorkspaceFolders(addedRootPaths, removedRootPaths);
	}

	private void updateRootPaths(Collection<IPath> addedRootPaths, Collection<IPath> removedRootPaths) {
		Set<IPath> rootPathSet = new HashSet<>(preferenceManager.getPreferences().getRootPaths());
		rootPathSet.removeAll(removedRootPaths);
		rootPathSet.addAll(addedRootPaths);
		preferenceManager.getPreferences().setRootPaths(rootPathSet);
	}
}
