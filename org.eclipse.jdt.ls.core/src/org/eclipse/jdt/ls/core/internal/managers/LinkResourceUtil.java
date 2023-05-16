/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;

class LinkResourceUtil {

	private static final IPath METADATA_FOLDER_PATH = ResourcesPlugin.getWorkspace().getRoot().getLocation().append(".projects");

	private static boolean isNewer(Path file, Instant instant) throws CoreException {
		try {
			Instant creationInstant = Files.getFileAttributeView(file, BasicFileAttributeView.class).readAttributes().creationTime().toInstant();
			// match accuracy
			ChronoUnit smallestSupportedUnit = Stream.of(ChronoUnit.NANOS, ChronoUnit.MILLIS, ChronoUnit.SECONDS) // IMPORTANT: keeps units in Stream from finer to coarser
				.filter(creationInstant::isSupported).filter(instant::isSupported) //
				.findFirst().orElse(null);
			if (Platform.OS_MACOSX.equals(Platform.getOS())) {
				// macOS filesystem has second-only accuracy (despite Instant.isSupported returns)
				smallestSupportedUnit = ChronoUnit.SECONDS;
			}
			if (smallestSupportedUnit != null) {
				creationInstant.truncatedTo(smallestSupportedUnit);
				instant.truncatedTo(smallestSupportedUnit);
			} else {
				throw new CoreException(Status.error("No supported time unit!"));
			}
			return creationInstant.equals(instant) || creationInstant.isAfter(instant);
		} catch (IOException ex) {
			throw new CoreException(Status.error(ex.getMessage(), ex));
		}
	}

    /**
     * Get the redirected path of the input path. The path will be redirected to
     * the workspace's metadata folder ({@link LinkResourceUtil#METADATA_FOLDER_PATH}).
     * @param projectName name of the project.
     * @param path path needs to be redirected.
     * @return the redirected path.
     */
    private static IPath getMetaDataFilePath(String projectName, IPath path) {
        if (path.segmentCount() == 1) {
            return METADATA_FOLDER_PATH.append(projectName).append(path);
        }

        String lastSegment = path.lastSegment();
        if (IProjectDescription.DESCRIPTION_FILE_NAME.equals(lastSegment)) {
            return METADATA_FOLDER_PATH.append(projectName).append(lastSegment);
        }

        return null;
    }

	private static void linkFolderIfNewer(IFolder settingsFolder, Instant instant) throws CoreException {
		if (settingsFolder.isLinked()) {
			return;
		}
		if (settingsFolder.exists() && !isNewer(settingsFolder.getLocation().toPath(), instant)) {
			return;
		}
		if (!settingsFolder.exists()) {
			// not existing yet, create link
			File diskFolder = getMetaDataFilePath(settingsFolder.getProject().getName(), settingsFolder.getProjectRelativePath()).toFile();
			diskFolder.mkdirs();
			settingsFolder.createLink(diskFolder.toURI(), IResource.NONE, new NullProgressMonitor());
		} else if (isNewer(settingsFolder.getLocation().toPath(), instant)) {
			// already existing but not existing before import: move then link
			File sourceFolder = settingsFolder.getLocation().toFile();
			File targetFolder = getMetaDataFilePath(settingsFolder.getProject().getName(), settingsFolder.getProjectRelativePath()).toFile();
			File parentTargetFolder = targetFolder.getParentFile();
			if (!parentTargetFolder.isDirectory()) {
				parentTargetFolder.mkdirs();
			}
			sourceFolder.renameTo(targetFolder);
			settingsFolder.createLink(targetFolder.toURI(), IResource.REPLACE, new NullProgressMonitor());
		}
	}

	private static void linkFileIfNewer(IFile metadataFile, Instant instant, String ifEmpty) throws CoreException {
		if (metadataFile.isLinked()) {
			return;
		}
		if (metadataFile.exists() && !isNewer(metadataFile.getLocation().toPath(), instant)) {
			return;
		}
		File targetFile = getMetaDataFilePath(metadataFile.getProject().getName(), metadataFile.getProjectRelativePath()).toFile();
		targetFile.getParentFile().mkdirs();
		try {
			if (metadataFile.exists()) {
				Files.move(metadataFile.getLocation().toFile().toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.writeString(targetFile.toPath(), ifEmpty != null ? ifEmpty : "", StandardOpenOption.CREATE);
			}
		} catch (IOException ex) {
			throw new CoreException(Status.error(ex.getMessage(), ex));
		}
		metadataFile.createLink(targetFile.toURI(), IResource.REPLACE, new NullProgressMonitor());
	}

	public static void linkMetadataResourcesIfNewer(IProject project, Instant instant) throws CoreException {
		linkFileIfNewer(project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME), instant, null);
		linkFolderIfNewer(project.getFolder(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME), instant);
		linkFileIfNewer(project.getFile(IJavaProject.CLASSPATH_FILE_NAME), instant, null);
		linkFileIfNewer(project.getFile(".factorypath"), instant, "<factorypath/>");
	}

}
