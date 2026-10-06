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

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.IProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;

/**
 * Centralized manager for on-demand project import.
 *
 * <p>In on-demand mode, projects are not imported at startup. Instead,
 * this manager intercepts {@code textDocument/didOpen} notifications and
 * tries each registered importer until one handles the opened file.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>At startup, {@link #initialize} registers importers that support
 *       on-demand mode. No project scanning or import happens.</li>
 *   <li>On each {@code didOpen}, {@link DocumentLifeCycleHandler} calls
 *       {@link #tryOnDemandImport}. Each importer lazily creates its
 *       module index on first use, walks up from the file to find
 *       the nearest build file, and imports the module if needed.</li>
 *   <li>At shutdown, {@link #closeProjectsOnShutdown} closes all imported
 *       projects so the next session starts clean.</li>
 * </ol>
 *
 * <h3>Stale project cleanup</h3>
 * <p>If a previous session crashed without proper shutdown, projects from
 * that session remain in the workspace. {@link #closeStaleProjects} must be
 * called early in initialization to close them, preventing the Eclipse
 * framework from indexing/building stale projects.</p>
 */
public class OnDemandImportManager {

	/**
	 * Importers that support on-demand mode, initialized once at startup.
	 * Each importer lazily creates its module index on first use.
	 */
	private final List<IProjectImporter> importers = new ArrayList<>();

	private Collection<IPath> rootPaths;

	private volatile boolean active;

	/**
	 * Initializes on-demand mode with the available importers.
	 *
	 * <p>Called at startup in place of {@code initializeProjects()}.
	 * Collects importers that support on-demand mode and initializes them
	 * with the workspace root folder. No scanning or import happens here;
	 * that is deferred to the first {@code didOpen}.</p>
	 *
	 * @param rootPaths the workspace root paths
	 */
	public void initialize(Collection<IPath> rootPaths) {
		// Called only once from InitHandler; no need to clear importers.
		this.rootPaths = rootPaths;
		// ex: rootPaths = [/home/user/quarkus]
		for (IPath rootPath : rootPaths) {
			File rootFolder = rootPath.toFile();
			// ex: importers() returns [MavenProjectImporter, GradleProjectImporter, ...]
			for (IProjectImporter importer : ProjectsManager.importers()) {
				// Only keep importers that support on-demand (Maven, Gradle)
				// ex: MavenProjectImporter.supportsOnDemand() → true
				// ex: InvisibleProjectImporter.supportsOnDemand() → false
				if (importer.supportsOnDemand()) {
					importer.initialize(rootFolder);
					importers.add(importer);
				}
			}
		}
		active = true;
		// ex: "On-demand mode initialized: 2 importer(s) registered for 1 root path(s)"
		JavaLanguageServerPlugin.logInfo("On-demand mode initialized: "
				+ importers.size() + " importer(s) registered for "
				+ rootPaths.size() + " root path(s)");
	}

	/**
	 * Returns whether on-demand mode is active.
	 */
	public boolean isActive() {
		return active;
	}

	// ── On-demand import ────────────────────────────────────────────────

	/**
	 * Attempts to import the module containing the given file URI.
	 *
	 * <p>Called from {@link DocumentLifeCycleHandler#resolveCompilationUnit}
	 * on each {@code textDocument/didOpen}. Tries each registered importer
	 * in order; the first one whose module index recognizes the file
	 * handles the import.</p>
	 *
	 * @param uri the file URI that was opened
	 * @param monitor progress monitor
	 * @return {@code true} if a new module was imported
	 */
	public boolean tryOnDemandImport(String uri, IProgressMonitor monitor) {
		if (!active) {
			return false;
		}
		// ex: uri = "file:///home/user/quarkus/core/src/main/java/Foo.java"
		// Try each importer in order until one handles the file
		for (IProjectImporter importer : importers) {
			try {
				// ex: MavenProjectImporter finds pom.xml → imports the module → returns [project]
				// ex: GradleProjectImporter finds no build.gradle → returns []
				List<IProject> imported = importer.importOnDemand(uri, monitor);
				if (!imported.isEmpty()) {
					// ex: notify client that [quarkus-core] was imported → Java Projects view updates
					notifyProjectsImported(imported);
					return true;
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("On-demand import failed for " + uri, e);
			}
		}
		// No importer handled the file (e.g. file outside any build system)
		return false;
	}

	private void notifyProjectsImported(List<IProject> projects) {
		JavaLanguageClient client = JavaLanguageServerPlugin.getProjectsManager().getConnection();
		if (client == null) {
			return;
		}
		// ex: projects = [quarkus-core] → projectUris = [file:///workspace/quarkus/core/]
		// The client (vscode-java) forwards this to vscode-java-dependency
		// which adds the projects to the Java Projects tree view
		List<URI> projectUris = projects.stream()
				.map(p -> ProjectUtils.getProjectRealFolder(p).toFile().toURI())
				.collect(Collectors.toList());
		EventNotification notification = new EventNotification()
				.withType(EventType.ProjectsImported)
				.withData(projectUris);
		client.sendEventNotification(notification);
	}

	// ── Project cleanup ─────────────────────────────────────────────────

	/**
	 * Closes stale projects left open by a crashed previous session.
	 *
	 * <p>Only closes projects whose location is under one of the workspace
	 * root paths. Projects outside the workspace (e.g. external libraries)
	 * are left untouched.</p>
	 *
	 * @param rootPaths the workspace root paths
	 * @param monitor progress monitor
	 */
	public void closeStaleProjects(Collection<IPath> rootPaths, IProgressMonitor monitor) {
		closeProjects(rootPaths, monitor, "On-demand startup cleanup");
	}

	/**
	 * Closes all on-demand imported projects before workspace save on shutdown.
	 *
	 * <p>This prevents the next session from loading stale projects at startup.</p>
	 *
	 * @param monitor progress monitor
	 */
	public void closeProjectsOnShutdown(IProgressMonitor monitor) {
		if (!active || rootPaths == null) {
			return;
		}
		closeProjects(rootPaths, monitor, "On-demand shutdown cleanup");
	}

	private void closeProjects(Collection<IPath> rootPaths, IProgressMonitor monitor, String context) {
		for (IPath root : rootPaths) {
			Path workspacePath = root.toFile().toPath().normalize();
			closeProjectsUnder(workspacePath, monitor, context);
		}
	}

	/**
	 * Closes open projects, optionally filtering by workspace path.
	 */
	private void closeProjectsUnder(Path workspacePath, IProgressMonitor monitor, String context) {
		IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		int closed = 0;
		for (IProject project : allProjects) {
			// Skip already-closed projects
			if (!project.isOpen()) {
				continue;
			}
			// If workspacePath is set, only close projects under that path
			// ex: workspacePath = /home/user/quarkus, project at /home/user/quarkus/core → close
			// ex: workspacePath = /home/user/quarkus, project at /home/user/other → skip
			// If workspacePath is null (shutdown), close all projects
			if (workspacePath != null && project.getLocation() != null) {
				Path projectPath = project.getLocation().toFile().toPath().normalize();
				if (!projectPath.startsWith(workspacePath)) {
					continue;
				}
			}
			try {
				// Close, not delete: preserves JDT index cache for fast reopen next session
				project.close(monitor);
				closed++;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to close project " + project.getName(), e);
			}
		}
		if (closed > 0) {
			JavaLanguageServerPlugin.logInfo(context + ": closed " + closed + " project(s)");
		}
	}
}
