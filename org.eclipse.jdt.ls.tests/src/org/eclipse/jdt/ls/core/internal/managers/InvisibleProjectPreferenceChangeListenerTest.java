/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * InvisibleProjectPreferenceChangeListenerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class InvisibleProjectPreferenceChangeListenerTest extends AbstractInvisibleProjectBasedTest {

	private JavaClientConnection javaClient;
	private DocumentLifeCycleHandler lifeCycleHandler;

	@Before
	public void setup() throws Exception {
		javaClient = new JavaClientConnection(client);
		lifeCycleHandler = new DocumentLifeCycleHandler(javaClient, preferenceManager, projectsManager, false);
		mockJDTLanguageServer();
	}

	@After
	public void tearDown() throws Exception {
		// JavaLanguageServerPlugin.getNonProjectDiagnosticsState().setGlobalErrorLevel(originalGlobalErrorLevel);
		javaClient.disconnect();
		JavaLanguageServerPlugin.getInstance().setProtocol(null);
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
	}

	private void mockJDTLanguageServer() {
		JDTLanguageServer server = Mockito.mock(JDTLanguageServer.class);
		Mockito.when(server.getClientConnection()).thenReturn(this.javaClient);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
	}

	@Test
	public void testUpdateOutputPath() throws Exception {
		preferenceManager.getPreferences().setInvisibleProjectOutputPath("");
		IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(String.join("/", "", javaProject.getElementName(), "bin"), javaProject.getOutputLocation().toString());

		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectOutputPath("bin");
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);
		waitForBackgroundJobs();

		assertEquals(String.join("/", "", javaProject.getElementName(), ProjectUtils.WORKSPACE_LINK, "bin"), javaProject.getOutputLocation().toString());
	}

	@Test
	public void testUpdateOutputPathWontAffectSourcePath() throws Exception {
		preferenceManager.getPreferences().setInvisibleProjectOutputPath("");
		IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
		IJavaProject javaProject = JavaCore.create(project);

		int originalSourcePathCount = 0;
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				originalSourcePathCount++;
			}
		}

		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectOutputPath("bin");
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);
		waitForBackgroundJobs();

		int newSourcePathCount = 0;
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				newSourcePathCount++;
			}
		}
		assertEquals(originalSourcePathCount, newSourcePathCount);
	}

	@Test
	public void testUpdateOutputPathToUnEmptyFolder() throws Exception {
		copyAndImportFolder("singlefile/simple", "src/App.java");
		waitForBackgroundJobs();

		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectOutputPath("lib");

		JavaLanguageClient client = mock(JavaLanguageClient.class);
		ProjectsManager pm = JavaLanguageServerPlugin.getProjectsManager();
		pm.setConnection(client);
		doNothing().when(client).showMessage(any(MessageParams.class));
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);
		waitForBackgroundJobs();

		verify(client, times(1)).showMessage(any(MessageParams.class));
	}

	@Test
	public void testUpdateSourcePaths() throws Exception {
		preferences.setInvisibleProjectSourcePaths(Arrays.asList("src"));
		IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
		IJavaProject javaProject = JavaCore.create(project);
		IFolder linkFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(1, sourcePaths.size());
		assertTrue(sourcePaths.contains("src"));

		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectSourcePaths(Arrays.asList("src", "test"));
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);

		waitForBackgroundJobs();

		sourcePaths.clear();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}

		assertEquals(2, sourcePaths.size());
		assertTrue(sourcePaths.contains("src"));
		assertTrue(sourcePaths.contains("test"));
	}

	@Test
	public void testWhenRootPathChanged() throws Exception {
		JavaLanguageClient client = mock(JavaLanguageClient.class);
		ProjectsManager pm = JavaLanguageServerPlugin.getProjectsManager();
		pm.setConnection(client);
		doNothing().when(client).showMessage(any(MessageParams.class));
		copyAndImportFolder("singlefile/simple", "src/App.java");

		List<IPath> rootPaths = new ArrayList<>(preferenceManager.getPreferences().getRootPaths());
		rootPaths.remove(0);
		preferenceManager.getPreferences().setRootPaths(rootPaths);

		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectSourcePaths(Arrays.asList("src", "src2"));
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);

		verify(client, times(0)).showMessage(any(MessageParams.class));
	}

	@Test
	public void testUpdateSourcePaths2() throws Exception {
		preferenceManager.getPreferences().setInvisibleProjectSourcePaths(Arrays.asList("src1"));
		IProject project = copyAndImportFolder("singlefile/wrong-packagename", "src/mypackage/Foo.java");
		IJavaProject javaProject = JavaCore.create(project);
		IFolder linkFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK);
		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(0, sourcePaths.size());
		IFile file = project.getFile("_/src/mypackage/Foo.java");
		String contents = IOUtils.toString(file.getContents(), "UTF-8");
		ICompilationUnit cu = (ICompilationUnit) JavaCore.create(file);
		openDocument(cu, contents, 1);
		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		getClientRequests("publishDiagnostics").clear();
		Preferences newPreferences = new Preferences();
		initPreferences(newPreferences);
		newPreferences.setInvisibleProjectSourcePaths(Arrays.asList("src"));
		InvisibleProjectPreferenceChangeListener listener = new InvisibleProjectPreferenceChangeListener();
		listener.preferencesChange(preferenceManager.getPreferences(), newPreferences);
		waitForBackgroundJobs();
		sourcePaths.clear();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(1, sourcePaths.size());
		assertTrue(sourcePaths.contains("src"));
		diagnosticReports = getClientRequests("publishDiagnostics");
		assertTrue(diagnosticReports.size() > 0);
		for (PublishDiagnosticsParams diagnosticReport : diagnosticReports) {
			assertTrue(diagnosticReport.getDiagnostics().isEmpty());
		}
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> getClientRequests(String name) {
		List<?> requests = clientRequests.get(name);
		return requests != null ? (List<T>) requests : Collections.emptyList();
	}

	private void openDocument(ICompilationUnit cu, String content, int version) {
		DidOpenTextDocumentParams openParms = new DidOpenTextDocumentParams();
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setLanguageId("java");
		textDocument.setText(content);
		textDocument.setUri(JDTUtils.toURI(cu));
		textDocument.setVersion(version);
		openParms.setTextDocument(textDocument);
		lifeCycleHandler.didOpen(openParms);
	}
}
