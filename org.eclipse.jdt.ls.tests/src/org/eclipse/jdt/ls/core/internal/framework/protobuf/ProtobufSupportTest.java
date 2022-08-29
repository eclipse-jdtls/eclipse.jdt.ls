/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.framework.protobuf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.AbstractGradleBasedTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProtobufSupportTest extends AbstractGradleBasedTest {

	@Before
	public void setUp() {
		JavaLanguageServerPlugin.getProjectsManager().setConnection(this.client);
		this.preferences.setProtobufSupportEnabled(true);
	}

	@After
	public void tearDown() {
		JavaLanguageServerPlugin.getProjectsManager().setConnection(null);
		this.preferences.setProtobufSupportEnabled(false);
	}

	@Test
	public void testAfterImportsForProtobuf() throws Exception {
		importGradleProject("protobuf");

		ProtobufSupport protobufSupport = new ProtobufSupport();
		protobufSupport.onDidProjectsImported(new NullProgressMonitor());
		waitForBackgroundJobs();

		List<Object> notifications = this.clientRequests.get("sendActionableNotification");
		assertEquals(1, notifications.size());
	}
	
	@Test
	public void testGenerateProtobufSources() throws Exception {
		IProject project = importGradleProject("protobuf");

		ProtobufSupport.generateProtobufSources(Arrays.asList(project.getName()), new NullProgressMonitor());

		waitForBackgroundJobs();

		assertTrue(project.getFile("build/extracted-protos").getLocation().toFile().exists());
	}
}
