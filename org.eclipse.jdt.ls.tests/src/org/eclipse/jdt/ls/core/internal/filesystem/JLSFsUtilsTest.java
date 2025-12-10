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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JLSFsUtilsTest {
	@Test
	public void testGeneratesMetadataFilesAtProjectRoot() {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, "true");
		assertTrue(JLSFsUtils.generatesMetadataFilesAtProjectRoot());
	}

	@Test
	public void testNotGeneratesMetadataFilesAtProjectRoot() {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, "false");
		assertFalse(JLSFsUtils.generatesMetadataFilesAtProjectRoot());
	}

	@Test
	public void testGeneratesMetadataFilesAtProjectRootWhenNotSet() {
		assertTrue(JLSFsUtils.generatesMetadataFilesAtProjectRoot());
	}

	@Test
	public void testExcluded() {
		IPath path = new Path("/project/node_modules");
		assertTrue(JLSFsUtils.isExcluded(path));
	}

	@AfterEach
	public void cleanUp() throws Exception {
		System.clearProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT);
	}
}
