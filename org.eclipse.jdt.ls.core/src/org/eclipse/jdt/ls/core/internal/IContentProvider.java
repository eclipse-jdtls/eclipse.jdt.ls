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

import java.net.URI;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

/**
 * Interface for classes that provide text content from resources.
 *
 * @see IDecompiler
 */
public interface IContentProvider {
	/**
	 * Accept preferences which may contain configuration for this content provider.
	 *
	 * @param preferences
	 */
	default void setPreferences(Preferences preferences) {};

	/**
	 * Provide text content from a resource.
	 *
	 * @param uri
	 *            the URI of the resource to get content from
	 * @param monitor
	 *            monitor of the activity progress
	 * @return text content or <code>null</code>
	 * @throws CoreException
	 */
	String getContent(URI uri, IProgressMonitor monitor) throws CoreException;
}
