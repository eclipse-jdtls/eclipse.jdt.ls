/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * @author Fred Bricon
 *
 */
public interface IBuildSupport {

	boolean applies(IProject project);

	boolean isBuildFile(IResource resource);

	/**
	 *
	 * @param resource
	 *            - a project to update
	 * @param force
	 *            - defines if the <code>project</code> must be updated despite of
	 *            no changes in the build descriptor are made
	 * @param monitor
	 * @throws CoreException
	 */
	void update(IProject resource, boolean force, IProgressMonitor monitor) throws CoreException;

	/**
	 * Is equal to a non-forced update: {@code update(resource, false, monitor)}
	 */
	default void update(IProject resource, IProgressMonitor monitor) throws CoreException {
		update(resource, false, monitor);
	}
}
