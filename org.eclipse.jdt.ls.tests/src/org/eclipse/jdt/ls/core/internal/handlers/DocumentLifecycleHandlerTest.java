/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.Lsp4jAssertions.assertRange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;

import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DocumentLifecycleHandlerTest extends AbstractProjectsManagerBasedTest {

	@Mock
	private JavaClientConnection connection;

	private DocumentLifeCycleHandler handler;

	private String uri;

	@Before
	public void setUp() throws Exception {
		LanguageServerWorkingCopyOwner workingCopyOwner = new LanguageServerWorkingCopyOwner(connection);
		WorkingCopyOwner.setPrimaryBufferProvider(workingCopyOwner);
		handler = new DocumentLifeCycleHandler(connection, preferenceManager, projectsManager, workingCopyOwner);
		when(preferenceManager.getPreferences(any())).thenReturn(new Preferences());
		newEmptyProject();

		uri = Paths.get("projects", "maven", "salut", "src", "main", "java", "java", "Foo.java").toUri().toString();
	}

	@After
	public void invalidateAll() {
		if (uri != null) {
			handler.handleClosed(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
		}
	}

	@Test
	public void testDidOpenStandaloneFile() throws Exception {
		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo extends UnknownType {"+
				"	public void method1(){\n"+
				"		super.whatever();"+
				"	}\n"+
				"}";
		// @formatter:on

		PublishDiagnosticsParams diagnostics = open(standaloneFileContent);
		assertEquals("Unexpected number of errors " + diagnostics.getDiagnostics(), 0, diagnostics.getDiagnostics().size());
	}

	@Test
	public void testDidOpenStandaloneFileWithSyntaxError() throws Exception {
		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo extends UnknownType {\n"+
				"	public void method1(){\n"+
				"		super.whatever()\n"+
				"	}\n"+
				"}";
		// @formatter:on

		PublishDiagnosticsParams diagParam = open(standaloneFileContent);
		assertEquals("Unexpected number of errors " + diagParam.getDiagnostics(), 1, diagParam.getDiagnostics().size());
		Diagnostic d = diagParam.getDiagnostics().get(0);
		assertEquals("Syntax error, insert \";\" to complete BlockStatements", d.getMessage());
		assertRange(3, 17, 18, d.getRange());
	}

	@Test
	public void testDidOpenStandaloneFileWithNonSyntaxErrors() throws Exception {
		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo {\n"+
				"	public static void notThis(){\n"+
				"		System.out.println(this);\n"+
				"	}\n"+
				"	public void method1(){\n"+
				"	}\n"+
				"	public void method1(){\n"+
				"	}\n"+
				"}";
		// @formatter:on

		PublishDiagnosticsParams diagParam = open(standaloneFileContent);
		assertEquals("Unexpected number of errors " + diagParam.getDiagnostics(), 3, diagParam.getDiagnostics().size());
		Diagnostic d = diagParam.getDiagnostics().get(0);
		assertEquals("Cannot use this in a static context", d.getMessage());
		assertRange(3, 21, 25, d.getRange());

		d = diagParam.getDiagnostics().get(1);
		assertEquals("Duplicate method method1() in type Foo", d.getMessage());
		assertRange(5, 13, 22, d.getRange());

		d = diagParam.getDiagnostics().get(2);
		assertEquals("Duplicate method method1() in type Foo", d.getMessage());
		assertRange(7, 13, 22, d.getRange());
	}

	private PublishDiagnosticsParams open(String text) {
		DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(new TextDocumentItem(uri, "java", 0, text));
		handler.handleOpen(params);
		ArgumentCaptor<PublishDiagnosticsParams> diagnosticsCaptor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, times(1)).publishDiagnostics(diagnosticsCaptor.capture());
		PublishDiagnosticsParams diagnostics = diagnosticsCaptor.getValue();
		assertNotNull(diagnostics);
		return diagnostics;
	}
}
