/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     btstream - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.junit.Before;
import org.junit.Test;

public class SaveActionHandlerTest extends AbstractCompilationUnitBasedTest {

	private SaveActionHandler handler;

	private PreferenceManager preferenceManager;

	private IProgressMonitor monitor;

	@Override
	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		preferenceManager = mock(PreferenceManager.class);
		Preferences preferences = mock(Preferences.class);
		when(preferences.isJavaSaveActionsOrganizeImportsEnabled()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getPreferences()).thenReturn(preferences);
		monitor = mock(IProgressMonitor.class);
		when(monitor.isCanceled()).thenReturn(false);
		handler = new SaveActionHandler(preferenceManager);
	}

	@Test
	public void testWillSaveWaitUntil() throws Exception {

		URI srcUri = project.getFile("src/java/Foo4.java").getRawLocationURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(srcUri);

		StringBuilder buf = new StringBuilder();
		buf.append("package java;\n");
		buf.append("\n");
		buf.append("public class Foo4 {\n");
		buf.append("}\n");

		WillSaveTextDocumentParams params = new WillSaveTextDocumentParams();
		TextDocumentIdentifier document = new TextDocumentIdentifier();
		document.setUri(srcUri.toString());
		params.setTextDocument(document);

		List<TextEdit> result = handler.willSaveWaitUntil(params, monitor);

		Document doc = new Document();
		doc.set(cu.getSource());
		assertEquals(TextEditUtil.apply(doc, result), buf.toString());
	}

}
