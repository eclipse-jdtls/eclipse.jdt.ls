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

import static org.eclipse.jdt.ls.core.internal.ProjectUtils.getJavaSourceLevel;
import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

/**
 * @author Fred Bricon
 *
 */
public abstract class AbstractGradleBasedTest extends AbstractProjectsManagerBasedTest {

	protected IProject importSimpleJavaProject() throws Exception {
		String name = "simple-gradle";
		importProjects("gradle/"+name);
		IProject project = getProject(name);
		assertIsJavaProject(project);
		assertIsGradleProject(project);
		assertEquals("1.7", getJavaSourceLevel(project));
		return project;
	}

	protected void assertIsGradleProject(IProject project) {
		assertNotNull(project);
		assertTrue(project.getName() +" is missing the Gradle nature", ProjectUtils.isGradleProject(project));
	}

}
