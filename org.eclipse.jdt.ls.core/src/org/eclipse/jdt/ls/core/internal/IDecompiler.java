/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;

/**
 * Interface for content providers that decompile class files
 */
public interface IDecompiler extends IContentProvider {
	/**
	 * Provide decompiled source code from a resource.
	 *
	 * @param classFile
	 *            the class file to decompile
	 * @param monitor
	 *            monitor of the activity progress
	 * @return text content or <code>null</code>
	 * @throws CoreException
	 */
	public String getSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException;

	/**
	 * Provide decompiled source code from a resource.
	 *
	 * @param classFile
	 *            the class file to decompile
	 * @param monitor
	 *            monitor of the activity progress
	 * @return text content or <code>null</code>
	 * @throws CoreException
	 */
	default DecompilerResult getDecompiledSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		String source = getSource(classFile, monitor);
		return source == null ? null : new DecompilerResult(source);
	}

	/**
	 * Provides the line mappings from the original source to decompiled source.
	 * Its format is as follows, in ascending order by original line.
	 * - [i]: the original line
	 * - [i+1]: the decompiled line
	 *
	 * @param classFile
	 *             the class file to decompile
	 * @param contents
	 *             the decompiled contents
	 * @param monitor
	 *             the progress monitor
	 * @return the original line mappings if existed or <code>null</code>
	 * @throws CoreException
	 */
	default int[] getOriginalLineMappings(IClassFile classFile, String contents, IProgressMonitor monitor) throws CoreException {
		return null;
	}

	/**
	 * Provides the line mappings from the decompiled source to the original source.
	 * Its format is as follows, in ascending order by decompiled line.
	 * - [i]: the decompiled line
	 * - [i+1]: the original line
	 *
	 * @param classFile
	 *             the class file to decompile
	 * @param contents
	 *             the decompiled contents
	 * @param monitor
	 *             the progress monitor
	 * @return the decompiled line mappings if existed or <code>null</code>
	 * @throws CoreException
	 */
	default int[] getDecompiledLineMappings(IClassFile classFile, String contents, IProgressMonitor monitor) throws CoreException {
		return null;
	}
}
