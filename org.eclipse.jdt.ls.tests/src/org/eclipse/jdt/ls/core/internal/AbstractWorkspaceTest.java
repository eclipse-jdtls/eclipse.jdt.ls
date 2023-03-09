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

import java.util.Map;

import org.eclipse.lsp4j.ClientCapabilities;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * @author Fred Bricon
 *
 */
public abstract class AbstractWorkspaceTest {

	@BeforeClass
	public static void initWorkspace() throws Exception {
		JavaLanguageServerPlugin.getPreferencesManager().updateClientPrefences(new ClientCapabilities(), Map.of());
		WorkspaceHelper.initWorkspace();
	}

	@AfterClass
	public static void cleanWorkspace() {
		WorkspaceHelper.deleteAllProjects();
	}

}
