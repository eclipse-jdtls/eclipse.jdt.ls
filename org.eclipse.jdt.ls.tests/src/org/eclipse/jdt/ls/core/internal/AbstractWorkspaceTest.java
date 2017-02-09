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
package org.eclipse.jdt.ls.core.internal;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * @author Fred Bricon
 *
 */
public abstract class AbstractWorkspaceTest {

	@BeforeClass
	public static void initWorkspace() {
		WorkspaceHelper.initWorkspace();
	}

	@AfterClass
	public static void cleanWorkspace() {
		WorkspaceHelper.deleteAllProjects();
	}


}
