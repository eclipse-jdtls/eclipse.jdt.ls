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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WorkspaceFolderChangeHandlerTest extends AbstractProjectsManagerBasedTest {

	private WorkspaceFolderChangeHandler workspaceFolderChangeHander;


	@Before
	public void setup() {
		this.workspaceFolderChangeHander = new WorkspaceFolderChangeHandler(projectsManager, preferenceManager);
	}

	@After
	public void tearDown() throws Exception {
		waitForBackgroundJobs();
	}

	@Test
	public void testUpdateWorkspaceFolder() throws Exception {
		File rootFolder = copyFiles("maven/salut", true);
		String rootFolderUri = rootFolder.toURI().toString();
		IPath rootFolderPath = ResourceUtils.canonicalFilePathFromURI(rootFolderUri);
		WorkspaceFolder workspaceFolder = new WorkspaceFolder(rootFolder.toURI().toString());

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
