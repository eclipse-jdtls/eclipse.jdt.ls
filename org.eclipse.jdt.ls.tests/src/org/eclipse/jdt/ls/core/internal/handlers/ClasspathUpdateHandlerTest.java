/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClasspathUpdateHandlerTest extends AbstractProjectsManagerBasedTest {
	@Mock
	private JavaClientConnection connection;

	private ClasspathUpdateHandler handler;

	@Before
	public void setup() throws Exception {
		handler = new ClasspathUpdateHandler(connection);
		handler.addElementChangeListener();;
	}

	@After
	@Override
	public void cleanUp() throws Exception {
		super.cleanUp();
		handler.removeElementChangeListener();
	}

	@Test
	public void testProjectConfigurationIsNotUpToDate() throws Exception {
		//import project
		importProjects("maven/salut");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut");
		IFile pom = project.getFile("/pom.xml");
		assertTrue(pom.exists());
		ResourceUtils.setContent(pom, ResourceUtils.getContent(pom).replaceAll("<version>3.5</version>", "<version>3.6</version>"));

		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, monitor);

		verify(connection, times(1)).sendActionableNotification(any(ActionableNotification.class));
	}
}