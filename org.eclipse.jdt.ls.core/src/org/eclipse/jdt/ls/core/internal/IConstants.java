/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
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

/**
 * Holds commonly used constants in jdt.ls
 *
 * @author Fred Bricon
 */
public interface IConstants {

	/**
	 * Plugin id
	 */
	public static final String PLUGIN_ID = "org.eclipse.jdt.ls.core";

	/**
	 * Is workspace initialized
	 */
	public static final String WORKSPACE_INITIALIZED = "workspaceInitialized";

	/**
	 * Jobs family id
	 */
	public static final String JOBS_FAMILY = PLUGIN_ID + ".jobs";

	/**
	 * Update project job family id
	 */
	public static final String UPDATE_PROJECT_FAMILY = JOBS_FAMILY + ".updateProject";

	/**
	 * Update workspace folders job family id
	 */
	public static final String UPDATE_WORKSPACE_FOLDERS_FAMILY = JOBS_FAMILY + ".updateWorkspaceFolders";

	public static final String CHANGE_METHOD_SIGNATURE = "org.eclipse.jdt.ls.change.method.signature";
}
