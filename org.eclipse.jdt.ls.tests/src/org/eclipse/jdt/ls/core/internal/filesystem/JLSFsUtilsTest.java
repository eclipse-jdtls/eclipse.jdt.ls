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

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.junit.After;
import org.junit.Test;

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

	@After
	public void cleanUp() throws Exception {
		System.clearProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT);
	}
}
