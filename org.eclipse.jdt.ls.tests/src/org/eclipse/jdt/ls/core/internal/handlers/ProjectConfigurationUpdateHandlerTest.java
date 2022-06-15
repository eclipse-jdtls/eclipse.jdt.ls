/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.extended.ProjectConfigurationsUpdateParam;
import org.junit.Test;

public class ProjectConfigurationUpdateHandlerTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testUpdateConfiguration() throws Exception {
		importProjects("maven/multimodule");
		ProjectsManager pm = mock(ProjectsManager.class);
		when(pm.updateProject(any(IProject.class), anyBoolean())).thenReturn(null);
		ProjectConfigurationUpdateHandler handler = new ProjectConfigurationUpdateHandler(pm);
		IProject project = WorkspaceHelper.getProject("multimodule");
		handler.updateConfiguration(new TextDocumentIdentifier(project.getLocationURI().toString()));
		verify(pm, times(1)).updateProject(any(IProject.class), eq(true));
	}

	@Test
	public void testUpdateConfigurations() throws Exception {
		importProjects("maven/multimodule");
		ProjectsManager pm = mock(ProjectsManager.class);
		when(pm.updateProject(any(IProject.class), anyBoolean())).thenReturn(null);
		ProjectConfigurationUpdateHandler handler = new ProjectConfigurationUpdateHandler(pm);
		List<TextDocumentIdentifier> list = new ArrayList<>();
		IProject project = WorkspaceHelper.getProject("module1");
		list.add(new TextDocumentIdentifier(project.getLocationURI().toString()));
		project = WorkspaceHelper.getProject("module2");
		list.add(new TextDocumentIdentifier(project.getLocationURI().toString()));

		ProjectConfigurationsUpdateParam param = new ProjectConfigurationsUpdateParam(list);

		handler.updateConfigurations(param.getIdentifiers());
		verify(pm, times(2)).updateProject(any(IProject.class), eq(true));
	}
}
