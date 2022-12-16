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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaModel;
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
	private static boolean resourceChangeRegistered = false;
	private static Map<IPath, Long> externalTimeStamps = null;

	public static void copyIndexesToSharedLocation() {
		// common index location for all workspaces
		final String SHARED_INDEX_LOCATION = System.getProperty("jdt.core.sharedIndexLocation");
		if (JavaModelManager.getIndexManager() == null || StringUtils.isBlank(SHARED_INDEX_LOCATION)) {
			return;
		}

		JobHelpers.waitUntilIndexesReady();
		getExternalLibTimeStamps(); // init load of externalLibTimeStamps
		registerResourceChangeListener();
		copyIndexesToSharedLocation(ProjectUtils.getJavaProjects());
	}

	private static synchronized void registerResourceChangeListener() {
		if (resourceChangeRegistered) {
			return;
		}
		resourceChangeRegistered = true;
		ResourcesPlugin.getWorkspace().addResourceChangeListener(new IResourceChangeListener() {
			@Override
			public void resourceChanged(IResourceChangeEvent event) {
				IJavaProject [] projects = null;
				Object obj = event.getSource();
				if (obj instanceof IProject project) {
					if (ProjectUtils.isJavaProject(project)) {
						projects = new IJavaProject[]{ JavaCore.create(project) };
					}
				} else if (obj instanceof IWorkspace) {
					projects = ProjectUtils.getJavaProjects();
				}

				if (projects != null && projects.length > 0) {
					JavaModelManager.getIndexManager().waitForIndex(true, null);
					copyIndexesToSharedLocation(projects);
				}
			}
		}, IResourceChangeEvent.PRE_REFRESH);
	}

	private static void copyIndexesToSharedLocation(IJavaProject[] javaProjects) {
		Set<ClasspathEntry> processedEntries = new HashSet<>();
		Set<ClasspathEntry> deferredEntries = new HashSet<>();
		for (IJavaProject javaProject : javaProjects) {
			try {
				if (javaProject == null || !javaProject.exists()) {
					continue;
				}

				IClasspathEntry[] entries = ((JavaProject) javaProject).getResolvedClasspath();
				for (IClasspathEntry entry : entries) {
					if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
						processLibraryIndex(javaProject.getProject(), (ClasspathEntry) entry, processedEntries, deferredEntries);
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}

		if (!deferredEntries.isEmpty()) {
			JobHelpers.waitUntilIndexesReady();
			processedEntries = new HashSet<>();
			for (ClasspathEntry entry : deferredEntries) {
				processLibraryIndex(entry, processedEntries);
			}
		}
	}

	private static void processLibraryIndex(ClasspathEntry libraryEntry, Set<ClasspathEntry> processedEntries) {
		processLibraryIndex(null, libraryEntry, processedEntries, null);
	}

	private static void processLibraryIndex(IProject project, ClasspathEntry libraryEntry, Set<ClasspathEntry> processedEntries, Set<ClasspathEntry> deferredEntries) {
		if (!processedEntries.add(libraryEntry)) {
			return;
		}

		IPath libraryPath = libraryEntry.getPath();
		Object libraryFile = JavaModel.getTarget(libraryPath, true);
		/**
		 * If the library is a jar inside the workspace, it will be resolved to a IFile.
		 * If it is an external jar, it will be resolved to a java.io.File.
		 *
		 * Currently JDT only supports shared indexes for external jars, we can skip
		 * the processing of non-external jars.
		 */
		boolean isExternalJar = libraryFile instanceof File;
		if (!isExternalJar) {
			return;
		}

		IndexLocation sharedIndexLocation = getSharedIndexLocation(libraryEntry);
		IndexLocation localIndexLocation = getLocalIndexLocation(libraryPath);
		if (sharedIndexLocation == null || Objects.equals(sharedIndexLocation, localIndexLocation)) {
			return;
		}

		long lastModifiedTime = ((File) libraryFile).lastModified();
		File localIndexFile = localIndexLocation.getIndexFile();
		IndexManager indexManager = JavaModelManager.getIndexManager();
		// shared index is currently in use.
		if (indexManager.getIndex(sharedIndexLocation) != null) {
			Long oldTimestamp = getExternalLibTimeStamps().get(libraryPath);
			long newTimeStamp = getLibTimeStamp((File) libraryFile);
			boolean libChanged = oldTimestamp != null && oldTimestamp.longValue() != newTimeStamp;
			if (!libChanged || sharedIndexLocation.lastModified() == lastModifiedTime) {
				try {
					Files.deleteIfExists(localIndexFile.toPath());
				} catch (IOException e) {
					JavaLanguageServerPlugin.logError(String.format("Failed to delete the local index %s: %s",
						localIndexFile.getName(), e.getMessage()));
				}

				return;
			}

			if (project == null || deferredEntries == null) {
				return;
			}

			// The shared index is outdated, remove it so that it is forced to be re-indexed in local workspace.
			indexManager.removeIndex(libraryPath);
			indexManager.indexLibrary(libraryPath, project, localIndexLocation.getUrl(), true);
			deferredEntries.add(libraryEntry);
			return;
		}

		// local index is currently in use.
		if (lastModifiedTime > 0 && localIndexLocation.exists()) {
			File sharedIndexFile = sharedIndexLocation.getIndexFile();
			if (sharedIndexFile == null || localIndexFile == null) {
				return;
			}

			Index localIndex = indexManager.getIndex(localIndexLocation);
			if (localIndex == null || localIndex.hasChanged()) {
				return;
			}

			ReadWriteMonitor monitor = localIndex.monitor;
			if (monitor == null) { // index got deleted since acquired
				return;
			}

			try {
				monitor.enterRead();
				copyIndexFile(localIndexFile, sharedIndexFile, lastModifiedTime);
				getExternalLibTimeStamps().put(libraryPath, getLibTimeStamp((File) libraryFile));
			} finally {
				monitor.exitRead();
			}
		}
	}

	private static boolean copyIndexFile(File from, File to, long lastModified) {
		long toFileModifiedTime = to.lastModified();
		// The file contents are not changed, skip the file copy
		if (toFileModifiedTime != 0 && toFileModifiedTime == lastModified) {
			return false;
		}

		File tmpFile = new File(to.getParent(), to.getName() + "." + System.currentTimeMillis());
		try {
			mkdirsFor(tmpFile);
			Files.copy(from.toPath(), tmpFile.toPath(), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
			Files.deleteIfExists(to.toPath());
			boolean success = tmpFile.renameTo(to);
			// Keep the lastModified timestamp of the shared index same as the original library file.
			to.setLastModified(lastModified);
			return success;
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

	private static IndexLocation getSharedIndexLocation(ClasspathEntry libraryEntry) {
		URL indexURL = libraryEntry.getLibraryIndexLocation();
		if (indexURL == null) {
			return null;
		}

		return IndexLocation.createIndexLocation(indexURL);
	}

	private static IndexLocation getLocalIndexLocation(IPath libraryPath) {
		String pathString = libraryPath.toOSString();
		CRC32 checksumCalculator = new CRC32();
		checksumCalculator.update(pathString.getBytes());
		String fileName = Long.toString(checksumCalculator.getValue()) + ".index";
		IPath statePath = JavaCore.getPlugin().getStateLocation();
		return new FileIndexLocation(new File(statePath.toOSString(), fileName));
	}

	private static void mkdirsFor(File destination) {
		if (destination.getParentFile() != null && !destination.getParentFile().exists()) {
			destination.getParentFile().mkdirs();
		}
	}

	private synchronized static Map<IPath, Long> getExternalLibTimeStamps() {
		if (externalTimeStamps == null) {
			Map<IPath, Long> timeStamps = new HashMap<>();
			File timestampsFile = getExternalLibTimeStampsFile();
			DataInputStream in = null;
			try {
				in = new DataInputStream(new BufferedInputStream(new FileInputStream(timestampsFile)));
				int size = in.readInt();
				while (size-- > 0) {
					String key = in.readUTF();
					long timestamp = in.readLong();
					timeStamps.put(Path.fromPortableString(key), Long.valueOf(timestamp));
				}
			} catch (IOException e) {
				if (timestampsFile.exists()) {
					JavaLanguageServerPlugin.logException("Unable to read external time stamps", e);
				}
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						// nothing we can do: ignore
					}
				}
			}

			externalTimeStamps = timeStamps;
		}

		return externalTimeStamps;
	}

	private static File getExternalLibTimeStampsFile() {
		return JavaCore.getPlugin().getStateLocation().append("externalLibsTimeStamps").toFile();
	}

	/*
	 * Answer a combination of the lastModified stamp and the size.
	 * Used for detecting external JAR changes
	 */
	private static long getLibTimeStamp(File file) {
		return file.lastModified() + file.length();
	}
}
