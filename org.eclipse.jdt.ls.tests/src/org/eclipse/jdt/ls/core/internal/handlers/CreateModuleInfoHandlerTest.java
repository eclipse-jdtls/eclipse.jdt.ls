/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class CreateModuleInfoHandlerTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testCreateModuleInfo() throws Exception {
		importProjects("maven/modular-project2");
		IProject project = WorkspaceHelper.getProject("modular-project2");

		String moduleInfoUri = CreateModuleInfoHandler.createModuleInfo(project.getLocationURI().toString(), new NullProgressMonitor());

		File file = ResourceUtils.toFile(new URI(moduleInfoUri));
		String content = Files.toString(file, Charsets.UTF_8);
		assertTrue(content.contains("exports com.example;"));
		assertTrue(content.contains("requires xml.apis;"));
	}

	@Test
	public void testConvertToModuleName() {
		assertEquals("a.b", CreateModuleInfoHandler.convertToModuleName("..a-b.."));
	}

}
