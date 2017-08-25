/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceResult;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author xuzho
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class BuildWorkspaceHandlerTest extends AbstractProjectsManagerBasedTest {

	private BuildWorkspaceHandler handler;
	private IFile file;

	@Before
	public void setUp() throws Exception {
		handler = new BuildWorkspaceHandler();
		importProjects("maven/salut2");
		file = linkFilesToDefaultProject("singlefile/s1.java");
	}

	@Test
	public void testSucceedCase() throws Exception {
		BuildWorkspaceResult result = handler.buildWorkspace(monitor);
		waitForBackgroundJobs();
		assertTrue("BuildWorkspaceStatus is: " + result.getStatus(), result != null && result.getStatus() == BuildWorkspaceStatus.SUCCEED);
	}

	@Test
	public void testFailedCase() throws Exception {
		String codeWithError = "public class s2 {\n"+
				"	public static void main(String[] args){\n"+
				"		int ss = 1;;\n"+
				"	}\n"+
				"}";
		try (InputStream stream = new ByteArrayInputStream(codeWithError.getBytes(StandardCharsets.UTF_8))) {
			file.setContents(stream, true, false, monitor);
			waitForBackgroundJobs();
			BuildWorkspaceResult result = handler.buildWorkspace(monitor);
			waitForBackgroundJobs();
			assertTrue(result != null && result.getStatus() == BuildWorkspaceStatus.FAILED);
		}
	}

	@Test
	public void testCanceledCase() throws Exception {
		monitor.setCanceled(true);
		BuildWorkspaceResult result = handler.buildWorkspace(monitor);
		waitForBackgroundJobs();
		assertTrue(result != null && result.getStatus() == BuildWorkspaceStatus.CANCELLED);
	}
}
