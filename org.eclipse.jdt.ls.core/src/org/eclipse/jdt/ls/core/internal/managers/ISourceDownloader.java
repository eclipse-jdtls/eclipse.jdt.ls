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

/**
 * Service to automatically discover and attach sources to unknown class files.
 *
 * @author Fred Bricon
 *
 */
public interface ISourceDownloader {

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


}
