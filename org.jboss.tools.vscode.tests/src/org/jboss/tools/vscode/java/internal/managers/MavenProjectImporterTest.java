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

import static org.jboss.tools.vscode.java.internal.ProjectUtils.getJavaSourceLevel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.resources.IProject;
import org.jboss.tools.vscode.java.internal.ProjectUtils;
import org.junit.Test;

/**
 * @author Fred Bricon
 */
public class MavenProjectImporterTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void importSimpleJavaProject() throws Exception {
		IProject project = importProject("maven/salut");
		assertIsJavaProject(project);
		assertIsJavaProject(project);
		assertEquals("1.7", getJavaSourceLevel(project));
	}

	protected void assertIsMavenProject(IProject project) {
		assertNotNull(project);
		assertTrue(project.getName() +" is missing the Maven nature", ProjectUtils.isMavenProject(project));
	}

}
