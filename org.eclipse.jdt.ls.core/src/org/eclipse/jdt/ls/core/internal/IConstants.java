/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
}
