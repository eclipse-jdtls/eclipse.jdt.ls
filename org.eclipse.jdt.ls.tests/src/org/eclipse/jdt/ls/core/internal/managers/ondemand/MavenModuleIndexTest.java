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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
public class MavenModuleIndexTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testFindModulePath() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		MavenModuleIndex index = new MavenModuleIndex(workspacePath);

		Path javaFile = workspacePath.resolve("src/main/java/org/sample/Bar.java");
		Path modulePath = index.findModulePath(javaFile);

		assertNotNull(modulePath);
		assertEquals(workspacePath, modulePath);
	}

	@Test
	public void testFindModulePathOutsideWorkspace() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		MavenModuleIndex index = new MavenModuleIndex(workspacePath);

		Path outsideFile = Path.of("/nonexistent/outside/workspace/Foo.java");
		Path modulePath = index.findModulePath(outsideFile);

		assertNull(modulePath);
	}

	@Test
	public void testResolveModulesToImport() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		MavenModuleIndex index = new MavenModuleIndex(workspacePath);

		Path javaFile = workspacePath.resolve("src/main/java/org/sample/Bar.java");
		Path modulePath = index.findModulePath(javaFile);
		assertNotNull(modulePath);

		List<Path> first = index.resolveModulesToImport(modulePath);
		assertEquals(1, first.size());
		assertEquals(modulePath, first.get(0));

		List<Path> second = index.resolveModulesToImport(modulePath);
		assertTrue(second.isEmpty());
	}

	@Test
	public void testIsImported() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		MavenModuleIndex index = new MavenModuleIndex(workspacePath);

		Path javaFile = workspacePath.resolve("src/main/java/org/sample/Bar.java");
		Path modulePath = index.findModulePath(javaFile);
		assertNotNull(modulePath);

		assertFalse(index.isImported(modulePath));

		index.resolveModulesToImport(modulePath);

		assertTrue(index.isImported(modulePath));
	}

	@Test
	public void testModuleCount() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		MavenModuleIndex index = new MavenModuleIndex(workspacePath);

		assertEquals(0, index.getModuleCount());

		Path javaFile = workspacePath.resolve("src/main/java/org/sample/Bar.java");
		index.findModulePath(javaFile);

		assertEquals(1, index.getModuleCount());
	}

	@Test
	public void testFindModulePathForUri() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		MavenModuleIndex index = new MavenModuleIndex(workspacePath);

		String fileUri = workspacePath.resolve("src/main/java/org/sample/Bar.java")
				.toUri().toString();
		Path modulePath = index.findModulePath(fileUri);

		assertNotNull(modulePath);
		assertEquals(workspacePath, modulePath);
	}

	@Test
	public void testFindModulePathForUriNull() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		Path workspacePath = projectDir.toPath().toAbsolutePath().normalize();
		MavenModuleIndex index = new MavenModuleIndex(workspacePath);

		Path modulePath = index.findModulePath("file:///nonexistent/path/Foo.java");

		assertNull(modulePath);
	}
}
