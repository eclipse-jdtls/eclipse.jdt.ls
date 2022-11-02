/*******************************************************************************
 * Copyright (c) 2021-2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.filesystem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;

/**
 * Utilities of the file system implementation.
 */
public class JLSFsUtils {
    private JLSFsUtils() {}

    static final IPath METADATA_FOLDER_PATH = ResourcesPlugin.getPlugin().getStateLocation().append(".projects");

    /**
     * The system property key to specify the file system mode.
    */
    static final String GENERATES_METADATA_FILES_AT_PROJECT_ROOT = "java.import.generatesMetadataFilesAtProjectRoot";

    static final String FACTORY_PATH = ".factorypath";

    /**
     * The metadata files
     */
    static final Set<String> METADATA_NAMES = new HashSet<>(Arrays.asList(
        IProjectDescription.DESCRIPTION_FILE_NAME,
        EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME,
        IJavaProject.CLASSPATH_FILE_NAME,
        FACTORY_PATH
    ));

    /**
     * Determine whether the resource should be stored in workspace's metadata folder.
     * <p>
     * The file will be stored in workspace's metadata folder when following conditions meet:
     * <ul>
     *   <li>The system property shows that it's allowed to store them in workspace.</li>
     *   <li>The file belongs to the metadata file defined in {@link JLSFsUtils#METADATA_NAMES}.</li>
     *   <li>The project's root path does not contain the metadata file with the same name.</li>
     * </ul>
     * </p>
     *
     * @param location the path of the resource.
     * @return whether the resource needs to be stored in workspace's metadata folder.
     */
    static boolean shouldStoreInMetadataArea(IPath location) {
        if (generatesMetadataFilesAtProjectRoot()) {
            return false;
        }

        if (!isProjectMetadataFile(location)) {
            return false;
        }

        // do not redirect if the file already exists on disk
        if (location.toFile().exists()) {
            return false;
        } else if (location.lastSegment().endsWith(EclipsePreferences.PREFS_FILE_EXTENSION)) {
            location = location.removeLastSegments(1);
            return !location.toFile().exists();
        }

        return true;
    }

    /**
     * Check whether the given location points to a metadata file.
     * @param location file location.
     * @return whether the given location points to a metadata file.
     */
    static boolean isProjectMetadataFile(IPath location) {
        if (location == null || location.segmentCount() < 2) {
            return false;
        }

        if (location.lastSegment().endsWith(EclipsePreferences.PREFS_FILE_EXTENSION)) {
            location = location.removeLastSegments(1);
        }

        if (location.segmentCount() < 2) {
            return false;
        }

        if (!METADATA_NAMES.contains(location.lastSegment())) {
            return false;
        }

        return true;
    }

    /**
     * Get the container path of the given file path.
     * If the file path is a preferences file, the grand-parent container will be returned.
     * @param filePath the file path.
     */
    static IPath getContainerPath(IPath filePath) {
        if (filePath.lastSegment().endsWith(EclipsePreferences.PREFS_FILE_EXTENSION)) {
            filePath = filePath.removeLastSegments(1);
        }
        return filePath.removeLastSegments(1);
    }

    /**
     * Get the project name if the given path is a project root, or <code>null</code> if
     * no project root can match the path.
     * @param location the location path.
     * @return The project name
     */
    static String getProjectNameIfLocationIsProjectRoot(IPath location) {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects(IContainer.INCLUDE_HIDDEN);
        for (IProject project : projects) {
            IPath projectLocation = project.getLocation();
            if (Objects.equals(projectLocation, location)) {
                return project.getName();
            }
        }
        return null;
    }

    /**
     * Get the redirected path of the input path. The path will be redirected to
     * the workspace's metadata folder ({@link JLSFsUtils#METADATA_FOLDER_PATH}).
     * @param projectName name of the project.
     * @param path path needs to be redirected.
     * @return the redirected path.
     */
    static IPath getMetaDataFilePath(String projectName, IPath path) {
        if (path.segmentCount() == 1) {
            return METADATA_FOLDER_PATH.append(projectName).append(path);
        }

        String lastSegment = path.lastSegment();
        if (METADATA_NAMES.contains(lastSegment)) {
            return METADATA_FOLDER_PATH.append(projectName).append(lastSegment);
        } else if (lastSegment.endsWith(EclipsePreferences.PREFS_FILE_EXTENSION)) {
            return METADATA_FOLDER_PATH.append(projectName)
                    .append(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME)
                    .append(lastSegment);
        }

        return null;
    }

    /**
     * Check whether the metadata files needs to be generated at project root.
     */
    public static boolean generatesMetadataFilesAtProjectRoot() {
        String property = System.getProperty(GENERATES_METADATA_FILES_AT_PROJECT_ROOT);
        if (property == null) {
            return true;
        }
        return Boolean.parseBoolean(property);
    }
}
