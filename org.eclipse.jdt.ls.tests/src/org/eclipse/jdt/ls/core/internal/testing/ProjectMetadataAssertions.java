/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.testing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.jdt.core.IJavaProject;

public final class ProjectMetadataAssertions {

	private ProjectMetadataAssertions() {
	}

	public static Map<Path, byte[]> snapshotProjectMetadata(Path projectRoot) throws IOException {
		Map<Path, byte[]> snapshot = new LinkedHashMap<>();
		try (Stream<Path> paths = Files.walk(projectRoot)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
				Path relativePath = projectRoot.relativize(path);
				if (isProtectedMetadataPath(relativePath)) {
					snapshot.put(relativePath, Files.readAllBytes(path));
				}
			}
		}
		return snapshot;
	}

	public static void assertProjectMetadataEquals(Map<Path, byte[]> expected, Path projectRoot) throws IOException {
		Map<Path, byte[]> actual = snapshotProjectMetadata(projectRoot);
		assertEquals(expected.keySet(), actual.keySet(), "Project-root metadata inventory changed");
		for (Path path : expected.keySet()) {
			assertArrayEquals(expected.get(path), actual.get(path), path + " changed");
		}
	}

	private static boolean isProtectedMetadataPath(Path relativePath) {
		if (relativePath.getNameCount() == 0) {
			return false;
		}
		String topLevelName = relativePath.getName(0).toString();
		if (".settings".equals(topLevelName)) {
			return true;
		}
		return relativePath.getNameCount() == 1 && (IProjectDescription.DESCRIPTION_FILE_NAME.equals(topLevelName)
				|| IJavaProject.CLASSPATH_FILE_NAME.equals(topLevelName));
	}
}
