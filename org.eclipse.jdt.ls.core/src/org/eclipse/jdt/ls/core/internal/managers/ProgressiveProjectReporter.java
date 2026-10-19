/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

/**
 * Periodically reports newly created projects to the client during a
 * long-running import so that the Java Projects tree view can render
 * project names progressively instead of waiting for the entire import
 * to finish.
 * <p>
 * Usage:
 * <pre>
 * ProgressiveProjectReporter reporter = new ProgressiveProjectReporter(client);
 * reporter.start();
 * try {
 *     // ... perform import ...
 * } finally {
 *     reporter.stop();
 * }
 * </pre>
 */
public class ProgressiveProjectReporter {

	private static final long POLL_INTERVAL_SECONDS = 2;

	private final JavaLanguageClient client;
	private final Set<String> notifiedProjects = ConcurrentHashMap.newKeySet();
	private ScheduledExecutorService scheduler;
	private ScheduledFuture<?> progressReporter;

	public ProgressiveProjectReporter(JavaLanguageClient client) {
		this.client = client;
		// Seed with default project name so it's never reported
		notifiedProjects.add(ProjectsManager.DEFAULT_PROJECT_NAME);
	}

	/**
	 * Start the background polling thread. Call this before the import begins.
	 */
	public void start() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "ProjectImportProgressReporter");
			t.setDaemon(true);
			return t;
		});
		progressReporter = scheduler.scheduleAtFixedRate(
				this::reportNewProjects, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * Stop the background polling and send a final notification for any
	 * remaining unreported projects. Call this in a {@code finally} block
	 * after the import completes.
	 */
	public void stop() {
		if (progressReporter != null) {
			progressReporter.cancel(false);
		}
		if (scheduler != null) {
			scheduler.shutdown();
		}
		// Send a final flush for any remaining projects
		reportNewProjects();
	}

	/**
	 * Check for newly created projects in the workspace and send a
	 * {@link EventType#ProjectsImported} notification for any that haven't
	 * been reported yet.
	 */
	private void reportNewProjects() {
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			List<URI> newProjectUris = new ArrayList<>();
			for (IProject p : root.getProjects()) {
				if (p.isAccessible() && notifiedProjects.add(p.getName())) {
					IPath realFolder = ProjectUtils.getProjectRealFolder(p);
					if (realFolder != null) {
						newProjectUris.add(realFolder.toFile().toURI());
					}
				}
			}
			if (!newProjectUris.isEmpty() && client != null) {
				EventNotification notification = new EventNotification()
						.withType(EventType.ProjectsImported)
						.withData(newProjectUris);
				JavaLanguageServerPlugin.logInfo(
						"Progressive import: reporting " + newProjectUris.size() + " new project(s)");
				client.sendEventNotification(notification);
			}
		} catch (Exception e) {
			// Best effort — don't interrupt the import process
			JavaLanguageServerPlugin.logException("Error sending progressive import notification", e);
		}
	}
}
