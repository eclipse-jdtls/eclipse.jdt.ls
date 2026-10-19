/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.IMaven;

/**
 * Identify a Maven artifact from a file in the local repository using a heuristic approach.
 */
public class MavenLocalRepositoryIdentifier implements IMavenArtifactIdentifier {

	@Override
	public ArtifactKey identify(IPath path, IProgressMonitor monitor) {
		if (path == null) {
			return null;
		}
		return identify(path.toFile(), monitor);
	}

	private ArtifactKey identify(File file, IProgressMonitor monitor) {
		if (file == null || !file.isFile() || !file.canRead()) {
			return null;
		}

		IMaven maven = MavenPlugin.getMaven();
		try {
			ArtifactRepository localRepository = maven.getLocalRepository();
			if (localRepository == null) {
				return null;
			}
			String localRepositoryDir = localRepository.getBasedir();
			if (localRepositoryDir == null) {
				return null;
			}

			// Normalize paths for proper comparison and relativize operations
			Path filePath = file.toPath().normalize();
			Path localRepoPath = Paths.get(localRepositoryDir).normalize();

			// Check if file is within the local repository using Path operations
			if (!filePath.startsWith(localRepoPath)) {
				return null;
			}

			// Check that we have enough segments in the relative path for Maven structure:
			// <groupId>/<artifactId>/<version>/<file> (minimum 4 segments)
			Path fileRelativePath = localRepoPath.relativize(filePath);
			int fileSegmentCount = fileRelativePath.getNameCount();
			if (fileSegmentCount < 4) {
				return null;
			}

			File versionDirectory = file.getParentFile();
			String version = versionDirectory.getName();

			File artifactIdDirectory = versionDirectory.getParentFile();
			String artifactId = artifactIdDirectory.getName();

			// Infer the groupId from all directories below the local repository directory
			File lastPartGroupIdDirectory = artifactIdDirectory.getParentFile();
			Path groupIdPath = lastPartGroupIdDirectory.toPath().normalize();
			Path relativePath = localRepoPath.relativize(groupIdPath);

			// Convert path separators to dots for groupId
			String groupId = relativePath.toString().replace("\\", ".")
													.replace("/", ".");

			return new ArtifactKey(groupId, artifactId, version, null);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logError("Failed to identify " + file + " : " + e);
		}
		return null;
	}
}
