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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class GradleProjectImporterTest extends AbstractGradleBasedTest{

	@Test
	public void importSimpleGradleProject() throws Exception {
		importSimpleJavaProject();
	}

	@Test
	public void importNestedGradleProject() throws Exception {
		List<IProject> projects = importProjects("gradle/nested");
		assertEquals(3, projects.size());//default + 2 gradle projects
		IProject gradle1 = WorkspaceHelper.getProject("gradle1");
		assertIsGradleProject(gradle1);
		IProject gradle2 = WorkspaceHelper.getProject("gradle2");
		assertIsGradleProject(gradle2);
	}
}
