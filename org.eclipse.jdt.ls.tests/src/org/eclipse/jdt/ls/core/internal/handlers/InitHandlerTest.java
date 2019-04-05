/*******************************************************************************
 * Copyright (c) 2017-2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeConfigurationCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandCapabilities;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceEditCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author snjeza
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class InitHandlerTest extends AbstractProjectsManagerBasedTest {

	private static final String TEST_CONTENT = "test=test\n";
	private static final String TEST_EXCLUSIONS = "**/test/**";
	protected JDTLanguageServer server;

	@Mock
	private JavaLanguageClient client;

	@Before
	public void setup() throws Exception {
		server = new JDTLanguageServer(projectsManager, preferenceManager);
		server.connectClient(client);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
	}

	@After
	public void tearDown() {
		server.disconnectClient();
		JavaLanguageServerPlugin.getInstance().setProtocol(null);
		try {
			projectsManager.setAutoBuilding(true);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	@Test
	public void testExecuteCommandProvider() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isExecuteCommandDynamicRegistrationSupported()).thenReturn(Boolean.FALSE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		InitializeResult result = initialize(false);
		List<String> commands = result.getCapabilities().getExecuteCommandProvider().getCommands();
		assertFalse(commands.isEmpty());
	}

	@Test
	public void testExecuteCommandProviderDynamicRegistration() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isExecuteCommandDynamicRegistrationSupported()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		InitializeResult result = initialize(true);
		assertNull(result.getCapabilities().getExecuteCommandProvider());
	}

	@Test
	public void testWillSaveAndWillSaveWaitUntilCapabilities() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isExecuteCommandDynamicRegistrationSupported()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		when(mockCapabilies.isWillSaveRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isWillSaveWaitUntilRegistered()).thenReturn(Boolean.TRUE);
		InitializeResult result = initialize(true);
		Either<TextDocumentSyncKind, TextDocumentSyncOptions> o = result.getCapabilities().getTextDocumentSync();
		assertTrue(o.isRight());
		assertTrue(o.getRight().getWillSave());
		assertTrue(o.getRight().getWillSaveWaitUntil());
	}

	@Test
	public void testRegisterDelayedCapability() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		when(mockCapabilies.isDocumentSymbolDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isWorkspaceSymbolDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isDocumentSymbolDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isCodeActionDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isDefinitionDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isHoverDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isReferencesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isDocumentHighlightDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isFoldgingRangeDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isCompletionDynamicRegistered()).thenReturn(Boolean.TRUE);
		InitializeResult result = initialize(true);
		assertNull(result.getCapabilities().getDocumentSymbolProvider());
		server.initialized(new InitializedParams());
		verify(client, times(9)).registerCapability(any());
	}

	@Test
	public void testMavenSettings() throws Exception {
		Map<String, Object> initializationOptions = new HashMap<>();
		String test = File.separator + "test";
		initializationOptions.put(Preferences.MAVEN_USER_SETTINGS_KEY, "~" + test);
		Preferences prefs = Preferences.createFrom((initializationOptions));
		assertEquals(System.getProperty("user.home") + test, prefs.getMavenUserSettings());
		initializationOptions.put(Preferences.MAVEN_USER_SETTINGS_KEY, null);
		prefs = Preferences.createFrom((initializationOptions));
		assertNull(prefs.getMavenUserSettings());
		String tildeTest = "~test";
		initializationOptions.put(Preferences.MAVEN_USER_SETTINGS_KEY, tildeTest);
		prefs = Preferences.createFrom((initializationOptions));
		assertEquals(tildeTest, prefs.getMavenUserSettings());
	}

	@Test
	public void testJavaImportExclusions() throws Exception {
		Map<String, Object> initializationOptions = createInitializationOptions();
		@SuppressWarnings("unchecked")
		Preferences prefs = Preferences.createFrom((Map<String, Object>) (initializationOptions.get(InitHandler.SETTINGS_KEY)));
		assertEquals(TEST_EXCLUSIONS, prefs.getJavaImportExclusions().get(0));
	}

	@Test
	public void testWatchers() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isWorkspaceChangeWatchedFilesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		importProjects(Arrays.asList("maven/salut", "gradle/simple-gradle"));
		newEmptyProject();
		List<FileSystemWatcher> watchers = projectsManager.registerWatchers();
		Collections.sort(watchers, new Comparator<FileSystemWatcher>() {

			@Override
			public int compare(FileSystemWatcher o1, FileSystemWatcher o2) {
				return o1.getGlobPattern().compareTo(o2.getGlobPattern());
			}
		});
		assertEquals("Unexpected watchers:\n" + toString(watchers), 8, watchers.size());
		assertEquals(watchers.get(0).getGlobPattern(), "**/*.gradle");
		assertEquals(watchers.get(1).getGlobPattern(), "**/*.java");
		assertEquals(watchers.get(2).getGlobPattern(), "**/.classpath");
		assertEquals(watchers.get(3).getGlobPattern(), "**/.project");
		assertEquals(watchers.get(4).getGlobPattern(), "**/gradle.properties");
		assertEquals(watchers.get(5).getGlobPattern(), "**/pom.xml");
		assertEquals(watchers.get(6).getGlobPattern(), "**/settings/*.prefs");
		assertEquals(watchers.get(7).getGlobPattern(), "**/src/**");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut");
		String location = project.getLocation().toString();
		IJavaProject javaProject = JavaCore.create(project);
		// for test purposes only
		removeExclusionPattern(javaProject);
		File outputDir = new File(new File(location), javaProject.getOutputLocation().removeFirstSegments(1).toOSString());
		File outputFile = new File(outputDir, "test.properties");
		String resourceName = location + "/src/main/resources/test.properties";
		String uri = "file://" + resourceName;
		File sourceFile = new Path(resourceName).toFile();
		assertTrue(FileUtils.contentEquals(sourceFile, outputFile));
		FileUtils.writeStringToFile(sourceFile, TEST_CONTENT);
		FileEvent fileEvent = new FileEvent(uri, FileChangeType.Changed);
		DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams();
		params.getChanges().add(fileEvent);
		server.didChangeWatchedFiles(params);
		JobHelpers.waitForJobsToComplete();
		assertTrue(FileUtils.contentEquals(sourceFile, outputFile));
		verify(client, times(1)).registerCapability(any());
		List<FileSystemWatcher> newWatchers = projectsManager.registerWatchers();
		verify(client, times(1)).registerCapability(any());
		Collections.sort(newWatchers, new Comparator<FileSystemWatcher>() {

			@Override
			public int compare(FileSystemWatcher o1, FileSystemWatcher o2) {
				return o1.getGlobPattern().compareTo(o2.getGlobPattern());
			}
		});
		assertEquals(newWatchers, watchers);
	}

	private String toString(List<FileSystemWatcher> watchers) {
		return watchers.stream().map(FileSystemWatcher::getGlobPattern).collect(Collectors.joining("\n"));
	}

	@Test
	public void testInitOnSymbolicLinkFolder() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isWorkspaceChangeWatchedFilesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		File tempDirectory = new File(System.getProperty("java.io.tmpdir"), "/projects_symbolic_link-" + new Random().nextInt(10000));
		tempDirectory.mkdirs();
		File targetLinkFolder = new File(tempDirectory, "simple-gradle");
		File targetFile = copyFiles("gradle/simple-gradle", true);
		try {

			Files.createSymbolicLink(Paths.get(targetLinkFolder.getPath()), Paths.get(targetFile.getAbsolutePath()));

			projectsManager.initializeProjects(Arrays.asList(Path.fromOSString(targetLinkFolder.getAbsolutePath())), null);
			newEmptyProject();
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("simple-gradle");
			String location = project.getLocation().toString();
			if (Platform.OS_WIN32.equals(Platform.getOS())) {
				assertEquals(Path.fromOSString(targetLinkFolder.getAbsolutePath()).toString(), location);
			} else {
				assertEquals(Path.fromOSString(targetFile.getAbsolutePath()).toString(), location);
			}
		} finally {
			targetLinkFolder.delete();
			FileUtils.deleteDirectory(targetFile);
			FileUtils.deleteDirectory(tempDirectory);
		}
	}

	@Test
	public void testMissingResourceOperations() throws Exception {
		ClientCapabilities capabilities = new ClientCapabilities();
		WorkspaceClientCapabilities worspaceCapabilities = new WorkspaceClientCapabilities();
		worspaceCapabilities.setWorkspaceEdit(new WorkspaceEditCapabilities());
		capabilities.setWorkspace(worspaceCapabilities);
		ClientPreferences preferences = new ClientPreferences(capabilities);
		assertFalse(preferences.isResourceOperationSupported());
	}

	private void removeExclusionPattern(IJavaProject javaProject) throws JavaModelException {
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		for (int i = 0; i < classpath.length; i++) {
			IClasspathEntry entry = classpath[i];
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				IPath path = entry.getPath();
				if (path.toString().endsWith("resources")) {
					IClasspathEntry newEntry = JavaCore.newSourceEntry(entry.getPath());
					classpath[i] = newEntry;
					javaProject.setRawClasspath(classpath, monitor);
					return;
				}
			}
		}
	}

	private Map<String, Object> createInitializationOptions() {
		List<String> javaImportExclusions = new ArrayList<>();
		javaImportExclusions.add(TEST_EXCLUSIONS);
		HashMap<String, Object> exclusionsMap = getMap("exclusions", javaImportExclusions);
		HashMap<String, Object> importMap = getMap("import", exclusionsMap);
		Map<String, Object> javaMap = getMap("java", importMap);
		Map<String, Object> initializationOptions = new HashMap<>();
		initializationOptions.put(InitHandler.SETTINGS_KEY, javaMap);
		return initializationOptions;
	}

	private HashMap<String, Object> getMap(String key, Object obj) {
		HashMap<String, Object> map = new HashMap<>();
		map.put(key, obj);
		return map;
	}

	private InitializeResult initialize(boolean dynamicRegistration) throws InterruptedException, ExecutionException {
		InitializeParams params = new InitializeParams();
		ClientCapabilities capabilities = new ClientCapabilities();
		WorkspaceClientCapabilities workspaceCapabilities = new WorkspaceClientCapabilities();
		workspaceCapabilities.setDidChangeConfiguration(new DidChangeConfigurationCapabilities(dynamicRegistration));
		ExecuteCommandCapabilities executeCommand = new ExecuteCommandCapabilities(dynamicRegistration);
		workspaceCapabilities.setExecuteCommand(executeCommand);
		capabilities.setWorkspace(workspaceCapabilities);
		TextDocumentClientCapabilities textDocument = new TextDocumentClientCapabilities();
		SynchronizationCapabilities synchronizationCapabilities = new SynchronizationCapabilities();
		synchronizationCapabilities.setWillSave(Boolean.TRUE);
		synchronizationCapabilities.setWillSaveWaitUntil(Boolean.TRUE);
		capabilities.setTextDocument(textDocument);
		params.setCapabilities(capabilities);
		CompletableFuture<InitializeResult> result = server.initialize(params);
		return result.get();
	}
}

