/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.google.common.io.Files;

public class WorkspaceEventHandlerTest extends AbstractProjectsManagerBasedTest {
	private CoreASTProvider sharedASTProvider;
	private DocumentLifeCycleHandler lifeCycleHandler;
	private JavaClientConnection javaClient;
	private WorkspaceDiagnosticsHandler handler;

	@Mock
	private ClientPreferences clientPreferences;

	@Before
	public void setup() throws Exception {
		sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		javaClient = new JavaClientConnection(client);
		lifeCycleHandler = new DocumentLifeCycleHandler(javaClient, preferenceManager, projectsManager, false);
		handler = new WorkspaceDiagnosticsHandler(javaClient, projectsManager, preferenceManager.getClientPreferences());
		handler.addResourceChangeListener();
	}

	@After
	public void tearDown() throws Exception {
		javaClient.disconnect();
		handler.removeResourceChangeListener();
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
	}

	@Test
	public void testChangeWorkingCopy() throws Exception {
		importProjects("eclipse/hello");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("hello");
		// IJavaProject javaProject = JavaCore.create(project);
		IFile file = project.getFile("src/org/sample/Foo.java");
		ICompilationUnit unit = (ICompilationUnit) JavaCore.create(file);
		String source = unit.getSource();
		openDocument(unit, source, 1);
		File projectFile = project.getRawLocation().toFile();
		File classFile = new File(projectFile, "bin/org/sample/Foo.class");
		long lastModified = classFile.lastModified();
		assertTrue(lastModified > 0);
		source = source.replace("world", "world2");
		File javaFile = new File(projectFile, "src/org/sample/Foo.java");
		FileUtils.writeStringToFile(javaFile, source);
		String uri = JDTUtils.toURI(unit);
		DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(Arrays.asList(new FileEvent(uri, FileChangeType.Changed)));
		new WorkspaceEventsHandler(projectsManager, javaClient, lifeCycleHandler).didChangeWatchedFiles(params);
		waitForBackgroundJobs();
		assertTrue(classFile.lastModified() > lastModified);
	}

	@Test
	public void testDiscardStaleWorkingCopies() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IFolder src = javaProject.getProject().getFolder("src");
		IPackageFragmentRoot srcRoot = javaProject.getPackageFragmentRoot(src);
		IPackageFragment mypack = srcRoot.createPackageFragment("mypack", true, null);
		// @formatter:off
		String contents = "package mypack;\n" +
						"public class Foo {" +
						"}\n";
		// @formatter:on
		ICompilationUnit unit = mypack.createCompilationUnit("Foo.java", contents, true, null);
		openDocument(unit, contents, 1);
		assertTrue(unit.isWorkingCopy());

		String oldUri = JDTUtils.getFileURI(srcRoot.getResource());
		String parentUri = JDTUtils.getFileURI(src);
		String newUri = oldUri.replace("mypack", "mynewpack");
		File oldPack = mypack.getResource().getLocation().toFile();
		File newPack = new File(oldPack.getParent(), "mynewpack");
		Files.move(oldPack, newPack);
		assertTrue(unit.isWorkingCopy());
		DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(Arrays.asList(
			new FileEvent(newUri, FileChangeType.Created),
			new FileEvent(parentUri, FileChangeType.Changed),
			new FileEvent(oldUri, FileChangeType.Deleted)
		));
		new WorkspaceEventsHandler(projectsManager, javaClient, lifeCycleHandler).didChangeWatchedFiles(params);
		assertFalse(unit.isWorkingCopy());
	}

	@Test
	public void testDeleteProjectFolder() throws Exception {
		importProjects("maven/multimodule3");

		IProject module2 = ProjectUtils.getProject("module2");
		assertTrue(module2 != null && module2.exists());

		String projectUri = JDTUtils.getFileURI(module2);
		FileUtils.deleteDirectory(module2.getLocation().toFile());
		assertTrue(module2.exists());

		clientRequests.clear();
		DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(Arrays.asList(
			new FileEvent(projectUri, FileChangeType.Deleted)
		));
		new WorkspaceEventsHandler(projectsManager, javaClient, lifeCycleHandler).didChangeWatchedFiles(params);
		waitForBackgroundJobs();
		assertFalse(module2.exists());

		List<PublishDiagnosticsParams> diags = getClientRequests("publishDiagnostics");
		assertEquals(7, diags.size());
		assertEndsWith(diags.get(0).getUri(), "/module2");
		assertEndsWith(diags.get(1).getUri(), "/multimodule3");
		assertEndsWith(diags.get(2).getUri(), "/multimodule3/pom.xml");
		assertEndsWith(diags.get(3).getUri(), "/module2/pom.xml");
		assertEquals(0L, diags.get(3).getDiagnostics().size());
		assertEndsWith(diags.get(4).getUri(), "/module2");
		assertEquals(0L, diags.get(4).getDiagnostics().size());
		assertEndsWith(diags.get(5).getUri(), "/App.java");
		assertEquals(0L, diags.get(5).getDiagnostics().size());
		assertEndsWith(diags.get(6).getUri(), "/AppTest.java");
		assertEquals(0L, diags.get(6).getDiagnostics().size());
	}

	private void assertEndsWith(String target, String suffix) {
		if (target.endsWith("/")) {
			target = target.substring(0, target.length() - 1);
		}

		assertTrue(target.endsWith(suffix));
	}

	private void openDocument(ICompilationUnit cu, String content, int version) {
		openDocument(JDTUtils.toURI(cu), content, version);
	}

	private void openDocument(String uri, String content, int version) {
		DidOpenTextDocumentParams openParms = new DidOpenTextDocumentParams();
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setLanguageId("java");
		textDocument.setText(content);
		textDocument.setUri(uri);
		textDocument.setVersion(version);
		openParms.setTextDocument(textDocument);
		lifeCycleHandler.didOpen(openParms);
	}

	private <T> List<T> getClientRequests(String name) {
		List<?> requests = clientRequests.get(name);
		return requests != null ? (List<T>) requests : Collections.emptyList();
	}
}
