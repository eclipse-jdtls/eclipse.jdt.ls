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
		IProject project = importGradleProject("simple-gradle");
		assertIsJavaProject(project);
		assertEquals("1.7", getJavaSourceLevel(project));
		return project;
	}

	protected IProject importGradleProject(String name) throws Exception {
		importProjects("gradle/"+name);
		IProject project = getProject(name);
		assertIsGradleProject(project);
		return project;
	}

	protected void assertIsGradleProject(IProject project) {
		assertNotNull(project);
		assertTrue(project.getName() +" is missing the Gradle nature", ProjectUtils.isGradleProject(project));
	}

}
