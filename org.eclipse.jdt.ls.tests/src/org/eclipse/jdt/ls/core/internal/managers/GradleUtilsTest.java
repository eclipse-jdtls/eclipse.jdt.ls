/*******************************************************************************
 * Copyright (c) 2023 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class GradleUtilsTest {
	@Test
	public void testGetJdkToLaunchDaemon() {
		assertEquals("17", GradleUtils.getMajorJavaVersion("17.0.8"));
		assertEquals("1.8", GradleUtils.getMajorJavaVersion("1.8.0_202"));
	}

	@Test
	public void testGetMajorJavaVersion() {
		File javaHome = GradleUtils.getJdkToLaunchDaemon("10");
		assertTrue(javaHome.getAbsolutePath().contains("fakejdk" + File.separator + "10"));
	}
}
