/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.filesystem;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.junit.Test;

public class JLSFsUtilsTest {
	@Test
	public void testGeneratesMetadataFilesAtProjectRoot() {
		System.setProperty(ProjectsManager.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, "true");
		assertTrue(ProjectsManager.generatesMetadataFilesAtProjectRoot());
	}

	@Test
	public void testNotGeneratesMetadataFilesAtProjectRoot() {
		System.setProperty(ProjectsManager.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, "false");
		assertFalse(ProjectsManager.generatesMetadataFilesAtProjectRoot());
	}

	@Test
	public void testGeneratesMetadataFilesAtProjectRootWhenNotSet() {
		assertTrue(ProjectsManager.generatesMetadataFilesAtProjectRoot());
	}
}
