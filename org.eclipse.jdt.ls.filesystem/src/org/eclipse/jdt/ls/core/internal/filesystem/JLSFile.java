/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.internal.filesystem.local.LocalFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

/**
 * JDT.LS's own implementation of files in the local operating system's file system.
 * The instance of this class will be returned by {@link JLSFileSystem}.
 */
public class JLSFile extends LocalFile {

    public JLSFile(File file) {
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
        IPath filePath = new Path(this.filePath);
        IPath preservedShadowRoot = getPreservedShadowRoot(filePath);
        if (preservedShadowRoot == null && JLSFsUtils.isExcluded(filePath)) {
            return childNames;
        }
        if (preservedShadowRoot != null) {
            Set<String> childNameSet = new LinkedHashSet<>();
            for (String childName : childNames) {
                if (!ProjectMetadataStore.PROTECTED_METADATA_NAMES.contains(childName)
                        || preservedShadowRoot.append(childName).toFile().exists()) {
                    childNameSet.add(childName);
                }
            }
            for (String fileName : ProjectMetadataStore.PROTECTED_METADATA_NAMES) {
                if (preservedShadowRoot.append(fileName).toFile().exists()) {
                    childNameSet.add(fileName);
                }
            }
            childNames = childNameSet.toArray(String[]::new);
        }

        if (JLSFsUtils.generatesMetadataFilesAtProjectRoot()) {
            return childNames;
        }

        String projectName = JLSFsUtils.getProjectNameIfLocationIsProjectRoot(filePath);
        if (projectName == null) {
            return childNames;
        }

        Set<String> childNameSet = new LinkedHashSet<>(Arrays.asList(childNames));
        for (String fileName : JLSFsUtils.METADATA_NAMES) {
            if (preservedShadowRoot != null && ProjectMetadataStore.PROTECTED_METADATA_NAMES.contains(fileName)) {
                continue;
            }
            if (!childNameSet.contains(fileName) &&
                    JLSFsUtils.METADATA_FOLDER_PATH.append(projectName).append(fileName).toFile().exists()) {
                childNameSet.add(fileName);
            }
        }

        return childNameSet.toArray(String[]::new);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Since the metadata files may be redirected into the workspace metadata area,
     * we override this method to make sure the redirected files are reported as
     * children. This mirrors {@link #childNames(int, IProgressMonitor)} and is
     * required because {@link LocalFile#childInfos(int, IProgressMonitor)} performs
     * a bulk directory listing of the physical location that bypasses both
     * {@link #childNames(int, IProgressMonitor)} and {@link #getChild(String)},
     * which would otherwise leave the redirected metadata files invisible to the
     * resource refresh/synchronization.
     * </p>
     */
    @Override
    public IFileInfo[] childInfos(int options, IProgressMonitor monitor) {
        IFileInfo[] childInfos = super.childInfos(options, monitor);
        IPath filePath = new Path(this.filePath);
        Set<String> physicalNames = new LinkedHashSet<>();
        for (IFileInfo info : childInfos) {
            physicalNames.add(info.getName());
        }
        IPath preservedShadowRoot = getPreservedShadowRoot(filePath);
        if (preservedShadowRoot == null && JLSFsUtils.isExcluded(filePath)) {
            return childInfos;
        }
        if (preservedShadowRoot != null) {
            List<IFileInfo> result = new ArrayList<>();
            for (IFileInfo info : childInfos) {
                if (!ProjectMetadataStore.PROTECTED_METADATA_NAMES.contains(info.getName())) {
                    result.add(info);
                    continue;
                }
                IPath shadowChild = preservedShadowRoot.append(info.getName());
                if (shadowChild.toFile().exists()) {
                    result.add(new JLSFile(shadowChild.toFile()).fetchInfo());
                }
            }
            for (String fileName : ProjectMetadataStore.PROTECTED_METADATA_NAMES) {
                IPath shadowChild = preservedShadowRoot.append(fileName);
                if (!physicalNames.contains(fileName) && shadowChild.toFile().exists()) {
                    result.add(new JLSFile(shadowChild.toFile()).fetchInfo());
                }
            }
            childInfos = result.toArray(IFileInfo[]::new);
        }

        if (JLSFsUtils.generatesMetadataFilesAtProjectRoot()) {
            return childInfos;
        }

        String projectName = JLSFsUtils.getProjectNameIfLocationIsProjectRoot(filePath);
        if (projectName == null) {
            return childInfos;
        }

        Set<String> existingNames = new LinkedHashSet<>();
        for (IFileInfo info : childInfos) {
            existingNames.add(info.getName());
        }

        List<IFileInfo> result = null;
        for (String fileName : JLSFsUtils.METADATA_NAMES) {
            if (preservedShadowRoot != null && ProjectMetadataStore.PROTECTED_METADATA_NAMES.contains(fileName)) {
                continue;
            }
            if (!existingNames.contains(fileName) &&
                    JLSFsUtils.METADATA_FOLDER_PATH.append(projectName).append(fileName).toFile().exists()) {
                if (result == null) {
                    result = new ArrayList<>(Arrays.asList(childInfos));
                }
                result.add(getChild(fileName).fetchInfo());
            }
        }

        return result == null ? childInfos : result.toArray(IFileInfo[]::new);
    }

    @Override
    public IFileStore getChild(String name) {
        IPath path = new Path(this.filePath).append(name);
        boolean excluded = JLSFsUtils.isExcluded(path);
        IPath preservedPath = ProjectMetadataStore.getRedirectedPath(path);
        if (preservedPath != null) {
            return new JLSFile(preservedPath.toFile());
        }
        if (JLSFsUtils.shouldStoreInMetadataArea(path) && !excluded) {
            IPath containerPath = JLSFsUtils.getContainerPath(path);
            String projectName = JLSFsUtils.getProjectNameIfLocationIsProjectRoot(containerPath);
            if (projectName == null) {
                return new JLSFile(new File(file, name));
            }
            IPath redirectedPath = JLSFsUtils.getMetaDataFilePath(projectName, new Path(name));
            if (redirectedPath != null) {
                return new JLSFile(redirectedPath.toFile());
            }
        }

        return new JLSFile(new File(file, name));
    }

    @Override
    public IFileStore getFileStore(IPath path) {
        IPath fullPath = new Path(this.filePath).append(path);
        boolean excluded = JLSFsUtils.isExcluded(fullPath);
        IPath preservedPath = ProjectMetadataStore.getRedirectedPath(fullPath);
        if (preservedPath != null) {
            return new JLSFile(preservedPath.toFile());
        }
        if (JLSFsUtils.shouldStoreInMetadataArea(fullPath) && !excluded) {
            IPath containerPath = JLSFsUtils.getContainerPath(fullPath);
            String projectName = JLSFsUtils.getProjectNameIfLocationIsProjectRoot(containerPath);
            if (projectName == null) {
                return new JLSFile(fullPath.toFile());
            }
            IPath redirectedPath = JLSFsUtils.getMetaDataFilePath(projectName, path);
            if (redirectedPath != null) {
                return new JLSFile(redirectedPath.toFile());
            }
        }

        return new JLSFile(fullPath.toFile());
    }

    private static IPath getPreservedShadowRoot(IPath projectRoot) {
        return ProjectMetadataStore.getShadowRoot(projectRoot);
    }
}
