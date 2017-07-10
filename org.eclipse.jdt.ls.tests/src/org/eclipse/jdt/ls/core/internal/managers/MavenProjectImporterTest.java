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
import static org.junit.Assert.assertNull;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.junit.Test;

/**
 * @author Fred Bricon
 */
public class MavenProjectImporterTest extends AbstractMavenBasedTest {

	private static final String PROJECT1_PATTERN = "**/project1";

	@Test
	public void testImportSimpleJavaProject() throws Exception {
		importSimpleJavaProject();
	}

	@Test
	public void testJavaImportExclusions() throws Exception {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		try {
			javaImportExclusions.add(PROJECT1_PATTERN);
			List<IProject> projects = importProjects("maven/multi");
			assertEquals(2, projects.size());//default + project 2
			IProject project1 = WorkspaceHelper.getProject("project1");
			assertNull(project1);
			IProject project2 = WorkspaceHelper.getProject("project2");
			assertIsMavenProject(project2);
		} finally {
			javaImportExclusions.remove(PROJECT1_PATTERN);
		}
	}

}