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

package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.DiagnosticsState.ErrorLevel;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DiagnosticsCommandTest extends AbstractProjectsManagerBasedTest {
	private JavaClientConnection javaClient;
	private DocumentLifeCycleHandler lifeCycleHandler;
	@Mock
	private ClientPreferences clientPreferences;
	private ErrorLevel originalGlobalErrorLevel;

	@Before
	public void setup() throws Exception {
		mockPreferences();
		javaClient = new JavaClientConnection(client);
		lifeCycleHandler = new DocumentLifeCycleHandler(javaClient, preferenceManager, projectsManager, false);
		mockJDTLanguageServer();
		originalGlobalErrorLevel = JavaLanguageServerPlugin.getNonProjectDiagnosticsState().getGlobalErrorLevel();
		JavaLanguageServerPlugin.getNonProjectDiagnosticsState().setGlobalErrorLevel(ErrorLevel.SYNTAX_ERROR);
	}

	@After
	public void tearDown() throws Exception {
		JavaLanguageServerPlugin.getNonProjectDiagnosticsState().setGlobalErrorLevel(originalGlobalErrorLevel);
		javaClient.disconnect();
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
	}

	private Preferences mockPreferences() {
		Preferences mockPreferences = Mockito.mock(Preferences.class);
		Mockito.when(preferenceManager.getPreferences()).thenReturn(mockPreferences);
		Mockito.when(preferenceManager.getPreferences(Mockito.any())).thenReturn(mockPreferences);
		when(this.preferenceManager.getClientPreferences()).thenReturn(clientPreferences);
		when(clientPreferences.isSupportedCodeActionKind(CodeActionKind.QuickFix)).thenReturn(true);
		return mockPreferences;
	}

	private void mockJDTLanguageServer() {
		JDTLanguageServer server = Mockito.mock(JDTLanguageServer.class);
		Mockito.when(server.getClientConnection()).thenReturn(this.javaClient);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
	}

	@Test
	public void testRefreshDiagnosticsWithReportAllErrors() throws Exception {
		IJavaProject javaProject = newDefaultProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("java", false, null);

		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo extends UnknownType {\n"+
				"	public void method1(){\n"+
				"		super.whatever()\n"+
				"	}\n"+
				"}";
		// @formatter:on

		ICompilationUnit cu1 = pack1.createCompilationUnit("Foo.java", standaloneFileContent, false, null);

		openDocument(cu1, cu1.getSource(), 1);

		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		PublishDiagnosticsParams diagParam = diagnosticReports.get(0);
		assertEquals(2, diagParam.getDiagnostics().size());
		assertEquals("Foo.java is a non-project file, only syntax errors are reported", diagParam.getDiagnostics().get(0).getMessage());

		DiagnosticsCommand.refreshDiagnostics(JDTUtils.toURI(cu1), "thisFile", false);

		diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(2, diagnosticReports.size());
		diagParam = diagnosticReports.get(1);
		assertEquals(4, diagParam.getDiagnostics().size());
		assertEquals("Foo.java is a non-project file, only JDK classes are added to its build path", diagParam.getDiagnostics().get(0).getMessage());
		assertEquals("UnknownType cannot be resolved to a type", diagParam.getDiagnostics().get(1).getMessage());
		assertEquals("UnknownType cannot be resolved to a type", diagParam.getDiagnostics().get(2).getMessage());
		assertEquals("Syntax error, insert \";\" to complete BlockStatements", diagParam.getDiagnostics().get(3).getMessage());
	}

	@Test
	public void testRefreshDiagnosticsWithReportSyntaxErrors() throws Exception {
		JavaLanguageServerPlugin.getNonProjectDiagnosticsState().setGlobalErrorLevel(ErrorLevel.COMPILATION_ERROR);
		IJavaProject javaProject = newDefaultProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("java", false, null);

		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo extends UnknownType {\n"+
				"	public void method1(){\n"+
				"		super.whatever()\n"+
				"	}\n"+
				"}";
		// @formatter:on

		ICompilationUnit cu1 = pack1.createCompilationUnit("Foo.java", standaloneFileContent, false, null);

		openDocument(cu1, cu1.getSource(), 1);

		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		PublishDiagnosticsParams diagParam = diagnosticReports.get(0);
		assertEquals(4, diagParam.getDiagnostics().size());
		assertEquals("Foo.java is a non-project file, only JDK classes are added to its build path", diagParam.getDiagnostics().get(0).getMessage());
		assertEquals("UnknownType cannot be resolved to a type", diagParam.getDiagnostics().get(1).getMessage());
		assertEquals("UnknownType cannot be resolved to a type", diagParam.getDiagnostics().get(2).getMessage());
		assertEquals("Syntax error, insert \";\" to complete BlockStatements", diagParam.getDiagnostics().get(3).getMessage());

		DiagnosticsCommand.refreshDiagnostics(JDTUtils.toURI(cu1), "thisFile", true);

		diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(2, diagnosticReports.size());
		diagParam = diagnosticReports.get(1);
		assertEquals(2, diagParam.getDiagnostics().size());
		assertEquals("Foo.java is a non-project file, only syntax errors are reported", diagParam.getDiagnostics().get(0).getMessage());
		assertEquals("Syntax error, insert \";\" to complete BlockStatements", diagParam.getDiagnostics().get(1).getMessage());
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

	@SuppressWarnings("unchecked")
	private <T> List<T> getClientRequests(String name) {
		List<?> requests = clientRequests.get(name);
		return requests != null ? (List<T>) requests : Collections.emptyList();
	}
}
