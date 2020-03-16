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

package org.eclipse.jdt.ls.core.internal.syntaxserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTEnvironmentUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ContentProviderManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SyntaxServerTest extends AbstractSyntaxProjectsManagerBasedTest {
	private SyntaxLanguageServer server;
	private CoreASTProvider sharedASTProvider;

	private boolean oldBuildStatus = false;

	@Before
	public void setup() throws Exception {
		oldBuildStatus = ResourcesPlugin.getWorkspace().getDescription().isAutoBuilding();
		ProjectsManager.setAutoBuilding(false);
		sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		server = new SyntaxLanguageServer(new ContentProviderManager(preferenceManager), projectsManager, preferenceManager, false);
		server.connectClient(client);

		importProjects("maven/salut4");
	}

	@After
	public void tearDown() throws Exception {
		ProjectsManager.setAutoBuilding(oldBuildStatus);
		server.getClientConnection().disconnect();
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
	}

	@Test
	public void testDidOpen() throws Exception {
		URI fileURI = openFile("maven/salut4", "src/main/java/java/Foo.java");
		Job.getJobManager().join(SyntaxDocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(fileURI);
		assertNotNull(cu);
		IPath rootPath = getWorkingTestPath("maven/salut4");
		String projectName = ProjectUtils.getWorkspaceInvisibleProjectName(rootPath);
		assertEquals(projectName, cu.getJavaProject().getProject().getName());

		IPath[] sourcePaths = ProjectUtils.listSourcePaths(cu.getJavaProject());
		assertEquals(2, sourcePaths.length);
		IPath basePath = ProjectUtils.getProject(projectName).getFolder(ProjectUtils.WORKSPACE_LINK).getFullPath();
		assertTrue(Objects.equals(basePath.append("src/main/java"), sourcePaths[0]));
		assertTrue(Objects.equals(basePath.append("src/test/java"), sourcePaths[1]));
	}

	@Test
	public void testDidClose() throws Exception {
		URI fileURI = openFile("maven/salut4", "src/main/java/java/TestSyntaxError.java");
		Job.getJobManager().join(SyntaxDocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);

		String fileUri = ResourceUtils.fixURI(fileURI);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
		server.didClose(new DidCloseTextDocumentParams(identifier));
		Job.getJobManager().join(SyntaxDocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);

		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(2, diagnosticReports.size());
		PublishDiagnosticsParams params = diagnosticReports.get(1);
		assertEquals(fileUri, params.getUri());
		assertNotNull(params.getDiagnostics());
		assertTrue(params.getDiagnostics().isEmpty());
	}

	@Test
	public void testSyntaxDiagnostics() throws Exception {
		URI fileURI = openFile("maven/salut4", "src/main/java/java/TestSyntaxError.java");
		Job.getJobManager().join(SyntaxDocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);

		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		PublishDiagnosticsParams params = diagnosticReports.get(0);
		assertEquals(ResourceUtils.fixURI(fileURI), params.getUri());
		assertNotNull(params.getDiagnostics());
		assertEquals(1, params.getDiagnostics().size());
		assertEquals("Syntax error, insert \";\" to complete FieldDeclaration", params.getDiagnostics().get(0).getMessage());
	}

	@Test
	public void testDocumentSymbol() throws Exception {
		when(preferenceManager.getClientPreferences().isHierarchicalDocumentSymbolSupported()).thenReturn(Boolean.TRUE);

		URI fileURI = openFile("maven/salut4", "src/main/java/java/Foo.java");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileURI.toString());
		DocumentSymbolParams params = new DocumentSymbolParams(identifier);
		List<Either<SymbolInformation, DocumentSymbol>> result = server.documentSymbol(params).join();
		assertNotNull(result);
		assertEquals(2, result.size());
		Either<SymbolInformation, DocumentSymbol> symbol = result.get(0);
		assertTrue(symbol.isRight());
		assertEquals("java", symbol.getRight().getName());
		assertEquals(SymbolKind.Package, symbol.getRight().getKind());
		symbol = result.get(1);
		assertTrue(symbol.isRight());
		assertEquals("Foo", symbol.getRight().getName());
		assertEquals(SymbolKind.Class, symbol.getRight().getKind());
		List<DocumentSymbol> children = symbol.getRight().getChildren();
		assertNotNull(children);
		assertEquals(1, children.size());
		assertEquals("main(String[])", children.get(0).getName());
		assertEquals(SymbolKind.Method, children.get(0).getKind());
	}

	@Test
	public void testDefinition() throws Exception {
		URI fileURI = openFile("maven/salut4", "src/main/java/java/Foo.java");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileURI.toString());
		DefinitionParams params = new DefinitionParams(identifier, new Position(10, 22));
		Either<List<? extends Location>, List<? extends LocationLink>> result = server.definition(params).join();
		assertTrue(result.isLeft());
		assertNotNull(result.getLeft());
		assertEquals(1, result.getLeft().size());
		String targetUri = result.getLeft().get(0).getUri();
		URI targetURI = JDTUtils.toURI(targetUri);
		assertNotNull(targetURI);
		assertEquals("jdt", targetURI.getScheme());
		assertTrue(targetURI.getPath().endsWith("PrintStream.class"));
		assertEquals(JDTEnvironmentUtils.SYNTAX_SERVER_ID, targetURI.getFragment());
	}

	@Test
	public void testTypeDefinition() throws Exception {
		URI fileURI = openFile("maven/salut4", "src/main/java/java/Foo.java");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileURI.toString());
		TypeDefinitionParams params = new TypeDefinitionParams(identifier, new Position(11, 24));
		Either<List<? extends Location>, List<? extends LocationLink>> result = server.typeDefinition(params).join();
		assertTrue(result.isLeft());
		assertNotNull(result.getLeft());
		assertEquals(1, result.getLeft().size());
		String targetUri = result.getLeft().get(0).getUri();
		assertNotNull(targetUri);
		assertEquals(ResourceUtils.toClientUri(getFileUri("maven/salut4", "src/main/java/java/Bar.java")), targetUri);
	}

	@Test
	public void testHover() throws Exception {
		URI fileURI = openFile("maven/salut4", "src/main/java/java/TestJavadoc.java");
		String fileUri = ResourceUtils.fixURI(fileURI);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
		HoverParams params = new HoverParams(identifier, new Position(8, 23));
		Hover result = server.hover(params).join();
		assertNotNull(result);
		assertNotNull(result.getContents());
		assertTrue(result.getContents().isLeft());
		List<Either<String, MarkedString>> list = result.getContents().getLeft();
		assertNotNull(list);
		assertEquals(2, list.size());
		assertTrue(list.get(1).isLeft());
		assertEquals("Test", list.get(1).getLeft());
	}

	private URI openFile(String basePath, String filePath) throws Exception {
		IPath rootPath = getWorkingTestPath(basePath);
		preferences.setRootPaths(Collections.singletonList(rootPath));
		when(preferenceManager.getPreferences()).thenReturn(preferences);

		URI fileURI = getFileURI(basePath, filePath);
		TextDocumentItem textDocument = getTextDocument(fileURI.toString(), ResourceUtils.getContent(fileURI));
		DidOpenTextDocumentParams openParams = new DidOpenTextDocumentParams(textDocument);
		server.didOpen(openParams);

		return fileURI;
	}

	private IPath getFilePath(String basePath, String filePath) throws IOException {
		IPath rootPath = getWorkingTestPath(basePath);
		return rootPath.append(filePath);
	}

	private URI getFileURI(String basePath, String filePath) throws IOException {
		return getFilePath(basePath, filePath).toFile().toURI();
	}

	private String getFileUri(String basePath, String filePath) throws IOException {
		return ResourceUtils.fixURI(getFileURI(basePath, filePath));
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> getClientRequests(String name) {
		List<?> requests = clientRequests.get(name);
		return requests != null ? (List<T>) requests : Collections.emptyList();
	}

	private TextDocumentItem getTextDocument(String uri, String content) {
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setLanguageId("java");
		textDocument.setText(content);
		textDocument.setUri(uri);
		textDocument.setVersion(1);
		return textDocument;
	}
}
