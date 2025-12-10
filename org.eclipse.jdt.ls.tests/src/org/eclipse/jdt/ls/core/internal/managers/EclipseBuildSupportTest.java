/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author snjeza
 *
 */
@ExtendWith(MockitoExtension.class)
public class EclipseBuildSupportTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testUpdateJar() throws Exception {
		importProjects("eclipse/updatejar");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("updatejar");
		assertIsJavaProject(project);
		List<IMarker> errors = ResourceUtils.getErrorMarkers(project);
		assertEquals(2, errors.size(), "Unexpected errors " + ResourceUtils.toString(errors));
		File projectFile = project.getLocation().toFile();
		File validFooJar = new File(projectFile, "foo.jar");
		File destLib = new File(projectFile, "lib");
		FileUtils.copyFileToDirectory(validFooJar, destLib);
		File newJar = new File(destLib, "foo.jar");
		projectsManager.fileChanged(newJar.toPath().toUri().toString(), CHANGE_TYPE.CREATED);
		waitForBackgroundJobs();
		errors = ResourceUtils.getErrorMarkers(project);
		assertEquals(0, errors.size(), "Unexpected errors " + ResourceUtils.toString(errors));

	}


}
