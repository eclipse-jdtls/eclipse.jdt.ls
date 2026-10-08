/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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

import java.nio.file.Path;
import java.util.List;

/**
 * Lightweight descriptor for a workspace module discovered by the background scan.
 *
 * @param directory the module root directory (e.g. {@code /workspace/core})
 * @param artifactId the module's artifact identifier (from pom.xml or directory name for Gradle)
 * @param buildType the build system type
 * @param sourceFolders existing source folders within this module (e.g. {@code src/main/java})
 */
public record ModuleInfo(Path directory, String artifactId, BuildType buildType, List<Path> sourceFolders) {

	public enum BuildType {
		MAVEN, GRADLE
	}
}
