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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author xuzho
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class BuildWorkspaceHandlerTest extends AbstractProjectsManagerBasedTest {
	@Mock
	private JavaClientConnection connection;
	@InjectMocks
	private BuildWorkspaceHandler handler;
	private IFile file;

	@Before
	public void setUp() throws Exception {
		importProjects("maven/salut2");
		file = linkFilesToDefaultProject("singlefile/Single.java");
	}

	@Test
	public void testSucceedCase() throws Exception {
		BuildWorkspaceStatus result = handler.buildWorkspace(false, monitor);
		waitForBackgroundJobs();
		assertTrue(String.format("BuildWorkspaceStatus is: %s.", result.toString()), result == BuildWorkspaceStatus.SUCCEED);
	}

	@Test
	public void testFailedCase() throws Exception {
		String codeWithError = "public class Single2 {\n" +
				"	public static void main(String[] args){\n"+
				"		int ss = 1;;\n"+
				"	}\n"+
				"}";
		try (InputStream stream = new ByteArrayInputStream(codeWithError.getBytes(StandardCharsets.UTF_8))) {
			file.setContents(stream, true, false, monitor);
			waitForBackgroundJobs();
			BuildWorkspaceStatus result = handler.buildWorkspace(false, monitor);
			waitForBackgroundJobs();
			assertEquals(result, BuildWorkspaceStatus.WITH_ERROR);
		}
	}

	@Test
	public void testCanceledCase() throws Exception {
		monitor.setCanceled(true);
		BuildWorkspaceStatus result = handler.buildWorkspace(false, monitor);
		waitForBackgroundJobs();
		assertEquals(result, BuildWorkspaceStatus.CANCELLED);
	}
}
