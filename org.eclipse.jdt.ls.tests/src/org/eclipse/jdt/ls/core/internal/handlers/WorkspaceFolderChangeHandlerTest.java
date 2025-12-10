/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WorkspaceFolderChangeHandlerTest extends AbstractProjectsManagerBasedTest {

	private WorkspaceFolderChangeHandler workspaceFolderChangeHander;


	@BeforeEach
	public void setup() {
		this.workspaceFolderChangeHander = new WorkspaceFolderChangeHandler(projectsManager, preferenceManager);
	}

	@AfterEach
	public void tearDown() throws Exception {
		waitForBackgroundJobs();
	}

	@Test
	public void testUpdateWorkspaceFolder() throws Exception {
		File rootFolder = copyFiles("maven/salut", true);
		String rootFolderUri = rootFolder.toURI().toString();
		IPath rootFolderPath = ResourceUtils.canonicalFilePathFromURI(rootFolderUri);
		WorkspaceFolder workspaceFolder = new WorkspaceFolder(rootFolder.toURI().toString(), "test");

		WorkspaceFoldersChangeEvent event = new WorkspaceFoldersChangeEvent(Arrays.asList(workspaceFolder), Collections.emptyList());
		DidChangeWorkspaceFoldersParams params = new DidChangeWorkspaceFoldersParams(event);
		workspaceFolderChangeHander.update(params);
		assertTrue(preferenceManager.getPreferences().getRootPaths().contains(rootFolderPath));

		waitForBackgroundJobs();

		event = new WorkspaceFoldersChangeEvent(Collections.emptyList(), Arrays.asList(workspaceFolder));
		params = new DidChangeWorkspaceFoldersParams(event);
		workspaceFolderChangeHander.update(params);
		assertFalse(preferenceManager.getPreferences().getRootPaths().contains(rootFolderPath));
	}
}
