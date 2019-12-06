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
}
