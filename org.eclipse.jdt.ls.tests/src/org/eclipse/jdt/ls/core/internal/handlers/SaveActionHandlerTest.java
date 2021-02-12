/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     btstream - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
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
		assertEquals(buf.toString(), ResourceUtils.dos2Unix(TextEditUtil.apply(doc, result)));
	}

	@Test
	public void testStaticWillSaveWaitUntil() throws Exception {
		String[] favourites = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaCompletionFavoriteMembers();
		try {
			List<String> list = new ArrayList<>();
			list.add("java.lang.Math.*");
			list.add("java.util.stream.Collectors.*");
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(list);
			URI srcUri = project.getFile("src/org/sample/Foo6.java").getRawLocationURI();
			ICompilationUnit cu = JDTUtils.resolveCompilationUnit(srcUri);
			/* @formatter:off */
			String expected = "package org.sample;\n" +
					"\n" +
					"import static java.lang.Math.PI;\n" +
					"import static java.lang.Math.abs;\n" +
					"import static java.util.stream.Collectors.toList;\n" +
					"\n" +
					"import java.util.List;\n" +
					"\n" +
					"public class Foo6 {\n" +
					"    List list = List.of(1).stream().collect(toList());\n" +
					"    double i = abs(-1);\n" +
					"    double pi = PI;\n" +
					"}\n";
			//@formatter:on
			WillSaveTextDocumentParams params = new WillSaveTextDocumentParams();
			TextDocumentIdentifier document = new TextDocumentIdentifier();
			document.setUri(srcUri.toString());
			params.setTextDocument(document);
			List<TextEdit> result = handler.willSaveWaitUntil(params, monitor);
			Document doc = new Document();
			doc.set(cu.getSource());
			assertEquals(expected, ResourceUtils.dos2Unix(TextEditUtil.apply(doc, result)));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(Arrays.asList(favourites));
		}
	}

	@Test
	public void testMissingFormatterUrl() throws Exception {
		String formatterUrl = preferences.getFormatterUrl();
		try {
			preferences.setFormatterUrl("xxxx");
			URI srcUri = project.getFile("src/java/Foo4.java").getRawLocationURI();
			projectsManager.fileChanged(srcUri.toString(), CHANGE_TYPE.CHANGED);
		} catch (Exception e) {
			fail("Missing formatter url");
		} finally {
			preferences.setFormatterUrl(formatterUrl);
		}
	}

}
