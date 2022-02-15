/*******************************************************************************
 * Copyright (c) 2018-2022 Microsoft Corporation and others.
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

import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.jdt.core.JavaCore;

public class GradleCompatibilityChecker {

	public static String MAX_SUPPORTED_JAVA = JavaCore.VERSION_17;
	public static String CURRENT_GRADLE = "7.3.1";

	public static boolean isIncompatible(GradleVersion gradleVersion, String javaVersion) {
		if (gradleVersion == null || javaVersion == null || javaVersion.isEmpty()) {
			return false;
		}
		String highestSupportedJava = getHighestSupportedJava(gradleVersion);
		return JavaCore.compareJavaVersions(javaVersion, highestSupportedJava) > 0;
	}

	public static String getHighestSupportedJava(GradleVersion gradleVersion) {
		GradleVersion baseVersion = gradleVersion.getBaseVersion();
		try {
			// https://docs.gradle.org/current/userguide/compatibility.html
			if (baseVersion.compareTo(GradleVersion.version("7.3")) >= 0) {
				return JavaCore.VERSION_17;
			} else if (baseVersion.compareTo(GradleVersion.version("7.0")) >= 0) {
				return JavaCore.VERSION_16;
			} else if (baseVersion.compareTo(GradleVersion.version("6.7")) >= 0) {
				return JavaCore.VERSION_15;
			} else if (baseVersion.compareTo(GradleVersion.version("6.3")) >= 0) {
				return JavaCore.VERSION_14;
			} else if (baseVersion.compareTo(GradleVersion.version("6.0")) >= 0) {
				return JavaCore.VERSION_13;
			} else if (baseVersion.compareTo(GradleVersion.version("5.4")) >= 0) {
				return JavaCore.VERSION_12;
			} else if (baseVersion.compareTo(GradleVersion.version("5.0")) >= 0) {
				return JavaCore.VERSION_11;
			} else if (baseVersion.compareTo(GradleVersion.version("4.7")) >= 0) {
				return JavaCore.VERSION_10;
			} else if (baseVersion.compareTo(GradleVersion.version("4.3")) >= 0) {
				return JavaCore.VERSION_9;
			}
			return JavaCore.VERSION_1_8;
		} catch (IllegalArgumentException e) {
			return MAX_SUPPORTED_JAVA;
		}
	}

}
