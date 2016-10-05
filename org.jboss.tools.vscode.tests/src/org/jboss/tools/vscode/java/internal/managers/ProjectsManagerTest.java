/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.managers;

import static org.jboss.tools.vscode.java.internal.JobHelpers.waitForJobsToComplete;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class ProjectsManagerTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testCreateDefaultProject() throws Exception {
		List<IProject> projects = new ArrayList<>();
		projectsManager.createProject(null, projects, monitor);
		waitForJobsToComplete();
		assertEquals(1, projects.size());
		IProject result = projects.get(0);
		assertNotNull(result);
		assertEquals(projectsManager.getDefaultProject(), result);
		assertTrue("the default project doesn't exist", result.exists());
	}

}
