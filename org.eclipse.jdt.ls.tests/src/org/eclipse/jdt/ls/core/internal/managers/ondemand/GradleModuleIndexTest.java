/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers.ondemand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GradleModuleIndexTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testFindModulePath() throws Exception {
		File projectDir = copyFiles("gradle/multi-module", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		GradleModuleIndex index = new GradleModuleIndex(workspacePath);

		Path clientFile = workspacePath.resolve("client/src/main/java/Client.java");
		Path modulePath = index.findModulePath(clientFile);

		assertNotNull(modulePath, "Walk-up should discover the client module");
		assertEquals(workspacePath.resolve("client"), modulePath);
	}

	@Test
	public void testFindModulePathServer() throws Exception {
		File projectDir = copyFiles("gradle/multi-module", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		GradleModuleIndex index = new GradleModuleIndex(workspacePath);

		Path serverFile = workspacePath.resolve("server/src/main/java/Server.java");
		Path modulePath = index.findModulePath(serverFile);

		assertNotNull(modulePath, "Walk-up should discover the server module");
		assertEquals(workspacePath.resolve("server"), modulePath);
	}

	@Test
	public void testFindModulePathOutsideWorkspace() throws Exception {
		File projectDir = copyFiles("gradle/multi-module", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		GradleModuleIndex index = new GradleModuleIndex(workspacePath);

		Path outsideFile = Path.of("/nonexistent/outside/workspace/Foo.java");
		Path modulePath = index.findModulePath(outsideFile);

		assertNull(modulePath);
	}

	@Test
	public void testResolveModulesToImport() throws Exception {
		File projectDir = copyFiles("gradle/multi-module", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		GradleModuleIndex index = new GradleModuleIndex(workspacePath);

		Path clientFile = workspacePath.resolve("client/src/main/java/Client.java");
		Path modulePath = index.findModulePath(clientFile);
		assertNotNull(modulePath);

		List<Path> first = index.resolveModulesToImport(modulePath);
		assertEquals(1, first.size());

		List<Path> second = index.resolveModulesToImport(modulePath);
		assertTrue(second.isEmpty());
	}
}
