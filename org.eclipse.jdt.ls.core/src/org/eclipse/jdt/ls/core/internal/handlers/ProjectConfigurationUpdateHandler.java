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
package org.eclipse.jdt.ls.core.internal.handlers;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.TextDocumentIdentifier;

/**
 * @author Fred Bricon
 *
 */
public class ProjectConfigurationUpdateHandler {

	private ProjectsManager projectManager;

	ProjectConfigurationUpdateHandler(ProjectsManager projectManager) {
		this.projectManager = projectManager;
	}

	public void updateConfiguration(TextDocumentIdentifier param) {
		IFile file = JDTUtils.findFile(param.getUri());
		if (file == null) {
			return;
		}
		// most likely the handler is invoked intentionally by the user, that's why
		// we force the update despite no changes of in build descriptor being made
		projectManager.updateProject(file.getProject(), true);
	}
}
