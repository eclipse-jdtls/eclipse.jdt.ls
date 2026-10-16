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
package org.eclipse.jdt.ls.core.internal.mbt;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;

/**
 * Reads {@code mbt.json} files as originated by Metals V2 and generated
 * by the classpath-extractor project.
 */
public final class MbtJson {

	private static final String MBT_JSON = "mbt.json";
	private static final Gson GSON = new Gson();

	private MbtJson() {
	}

	/**
	 * Returns the path to the {@code mbt.json} file in the given workspace root.
	 *
	 * @param workspaceRoot the workspace root directory
	 * @return the path to {@code mbt.json}
	 */
	public static Path getPath(Path workspaceRoot) {
		return workspaceRoot.resolve(MBT_JSON);
	}

	/**
	 * Checks whether an {@code mbt.json} file exists in the given workspace root.
	 *
	 * @param workspaceRoot the workspace root directory
	 * @return {@code true} if {@code mbt.json} exists
	 */
	public static boolean exists(Path workspaceRoot) {
		return Files.isRegularFile(getPath(workspaceRoot));
	}

	/**
	 * Parse an {@code mbt.json} file into its DTO shape.
	 *
	 * @param mbtJsonPath the path to the {@code mbt.json} file
	 * @return the parsed {@link MbtInfo}
	 * @throws IOException if reading fails
	 */
	public static MbtInfo read(Path mbtJsonPath) throws IOException {
		try (Reader reader = Files.newBufferedReader(mbtJsonPath)) {
			return GSON.fromJson(reader, MbtInfo.class);
		}
	}

	/**
	 * Checks whether the {@code mbt.json} file is stale compared to any build
	 * file in the workspace. A stale file indicates that a build file (e.g.
	 * {@code pom.xml}) has been modified after {@code mbt.json} was generated.
	 *
	 * @param mbtJsonPath the path to the {@code mbt.json} file
	 * @param workspaceRoot the workspace root directory
	 * @return {@code true} if {@code mbt.json} is older than any {@code pom.xml}
	 *         found in the workspace
	 */
	public static boolean isStale(Path mbtJsonPath, Path workspaceRoot) {
		try {
			if (!Files.isRegularFile(mbtJsonPath)) {
				return true;
			}
			long mbtTimestamp = Files.getLastModifiedTime(mbtJsonPath).toMillis();
			return isAnyBuildFileNewer(workspaceRoot, mbtTimestamp);
		} catch (IOException e) {
			return true;
		}
	}

	private static boolean isAnyBuildFileNewer(Path dir, long referenceTimestamp) throws IOException {
		try (var stream = Files.find(dir, Integer.MAX_VALUE,
				(path, attrs) -> attrs.isRegularFile() && isBuildFileName(path.getFileName().toString()))) {
			return stream.anyMatch(path -> {
				try {
					return Files.getLastModifiedTime(path).toMillis() > referenceTimestamp;
				} catch (IOException e) {
					return false;
				}
			});
		}
	}

	private static boolean isBuildFileName(String fileName) {
		return "pom.xml".equals(fileName)
				|| "build.gradle".equals(fileName)
				|| "build.gradle.kts".equals(fileName);
	}
}
