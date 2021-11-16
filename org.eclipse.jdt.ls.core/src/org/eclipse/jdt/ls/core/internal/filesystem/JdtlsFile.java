/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
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

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.internal.filesystem.local.LocalFile;
import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;

/**
 * JDT.LS's own implementation of files in the local operating system's file system.
 * The instance of this class will be returned by {@link JdtlsFileSystem}.
 */
public class JdtlsFile extends LocalFile {

    public JdtlsFile(File file) {
        super(file);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Since the metadata files may be redirected into the workspace,
     * we override the method to make sure those files are not missed.
     * </p>
     */
    @Override
    public String[] childNames(int options, IProgressMonitor monitor) {
        String[] childNames = super.childNames(options, monitor);
        if (JdtlsFsUtils.generatesMetadataFilesAtProjectRoot()) {
            return childNames;
        }

        IPath filePath = ResourceUtils.filePathFromURI(this.toURI().toString());
        IProject project = JdtlsFsUtils.getProject(filePath);
        if (project == null || !project.getLocation().equals(filePath)) {
            return childNames;
        }

        Set<String> childNameSet = new LinkedHashSet<>(Arrays.asList(childNames));
        String projectName = project.getName();
        if (!childNameSet.contains(IProjectDescription.DESCRIPTION_FILE_NAME) &&
                JdtlsFsUtils.METADATA_FOLDER_PATH.append(projectName).append(IProjectDescription.DESCRIPTION_FILE_NAME).toFile().exists()) {
            childNameSet.add(IProjectDescription.DESCRIPTION_FILE_NAME);
        }

        if (!childNameSet.contains(IJavaProject.CLASSPATH_FILE_NAME) &&
                JdtlsFsUtils.METADATA_FOLDER_PATH.append(projectName).append(IJavaProject.CLASSPATH_FILE_NAME).toFile().exists()) {
            childNameSet.add(IJavaProject.CLASSPATH_FILE_NAME);
        }

        if (!childNameSet.contains(JdtlsFsUtils.FACTORY_PATH) &&
                JdtlsFsUtils.METADATA_FOLDER_PATH.append(projectName).append(JdtlsFsUtils.FACTORY_PATH).toFile().exists()) {
            childNameSet.add(JdtlsFsUtils.FACTORY_PATH);
        }

        if (!childNameSet.contains(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME) &&
                JdtlsFsUtils.METADATA_FOLDER_PATH.append(projectName).append(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME).toFile().exists()) {
            childNameSet.add(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME);
        }
        return childNameSet.toArray(String[]::new);
    }

    @Override
    public IFileStore getChild(String name) {
        IPath path = new Path(this.filePath).append(name);
        if (JdtlsFsUtils.shouldStoreInWorkspaceStorage(path)) {
            IProject project = JdtlsFsUtils.getProject(path);
            if (project == null) {
                return new JdtlsFile(new File(file, name));
            }
            String projectName = project.getName();
            IPath realPath = JdtlsFsUtils.getMetaDataFilePath(projectName, new Path(name));
            if (realPath != null) {
                return new JdtlsFile(realPath.toFile());
            }
        }

        return new JdtlsFile(new File(file, name));
    }

    @Override
    public IFileStore getFileStore(IPath path) {
        IPath fullPath = new Path(file.getPath()).append(path);
        if (JdtlsFsUtils.shouldStoreInWorkspaceStorage(fullPath)) {
            IProject project = JdtlsFsUtils.getProject(fullPath);
            if (project == null) {
                return new JdtlsFile(fullPath.toFile());
            }
            String projectName = project.getName();
            IPath realPath = JdtlsFsUtils.getMetaDataFilePath(projectName, path);
            if (realPath != null) {
                return new JdtlsFile(realPath.toFile());
            }
        }

        return new JdtlsFile(fullPath.toFile());
    }
}
