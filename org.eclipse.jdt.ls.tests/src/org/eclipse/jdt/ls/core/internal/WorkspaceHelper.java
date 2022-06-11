/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaCore;

/**
 * @author Fred Bricon
 *
 */
public final class WorkspaceHelper {

	private static final int DELETE_MAX_WAIT = 1000;
	private static final boolean DELETE_DEBUG = Boolean.getBoolean("jdt.ls.test.delete.debug");
	private static int DELETE_MAX_TIME = 0;

	private WorkspaceHelper() {
		//No instances allowed
	}

	public static void initWorkspace() throws CoreException {
		JavaLanguageServerPlugin.getProjectsManager().initializeProjects(Collections.emptyList(), new NullProgressMonitor());
		assertEquals(1, getAllProjects().size());
	}

	public static IProject getProject(String name) {
		IProject project = getWorkspaceRoot().getProject(name);
		return project.exists() ? project : null;
	}

	public static void deleteAllProjects() {
		getAllProjects().forEach(p -> delete(p));
	}

	public static List<IProject> getAllProjects() {
		return Arrays.asList(getWorkspaceRoot().getProjects());
	}

	public static IWorkspaceRoot getWorkspaceRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	public static void delete(IProject project) {
		try {
			if (project.exists() && !project.isOpen()) { // force opening so that project can be deleted without logging (see bug 23629)
				project.open(null);
			}
			IStatus status = deleteResource(project);
			if (!status.isOK()) {
				JavaLanguageServerPlugin.log(status);
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	/**
	 * Delete a file or directory and insure that the file is no longer present on
	 * file system. In case of directory, delete all the hierarchy underneath.
	 *
	 * copied from org.eclipse.jdt.core.tests.util.Util.delete(IResource)
	 *
	 * @param resource
	 *            The resource to delete
	 * @return true iff the file was really delete, false otherwise
	 */
	public static IStatus deleteResource(IResource resource) {
		IStatus status = null;
		try {
			resource.delete(true, null);
			if (isResourceDeleted(resource)) {
				return Status.OK_STATUS;
			}
		} catch (CoreException e) {
			status = e.getStatus();
		}
		boolean deleted = waitUntilResourceDeleted(resource);
		if (deleted) {
			return Status.OK_STATUS;
		}
		if (status != null) {
			return status;
		}
		return new Status(IStatus.ERROR, JavaCore.PLUGIN_ID, "Cannot delete resource " + resource);
	}

	/**
	 * Returns whether a resource is really deleted or not. Does not only rely on
	 * {@link IResource#isAccessible()} method but also look if it's not in its
	 * parent children {@link #getParentChildResource(IResource)}.
	 *
	 * copied from org.eclipse.jdt.core.tests.util.Util.isResourceDeleted(IResource)
	 *
	 * @param resource
	 *            The resource to test if deleted
	 * @return true if the resource is not accessible and was not found in its
	 *         parent children.
	 */
	public static boolean isResourceDeleted(IResource resource) {
		return !resource.isAccessible() && getParentChildResource(resource) == null;
	}

	/**
	 * Returns parent's child resource matching the given resource or null if not
	 * found.
	 *
	 * copied from
	 * org.eclipse.jdt.core.tests.util.Util.getParentChildResource(IResource)
	 *
	 * @param resource
	 *            The searched file in parent
	 * @return The parent's child matching the given file or null if not found.
	 */
	private static IResource getParentChildResource(IResource resource) {
		IContainer parent = resource.getParent();
		if (parent == null || !parent.exists()) {
			return null;
		}
		try {
			IResource[] members = parent.members();
			int length = members == null ? 0 : members.length;
			if (length > 0) {
				for (int i = 0; i < length; i++) {
					if (members[i] == resource) {
						return members[i];
					} else if (members[i].equals(resource)) {
						return members[i];
					} else if (members[i].getFullPath().equals(resource.getFullPath())) {
						return members[i];
					}
				}
			}
		} catch (CoreException ce) {
			// skip
		}
		return null;
	}

	/**
	 * Returns the parent's child file matching the given file or null if not found.
	 *
	 * copied from
	 * org.eclipse.jdt.core.tests.util.Util.getParentChildFile(IResource)
	 *
	 * @param file
	 *            The searched file in parent
	 * @return The parent's child matching the given file or null if not found.
	 */
	private static File getParentChildFile(File file) {
		File parent = file.getParentFile();
		if (parent == null || !parent.exists()) {
			return null;
		}
		File[] files = parent.listFiles();
		int length = files == null ? 0 : files.length;
		if (length > 0) {
			for (int i = 0; i < length; i++) {
				if (files[i] == file) {
					return files[i];
				} else if (files[i].equals(file)) {
					return files[i];
				} else if (files[i].getPath().equals(file.getPath())) {
					return files[i];
				}
			}
		}
		return null;
	}

	/**
	 * Wait until a resource is _really_ deleted on file system.
	 *
	 * copied from
	 * org.eclipse.jdt.core.tests.util.Util.waitUntilResourceDeleted(IResource)
	 *
	 * @param resource
	 *            Deleted resource
	 * @return true if the file was finally deleted, false otherwise
	 */
	public static boolean waitUntilResourceDeleted(IResource resource) {
		IPath location = resource.getLocation();
		if (location == null) {
			JavaLanguageServerPlugin.logInfo("	!!! ERROR: " + resource + " getLocation() returned null!!!");
			return false;
		}
		File file = location.toFile();
		if (DELETE_DEBUG) {
			JavaLanguageServerPlugin.logInfo("Problems occured while deleting resource " + resource);
			JavaLanguageServerPlugin.logException(new Exception());
			JavaLanguageServerPlugin.logInfo("Wait for (" + DELETE_MAX_WAIT + "ms max): ");
		}
		int count = 0;
		int delay = 10; // ms
		int maxRetry = DELETE_MAX_WAIT / delay;
		int time = 0;
		while (count < maxRetry) {
			try {
				count++;
				Thread.sleep(delay);
				time += delay;
				if (time > DELETE_MAX_TIME) {
					DELETE_MAX_TIME = time;
				}
				if (DELETE_DEBUG) {
					System.out.print('.');
				}
				if (resource.isAccessible()) {
					try {
						resource.delete(true, null);
						if (isResourceDeleted(resource) && isFileDeleted(file)) {
							// SUCCESS
							if (DELETE_DEBUG) {
								JavaLanguageServerPlugin.logInfo("=> resource really removed after " + time + "ms (max=" + DELETE_MAX_TIME + "ms)");
							}
							return true;
						}
					} catch (CoreException e) {
						//	skip
					}
				}
				if (isResourceDeleted(resource) && isFileDeleted(file)) {
					// SUCCESS
					if (DELETE_DEBUG) {
						JavaLanguageServerPlugin.logInfo("=> resource disappeared after " + time + "ms (max=" + DELETE_MAX_TIME + "ms)");
					}
					return true;
				}
				// Increment waiting delay exponentially
				if (count >= 10 && delay <= 100) {
					count = 1;
					delay *= 10;
					maxRetry = DELETE_MAX_WAIT / delay;
					if ((DELETE_MAX_WAIT % delay) != 0) {
						maxRetry++;
					}
				}
			} catch (InterruptedException ie) {
				break; // end loop
			}
		}
		if (!DELETE_DEBUG) {
			JavaLanguageServerPlugin.logInfo("	- problems occured while deleting resource " + resource);
			JavaLanguageServerPlugin.logException(new Exception());
		}
		JavaLanguageServerPlugin.logInfo("	!!! ERROR: " + resource + " was never deleted even after having waited " + DELETE_MAX_TIME + "ms!!!");
		return false;
	}

	/**
	 * Returns whether a file is really deleted or not. Does not only rely on
	 * {@link File#exists()} method but also look if it's not in its parent children
	 * {@link #getParentChildFile(File)}.
	 *
	 * copied from org.eclipse.jdt.core.tests.util.Util.isFileDeleted(IFile)
	 *
	 * @param file
	 *            The file to test if deleted
	 * @return true if the file does not exist and was not found in its parent
	 *         children.
	 */
	public static boolean isFileDeleted(File file) {
		return !file.exists() && getParentChildFile(file) == null;
	}

}
