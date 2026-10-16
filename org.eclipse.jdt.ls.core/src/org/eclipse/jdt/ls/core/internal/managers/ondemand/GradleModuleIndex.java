/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers.ondemand;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Module index for Gradle projects.
 *
 * <p>Module discovery is fully lazy: no upfront scan, no settings file
 * parsing. When a file is opened, the index walks up the directory tree
 * to find the nearest {@code build.gradle} or {@code build.gradle.kts}.
 * The actual project import is delegated to Buildship via
 * {@link org.eclipse.jdt.ls.core.internal.managers.GradleProjectImporter#importModule}.</p>
 *
 * <p>Buildship does not expose an API to list subprojects from
 * {@code settings.gradle} without triggering a full Gradle
 * synchronization, and parsing {@code settings.gradle} ourselves would
 * be unreliable since it is executable Groovy/Kotlin code. The walk-up
 * approach avoids both problems.</p>
 */
public class GradleModuleIndex extends AbstractModuleIndex {

	private static final String BUILD_GRADLE = "build.gradle";
	private static final String BUILD_GRADLE_KTS = "build.gradle.kts";

	/**
	 * Creates a new Gradle module index.
	 *
	 * @param workspacePath the workspace root directory
	 */
	public GradleModuleIndex(Path workspacePath) {
		super(workspacePath);
	}

	@Override
	protected boolean hasBuildFile(Path dir) {
		return Files.isRegularFile(dir.resolve(BUILD_GRADLE))
				|| Files.isRegularFile(dir.resolve(BUILD_GRADLE_KTS));
	}
}
