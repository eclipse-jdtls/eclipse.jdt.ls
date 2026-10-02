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
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OnDemandImportManagerTest extends AbstractProjectsManagerBasedTest {

	// ── Lifecycle ──────────────────────────────────────────────────────────

	@Test
	public void testNotActiveByDefault() {
		OnDemandImportManager manager = new OnDemandImportManager();
		assertFalse(manager.isActive());
	}

	@Test
	public void testInitialize() throws Exception {
		File projectDir = copyFiles("maven/salut", true);
		IPath rootPath = org.eclipse.core.runtime.Path.fromOSString(projectDir.getAbsolutePath());

		OnDemandImportManager manager = new OnDemandImportManager();
		manager.initialize(Collections.singleton(rootPath));

		assertTrue(manager.isActive());
	}

	// ── closeStaleProjects ─────────────────────────────────────────────────

	@Test
	public void testCloseStaleProjects() throws Exception {
		List<IProject> projects = importProjects("maven/salut");
		assertFalse(projects.isEmpty());
		IProject project = projects.stream()
				.filter(p -> p.getName().equals("salut"))
				.findFirst()
				.orElse(projects.get(0));
		assertTrue(project.isOpen());

		IPath rootPath = org.eclipse.core.runtime.Path.fromOSString(
				project.getLocation().toFile().getParentFile().getAbsolutePath());

		OnDemandImportManager manager = new OnDemandImportManager();
		manager.closeStaleProjects(Collections.singleton(rootPath), new NullProgressMonitor());

		assertFalse(project.isOpen(), "Project should have been closed by closeStaleProjects");
	}

	// ── closeProjectsOnShutdown ────────────────────────────────────────────

	@Test
	public void testCloseProjectsOnShutdown() throws Exception {
		List<IProject> projects = importProjects("maven/salut");
		assertFalse(projects.isEmpty());
		IProject project = projects.stream()
				.filter(p -> p.getName().equals("salut"))
				.findFirst()
				.orElse(projects.get(0));
		assertTrue(project.isOpen());

		OnDemandImportManager manager = new OnDemandImportManager();
		manager.initialize(Collections.singleton(
				org.eclipse.core.runtime.Path.fromOSString(
						project.getLocation().toFile().getAbsolutePath())));
		manager.closeProjectsOnShutdown(new NullProgressMonitor());

		assertFalse(project.isOpen(), "Project should have been closed on shutdown");
	}

	@Test
	public void testCloseProjectsOnShutdownNotActiveIsNoop() throws Exception {
		List<IProject> projects = importProjects("maven/salut");
		assertFalse(projects.isEmpty());
		IProject project = projects.stream()
				.filter(p -> p.getName().equals("salut"))
				.findFirst()
				.orElse(projects.get(0));
		assertTrue(project.isOpen());

		OnDemandImportManager manager = new OnDemandImportManager();
		manager.closeProjectsOnShutdown(new NullProgressMonitor());

		assertTrue(project.isOpen(), "Project should remain open when manager is not active");
	}

	// ── Closed project handling ────────────────────────────────────────────

	@Test
	public void testClosedProjectNotAccessible() throws Exception {
		List<IProject> projects = importProjects("maven/salut");
		assertFalse(projects.isEmpty());
		IProject project = projects.stream()
				.filter(p -> p.getName().equals("salut"))
				.findFirst()
				.orElse(projects.get(0));
		assertTrue(project.isAccessible());

		project.close(new NullProgressMonitor());

		assertFalse(project.isAccessible());
		IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		boolean found = false;
		for (IProject p : allProjects) {
			if (p.getName().equals(project.getName())) {
				found = true;
				break;
			}
		}
		assertTrue(found, "Closed project should still exist in the workspace");
	}
}
