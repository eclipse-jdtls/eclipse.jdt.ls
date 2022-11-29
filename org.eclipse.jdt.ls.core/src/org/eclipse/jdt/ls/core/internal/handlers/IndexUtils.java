/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.index.FileIndexLocation;
import org.eclipse.jdt.internal.core.index.Index;
import org.eclipse.jdt.internal.core.index.IndexLocation;
import org.eclipse.jdt.internal.core.search.indexing.IndexManager;
import org.eclipse.jdt.internal.core.search.indexing.ReadWriteMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

public class IndexUtils {
    public static void copyIndexesToSharedLocation() {
        IndexManager indexManager = JavaModelManager.getIndexManager();
        // common index location for all workspaces
        final String SHARED_INDEX_LOCATION = System.getProperty("jdt.core.sharedIndexLocation");
        if (indexManager == null || StringUtils.isBlank(SHARED_INDEX_LOCATION)) {
            return;
        }

        Set<IndexLocation> resolvedIndexLocations = new HashSet<>();
        IPath javaCoreStateLocation = JavaCore.getPlugin().getStateLocation();
        for (IJavaProject javaProject : ProjectUtils.getJavaProjects()) {
            try {
                IClasspathEntry[] entries = ((JavaProject) javaProject).getResolvedClasspath();
                for (IClasspathEntry entry : entries) {
                    if (entry instanceof ClasspathEntry && entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
                        URL indexURL = ((ClasspathEntry) entry).getLibraryIndexLocation();
                        if (indexURL == null) {
                            continue;
                        }

                        IndexLocation sharedIndexLocation = IndexLocation.createIndexLocation(indexURL);
                        IndexLocation localIndexLocation = getLocalIndexLocation(javaCoreStateLocation, entry.getPath());
                        if (sharedIndexLocation.equals(localIndexLocation) || !resolvedIndexLocations.add(localIndexLocation)) {
                            continue;
                        }

                        File localIndexFile = localIndexLocation.getIndexFile();
                        if (localIndexLocation.exists()) {
                            File sharedIndexFile = sharedIndexLocation.getIndexFile();
                            if (sharedIndexFile == null || localIndexFile == null) {
                                continue;
                            }

                            JobHelpers.waitUntilIndexesReady(); // wait for index ready
                            if (indexManager.getIndex(sharedIndexLocation) != null) {
                                try {
                                    // current classpath entry is using the shared index, delete the unused local index directly.
                                    Files.deleteIfExists(localIndexFile.toPath());
                                } catch (IOException e) {
                                    JavaLanguageServerPlugin.logError(String.format("Failed to delete the local index %s: %s",
                                        localIndexFile.getName(), e.getMessage()));
                                }
                            } else {
                                Index localIndex = indexManager.getIndex(localIndexLocation);
                                if (localIndex == null || localIndex.hasChanged()) {
                                    continue;
                                } else {
                                    ReadWriteMonitor monitor = localIndex.monitor;
                                    if (monitor == null) { // index got deleted since acquired
                                        continue;
                                    }

                                    try {
                                        monitor.enterRead();
                                        copyIndex(localIndexFile, sharedIndexFile);
                                    } finally {
                                        monitor.exitRead();
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (JavaModelException e) {
                JavaLanguageServerPlugin.logException(e);
            }
        }
    }

    private static boolean copyIndex(File from, File to) {
        File tmpFile = new File(to.getParent(), to.getName() + "." + System.currentTimeMillis());
        try {
            mkdirsFor(tmpFile);
            Files.copy(from.toPath(), tmpFile.toPath(), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(to.toPath());
            return tmpFile.renameTo(to);
        } catch (IOException e) {
            JavaLanguageServerPlugin.logError(String.format("Failed to copy the local index %s to the shared index %s: %s",
                from.getName(), to.getName(), e.getMessage()));
        } finally {
            try {
                Files.deleteIfExists(tmpFile.toPath());
            } catch (IOException e) {
                // do nothing
            }
        }

        return false;
    }

    private static IndexLocation getLocalIndexLocation(IPath savedIndexPath, IPath containerPath) {
        String pathString = containerPath.toOSString();
        CRC32 checksumCalculator = new CRC32();
        checksumCalculator.update(pathString.getBytes());
        String fileName = Long.toString(checksumCalculator.getValue()) + ".index";
        // to share the indexLocation between the indexLocations and indexStates tables, get the key from the indexStates table
        return new FileIndexLocation(new File(savedIndexPath.toOSString(), fileName));
    }

    private static void mkdirsFor(File destination) {
        if ( destination.getParentFile() != null && !destination.getParentFile().exists()) {
            destination.getParentFile().mkdirs();
        }
    }
}
