/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IJavaProject;

/**
 * Stores a private, writable snapshot of project metadata while leaving the
 * original metadata at the project root untouched.
 */
public final class ProjectMetadataStore {

	static final Set<String> PROTECTED_METADATA_NAMES = Set.of(
			IProjectDescription.DESCRIPTION_FILE_NAME,
			IJavaProject.CLASSPATH_FILE_NAME,
			".settings");

	private static final String CORE_PLUGIN_ID = "org.eclipse.jdt.ls.core";
	private static final String PRESERVE_PROJECT_METADATA = "java.import.preserveProjectMetadata";
	private static final String SHADOW_FOLDER = ".preserved";
	private static final String COMPLETE_MARKER = ".complete";
	private static final Object INITIALIZATION_LOCK = new Object();

	private ProjectMetadataStore() {
	}

	/**
	 * Returns the private location for protected metadata, initializing the entire
	 * metadata snapshot before returning it. Returns {@code null} for paths that
	 * are not protected or when preservation is disabled.
	 */
	static IPath getRedirectedPath(IPath location) {
		if (!isPreservationEnabled() || location == null || isInMetadataArea(location)) {
			return null;
		}

		try {
			MetadataPath metadataPath = MetadataPath.from(location);
			if (metadataPath == null || !isProjectMetadataRoot(metadataPath.projectRoot())) {
				return null;
			}
			if (!isRegisteredProjectRoot(metadataPath.projectRoot()) && JLSFsUtils.isExcluded(location)) {
				return null;
			}
			return ensureInitialized(metadataPath).append(metadataPath.relativePath());
		} catch (IOException e) {
			throw new IllegalStateException("Unable to preserve project metadata for " + location, e);
		}
	}

	/**
	 * Returns the private metadata root for an Eclipse project, initializing it on
	 * first access.
	 */
	static IPath getShadowRoot(IPath projectRoot) {
		if (!isPreservationEnabled() || projectRoot == null || isInMetadataArea(projectRoot)) {
			return null;
		}

		try {
			MetadataPath metadataPath = MetadataPath.forProjectRoot(projectRoot);
			if (!isProjectMetadataRoot(metadataPath.projectRoot())) {
				return null;
			}
			if (!isRegisteredProjectRoot(metadataPath.projectRoot()) && JLSFsUtils.isExcluded(projectRoot)) {
				return null;
			}
			return ensureInitialized(metadataPath);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to preserve project metadata for " + projectRoot, e);
		}
	}

	static boolean isPreservationEnabled() {
		return InstanceScope.INSTANCE.getNode(CORE_PLUGIN_ID).getBoolean(PRESERVE_PROJECT_METADATA, false);
	}

	private static boolean isInMetadataArea(IPath location) {
		return JLSFsUtils.METADATA_FOLDER_PATH.isPrefixOf(location);
	}

	private static boolean isProjectMetadataRoot(IPath projectRoot) {
		java.nio.file.Path projectDescription = projectRoot.append(IProjectDescription.DESCRIPTION_FILE_NAME).toFile().toPath();
		return Files.exists(projectDescription, LinkOption.NOFOLLOW_LINKS) || isRegisteredProjectRoot(projectRoot);
	}

	private static boolean isRegisteredProjectRoot(IPath projectRoot) {
		return JLSFsUtils.getProjectNameIfLocationIsProjectRoot(projectRoot) != null;
	}

	private static IPath ensureInitialized(MetadataPath metadataPath) throws IOException {
		synchronized (INITIALIZATION_LOCK) {
			java.nio.file.Path shadowRoot = metadataPath.shadowRoot().toFile().toPath();
			java.nio.file.Path completeMarker = shadowRoot.resolve(COMPLETE_MARKER);
			if (Files.isRegularFile(completeMarker, LinkOption.NOFOLLOW_LINKS)) {
				return metadataPath.shadowRoot();
			}
			if (Files.exists(shadowRoot, LinkOption.NOFOLLOW_LINKS)) {
				deleteRecursively(shadowRoot);
			}
			Files.createDirectories(shadowRoot);
			try {
				java.nio.file.Path projectRoot = metadataPath.projectRoot().toFile().toPath();
				for (String metadataName : PROTECTED_METADATA_NAMES) {
					java.nio.file.Path source = projectRoot.resolve(metadataName);
					if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
						copyRecursively(source, shadowRoot.resolve(metadataName));
					}
				}
				Files.createFile(completeMarker);
			} catch (IOException e) {
				try {
					deleteRecursively(shadowRoot);
				} catch (IOException cleanupException) {
					e.addSuppressed(cleanupException);
				}
				throw e;
			}
			return metadataPath.shadowRoot();
		}
	}

	private static void copyRecursively(java.nio.file.Path source, java.nio.file.Path target) throws IOException {
		if (Files.isSymbolicLink(source)) {
			throw new IOException("Symbolic links are not supported in preserved project metadata: " + source);
		}
		if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			return;
		}

		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(java.nio.file.Path directory, BasicFileAttributes attributes) throws IOException {
				if (attributes.isSymbolicLink()) {
					throw new IOException("Symbolic links are not supported in preserved project metadata: " + directory);
				}
				Files.createDirectories(target.resolve(source.relativize(directory)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attributes) throws IOException {
				if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
					throw new IOException("Unsupported file type in preserved project metadata: " + file);
				}
				java.nio.file.Path destination = target.resolve(source.relativize(file));
				Files.createDirectories(destination.getParent());
				Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteRecursively(java.nio.file.Path root) throws IOException {
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attributes) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(java.nio.file.Path directory, IOException exception) throws IOException {
				if (exception != null) {
					throw exception;
				}
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private record MetadataPath(IPath projectRoot, IPath relativePath, IPath shadowRoot) {

		static MetadataPath from(IPath location) throws IOException {
			for (int i = location.segmentCount() - 1; i >= 0; i--) {
				if (!".settings".equals(location.segment(i)) || i == 0) {
					continue;
				}
				IPath projectRoot = location.removeLastSegments(location.segmentCount() - i);
				MetadataPath candidate = create(projectRoot, location.removeFirstSegments(i));
				if (ProjectMetadataStore.isProjectMetadataRoot(candidate.projectRoot())) {
					return candidate;
				}
			}

			if (location.segmentCount() >= 2 && PROTECTED_METADATA_NAMES.contains(location.lastSegment())) {
				return create(location.removeLastSegments(1), new Path(location.lastSegment()));
			}
			return null;
		}

		static MetadataPath forProjectRoot(IPath projectRoot) throws IOException {
			return create(projectRoot, Path.EMPTY);
		}

		private static MetadataPath create(IPath projectRoot, IPath relativePath) throws IOException {
			java.nio.file.Path canonicalRoot = projectRoot.toFile().getCanonicalFile().toPath();
			IPath canonicalProjectRoot = Path.fromOSString(canonicalRoot.toString());
			String projectIdentity = canonicalRoot.toUri().normalize().toString();
			String projectKey = UUID.nameUUIDFromBytes(projectIdentity.getBytes(StandardCharsets.UTF_8)).toString();
			IPath shadowRoot = JLSFsUtils.METADATA_FOLDER_PATH.append(SHADOW_FOLDER).append(projectKey);
			return new MetadataPath(canonicalProjectRoot, relativePath, shadowRoot);
		}
	}
}
