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

                        boolean needDeleteLocalIndex = false;
                        File localIndexFile = localIndexLocation.getIndexFile();
                        if (localIndexLocation.exists()) {
                            File sharedIndexFile = sharedIndexLocation.getIndexFile();
                            if (sharedIndexFile == null || localIndexFile == null) {
                                continue;
                            }

                            if (indexManager.getIndex(sharedIndexLocation) != null) {
                                // current classpath entry is using the shared index, delete local index directly.
                                needDeleteLocalIndex = true;
                            } else {
                                Index localIndex = indexManager.getIndex(localIndexLocation);
                                if (localIndex == null) {
                                    continue;
                                }

                                ReadWriteMonitor monitor = localIndex.monitor;
                                if (monitor == null) { // index got deleted since acquired
                                    continue;
                                }
    
                                File tmpFile = new File(sharedIndexFile.getParent(), sharedIndexFile.getName() + "." + System.currentTimeMillis());
                                try {
                                    monitor.enterRead();
                                    mkdirsFor(tmpFile);
                                    Files.copy(localIndexFile.toPath(), tmpFile.toPath(), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
                                    Files.deleteIfExists(sharedIndexFile.toPath());
                                    needDeleteLocalIndex = tmpFile.renameTo(sharedIndexFile);
                                } catch (IOException e) {
                                    JavaLanguageServerPlugin.logError(String.format("Failed to copy the local index %s to the shared index %s: %s",
                                        localIndexFile.getName(), sharedIndexFile.getName(), e.getMessage()));
                                } finally {
                                    monitor.exitRead();
                                    try {
                                        Files.deleteIfExists(tmpFile.toPath());
                                    } catch (IOException e) {
                                        // do nothing
                                    }
                                }
                            }
                        }

                        if (needDeleteLocalIndex) {
                            try {
                                // Remove the local index after it has been copied to the shared index location.
                                Files.deleteIfExists(localIndexFile.toPath());
                            } catch (IOException e) {
                                JavaLanguageServerPlugin.logError(String.format("Failed to delete the local index %s: %s",
                                    localIndexFile.getName(), e.getMessage()));
                            }
                        }
                    }
                }
            } catch (JavaModelException e) {
                JavaLanguageServerPlugin.logException(e);
            }
        }
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
