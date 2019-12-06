/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
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

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.WorkspaceFolder;

public class WorkspaceFolderChangeHandler {

	private ProjectsManager projectManager;

	WorkspaceFolderChangeHandler(ProjectsManager projectManager) {
		this.projectManager = projectManager;
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
		projectManager.updateWorkspaceFolders(addedRootPaths, removedRootPaths);
	}
}
