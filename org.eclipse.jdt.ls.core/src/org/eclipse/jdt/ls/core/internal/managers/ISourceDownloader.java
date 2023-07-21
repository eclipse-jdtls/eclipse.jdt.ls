/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IPackageFragmentRoot;

/**
 * Service to automatically discover and attach sources to unknown class files.
 *
 * @author Fred Bricon
 *
 */
public interface ISourceDownloader {
	/**
	 * No download record exists for the element.
	 */
	public int DOWNLOAD_NONE = 0x001;
	/**
	 * The element's source is missing and about to
	 * initiate a source download request.
	 */
	public int DOWNLOAD_REQUESTED = 0x002;
	/**
	 * The download waiting job has either finished
	 * or expired.
	 */
	public int DOWNLOAD_WAIT_JOB_DONE = 0x004;

	/**
	 * Discovers and attaches sources to the given {@link IClassFile}'s parent
	 * {@link IClasspathEntry}, if it's a jar file.
	 *
	 * @param classFile
	 *            the file to identify and search sources for
	 * @param monitor
	 *            a progress monitor
	 * @throws CoreException
	 */
	public void discoverSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException;

	/**
	 * Gets the status of source download job for the jar element.
	 * @param root
	 *            the root jar element
	 * @return the status of source download job
	 */
	public int getDownloadStatus(IPackageFragmentRoot root);

	/**
	 * Cleanup the download status for the jar element.
	 * @param root
	 *            the root jar element
	 */
	default void clearDownloadStatus(IPackageFragmentRoot root) { }
}
