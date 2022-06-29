/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.TextDocumentIdentifier;

/**
 * @author Fred Bricon
 *
 */
public class ProjectConfigurationUpdateHandler {

	private ProjectsManager projectManager;

	public ProjectConfigurationUpdateHandler(ProjectsManager projectManager) {
		this.projectManager = projectManager;
	}

	/**
	 * Update the projects' configurations (build files).
	 *
	 * @param identifiers the identifiers which may point to the projects' paths or
	 * files that belong to some projects in the workspace.
	 */
	public void updateConfigurations(List<TextDocumentIdentifier> identifiers) {
		Collection<IProject> projects = ProjectUtils.getProjectsFromDocumentIdentifiers(identifiers);

		for (IProject project : projects) {
			// most likely the handler is invoked intentionally by the user, that's why
			// we force the update despite no changes of in build descriptor being made
			projectManager.updateProject(project, true);
		}
	}

	public void updateConfiguration(TextDocumentIdentifier param) {
		updateConfigurations(Arrays.asList(param));
	}
}
