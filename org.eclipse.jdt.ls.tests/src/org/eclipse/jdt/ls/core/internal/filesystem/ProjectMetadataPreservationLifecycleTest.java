/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.filesystem;

import static org.eclipse.jdt.ls.core.internal.testing.ProjectMetadataAssertions.assertProjectMetadataEquals;
import static org.eclipse.jdt.ls.core.internal.testing.ProjectMetadataAssertions.snapshotProjectMetadata;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.InitHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.handlers.MapFlattener;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.StandardProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.junit.jupiter.api.Test;

public class ProjectMetadataPreservationLifecycleTest extends AbstractProjectsManagerBasedTest {

	private static final String PROJECT_PATH = "maven/metadata-preservation";
	private static final String PROJECT_NAME = "metadata-preservation";

	private JDTLanguageServer server;

	@Test
	void preservesProjectMetadataAcrossImportUpdateAndRestart() throws Exception {
		String previousMetadataAtProjectRoot = System.getProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT);
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, "true");
		PreferenceManager originalPreferenceManager = preferenceManager;
		StandardProjectsManager originalProjectsManager = projectsManager;
		IEclipsePreferences instancePreferences = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		String previousPreservationSetting = instancePreferences.get(Preferences.PRESERVE_PROJECT_METADATA, null);
		String previousWorkspaceInitialized = instancePreferences.get(IConstants.WORKSPACE_INITIALIZED, null);
		File projectDirectory = copyFiles(PROJECT_PATH, true);
		Path projectRoot = projectDirectory.toPath();
		Map<Path, byte[]> metadataBeforeImport = snapshotProjectMetadata(projectRoot);
		Path shadowRoot = null;

		try {
			startServer(new PreferenceManager());
			initialize(projectDirectory);
			shadowRoot = Path.of(new JLSFileSystem().getStore(org.eclipse.core.runtime.Path.fromOSString(projectRoot.resolve(".project").toString())).toURI()).getParent();
			assertFalse(projectRoot.toRealPath().equals(shadowRoot.toRealPath()));

			IProject project = WorkspaceHelper.getProject(PROJECT_NAME);
			assertNotNull(project);
			IJavaProject javaProject = JavaCore.create(project);
			assertNotNull(javaProject.findType("org.apache.commons.lang3.StringUtils"));
			assertProjectMetadataEquals(metadataBeforeImport, projectRoot);

			IFile pom = project.getFile("pom.xml");
			String updatedPom = ResourceUtils.getContent(pom)
					.replace("<groupId>org.apache.commons</groupId>", "<groupId>commons-codec</groupId>")
					.replace("<artifactId>commons-lang3</artifactId>", "<artifactId>commons-codec</artifactId>")
					.replace("<version>3.18.0</version>", "<version>1.20.0</version>");
			ResourceUtils.setContent(pom, updatedPom);
			projectsManager.updateProject(project, false);
			waitForBackgroundJobs();

			assertNull(javaProject.findType("org.apache.commons.lang3.StringUtils"));
			assertNotNull(javaProject.findType("org.apache.commons.codec.binary.Base64"));
			assertProjectMetadataEquals(metadataBeforeImport, projectRoot);

			stopServer();
			project.delete(IProject.FORCE | IProject.NEVER_DELETE_PROJECT_CONTENT, new NullProgressMonitor());
			waitForBackgroundJobs();

			startServer(new PreferenceManager());
			initialize(projectDirectory);
			project = WorkspaceHelper.getProject(PROJECT_NAME);
			assertNotNull(project);
			assertNotNull(JavaCore.create(project).findType("org.apache.commons.codec.binary.Base64"));
			assertProjectMetadataEquals(metadataBeforeImport, projectRoot);
		} finally {
			try {
				stopServer();
			} finally {
				try {
					if (shadowRoot != null) {
						FileUtils.deleteDirectory(shadowRoot.toFile());
					}
				} finally {
					restore(instancePreferences, Preferences.PRESERVE_PROJECT_METADATA, previousPreservationSetting);
					restore(instancePreferences, IConstants.WORKSPACE_INITIALIZED, previousWorkspaceInitialized);
					restoreSystemProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, previousMetadataAtProjectRoot);
					preferenceManager = originalPreferenceManager;
					JavaLanguageServerPlugin.setPreferencesManager(preferenceManager);
					projectsManager = originalProjectsManager;
				}
			}
		}
	}

	private void startServer(PreferenceManager manager) {
		preferenceManager = manager;
		JavaLanguageServerPlugin.setPreferencesManager(manager);
		projectsManager = new StandardProjectsManager(manager);
		server = new JDTLanguageServer(projectsManager, manager);
		server.connectClient(client);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
	}

	private void initialize(File projectDirectory) throws Exception {
		InitializeParams params = new InitializeParams();
		params.setCapabilities(new ClientCapabilities());
		params.setRootUri(projectDirectory.toURI().toString());
		params.setInitializationOptions(Map.of(InitHandler.SETTINGS_KEY, preservationConfiguration()));
		server.initialize(params).get(60, TimeUnit.SECONDS);
		server.initialized(new InitializedParams());
		JobHelpers.waitForJobs(JDTLanguageServer.JAVA_LSP_INITIALIZE_WORKSPACE, monitor);
		waitForBackgroundJobs();
		assertTrue(preferenceManager.getPreferences().isPreserveProjectMetadata());
	}

	private static Map<String, Object> preservationConfiguration() {
		Map<String, Object> configuration = new HashMap<>();
		MapFlattener.setValue(configuration, Preferences.PRESERVE_PROJECT_METADATA, true);
		return configuration;
	}

	private void stopServer() throws Exception {
		if (server == null) {
			return;
		}
		server.shutdown().get(60, TimeUnit.SECONDS);
		server.disconnectClient();
		projectsManager.unregisterListeners();
		waitForBackgroundJobs();
		server = null;
		JavaLanguageServerPlugin.getInstance().setProtocol(null);
	}

	private static void restore(IEclipsePreferences preferences, String key, String value) {
		if (value == null) {
			preferences.remove(key);
		} else {
			preferences.put(key, value);
		}
	}

	private static void restoreSystemProperty(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}
}
