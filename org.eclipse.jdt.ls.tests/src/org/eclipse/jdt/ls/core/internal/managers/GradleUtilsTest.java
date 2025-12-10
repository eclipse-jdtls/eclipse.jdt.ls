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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Map;

import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.Test;

public class GradleUtilsTest {
	@Test
	public void testGetJdkToLaunchDaemon() {
		assertEquals("17", GradleUtils.getMajorJavaVersion("17.0.8"));
		assertEquals("1.8", GradleUtils.getMajorJavaVersion("1.8.0_202"));
	}

	@Test
	public void testGetMajorJavaVersion() {
		Map<String, File> vmInstalls = GradleUtils.getAllVmInstalls();
		vmInstalls.forEach((k, v) -> {
			File javaHome = GradleUtils.getJdkToLaunchDaemon(k);
			assertEquals(v, javaHome, "javaHome=" + javaHome.getAbsolutePath());
		});
	}

	@Test
	public void testCompatiblity() {
		GradleVersion gradleVersion = GradleVersion.version("9.1");
		assertEquals(JavaCore.VERSION_25, GradleUtils.getHighestSupportedJava(gradleVersion));
	}
}
