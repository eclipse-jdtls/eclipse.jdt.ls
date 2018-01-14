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

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.junit.Before;
import org.junit.Test;

public class SaveActionHandlerTest extends AbstractProjectsManagerBasedTest {

	private SaveActionHandler handler;

	private PreferenceManager preferenceManager;

	private IPackageFragmentRoot sourceFolder;

	private IProgressMonitor monitor;

	@Before
	public void setup() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		preferenceManager = mock(PreferenceManager.class);
		ClientPreferences clientPreferences = mock(ClientPreferences.class);
		when(preferenceManager.getClientPreferences()).thenReturn(clientPreferences);
		when(clientPreferences.isWillSaveRegistered()).thenReturn(true);
		when(clientPreferences.isWillSaveWaitUntilRegistered()).thenReturn(true);
		monitor = mock(IProgressMonitor.class);
		when(monitor.isCanceled()).thenReturn(false);
		handler = new SaveActionHandler(preferenceManager);
	}

	@Test
	public void testWillSaveWaitUntil() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		WillSaveTextDocumentParams params = new WillSaveTextDocumentParams();
		TextDocumentIdentifier document = new TextDocumentIdentifier();
		document.setUri(cu.getPath().toOSString());
		params.setTextDocument(document);

		List<TextEdit> result = handler.willSaveWaitUntil(params, monitor);

		Document doc = new Document();
		assertEquals(TextEditUtil.apply(doc, result), buf.toString());
	}

}
