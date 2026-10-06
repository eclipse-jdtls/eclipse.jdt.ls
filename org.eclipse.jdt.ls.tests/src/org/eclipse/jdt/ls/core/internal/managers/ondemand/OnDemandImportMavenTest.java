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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OnDemandImportMavenTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testTryOnDemandImportMaven() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		IPath rootPath = org.eclipse.core.runtime.Path.fromOSString(projectDir.getAbsolutePath());

		OnDemandImportManager manager = new OnDemandImportManager();
		manager.initialize(Collections.singleton(rootPath));

		String fileUri = projectDir.toPath()
				.resolve("src/main/java/org/sample/Bar.java")
				.toUri().toString();

		boolean imported = manager.tryOnDemandImport(fileUri, new NullProgressMonitor());
		assertTrue(imported);
		waitForBackgroundJobs();

		List<IProject> projects = WorkspaceHelper.getAllProjects();
		assertFalse(projects.isEmpty(), "At least one project should have been imported");
	}

	@Test
	public void testTryOnDemandImportAlreadyImported() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		IPath rootPath = org.eclipse.core.runtime.Path.fromOSString(projectDir.getAbsolutePath());

		OnDemandImportManager manager = new OnDemandImportManager();
		manager.initialize(Collections.singleton(rootPath));

		String fileUri = projectDir.toPath()
				.resolve("src/main/java/org/sample/Bar.java")
				.toUri().toString();

		boolean first = manager.tryOnDemandImport(fileUri, new NullProgressMonitor());
		assertTrue(first);
		waitForBackgroundJobs();

		boolean second = manager.tryOnDemandImport(fileUri, new NullProgressMonitor());
		assertFalse(second);
	}

	@Test
	public void testTryOnDemandImportUnknownFile() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		IPath rootPath = org.eclipse.core.runtime.Path.fromOSString(projectDir.getAbsolutePath());

		OnDemandImportManager manager = new OnDemandImportManager();
		manager.initialize(Collections.singleton(rootPath));

		String fileUri = new File("/nonexistent/path/Foo.java").toURI().toString();

		boolean imported = manager.tryOnDemandImport(fileUri, new NullProgressMonitor());
		assertFalse(imported);
	}

	@Test
	public void testTryOnDemandImportNotActive() throws Exception {
		OnDemandImportManager manager = new OnDemandImportManager();

		String fileUri = new File("/some/path/Foo.java").toURI().toString();
		boolean imported = manager.tryOnDemandImport(fileUri, new NullProgressMonitor());
		assertFalse(imported);
	}
}
