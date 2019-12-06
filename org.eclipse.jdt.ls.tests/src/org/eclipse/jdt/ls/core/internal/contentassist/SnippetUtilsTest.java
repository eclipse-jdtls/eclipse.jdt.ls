/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.contentassist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SnippetUtilsTest {

	//	@Mock
	protected PreferenceManager preferenceManager;
	private PreferenceManager oldPreferenceManager;

	@Before
	public void initProjectManager() throws Exception {
		preferenceManager = mock(PreferenceManager.class);
		oldPreferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
		JavaLanguageServerPlugin.setPreferencesManager(preferenceManager);
	}

	@After
	public void cleanup() throws Exception {
		JavaLanguageServerPlugin.setPreferencesManager(oldPreferenceManager);
	}

	@Test
	public void testWhenMarkDownSupported() {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isSupportsCompletionDocumentationMarkdown()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		String raw = "System.out.println(${0});";
		Either<String, MarkupContent> result = SnippetUtils.beautifyDocument(raw);

		assertNotNull(result);
		assertNull(result.getLeft());
		assertNotNull(result.getRight());

		assertEquals(result.getRight().getValue(), "```java\nSystem.out.println();\n```");
	}

	@Test
	public void testWhenMarkDownNotSupported() {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isSupportsCompletionDocumentationMarkdown()).thenReturn(Boolean.FALSE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		String raw = "System.out.println(${0});";
		Either<String, MarkupContent> result = SnippetUtils.beautifyDocument(raw);

		assertNotNull(result);
		assertNull(result.getRight());
		assertNotNull(result.getLeft());

		assertEquals(result.getLeft(), "System.out.println();");
	}

	@Test
	public void testComplicatedInput() {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isSupportsCompletionDocumentationMarkdown()).thenReturn(Boolean.FALSE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		//@formatter:off
		String raw = "/**\n" +
				" * ${1:A}\n" +
				" */\n" +
				"public class ${1:A} {\n" +
				"\n" +
				"\t${0}\n" +
				"}";
		//@formatter:on
		Either<String, MarkupContent> result = SnippetUtils.beautifyDocument(raw);

		//@formatter:off
		String expected = "/**\n" +
				" * A\n" +
				" */\n" +
				"public class A {\n" +
				"\n" +
				"\t\n" +
				"}";
		//@formatter:on

		assertEquals(result.getLeft(), expected);
	}

	@Test
	public void testMultipleVariablesInput() {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isSupportsCompletionDocumentationMarkdown()).thenReturn(Boolean.FALSE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		//@formatter:off
		String raw = "for (${1:int} ${2:i} = ${3:0}; ${2:i} < ${4:args.length}; ${2:i}++) {\n" +
				"\t${0}\n" +
				"}";
		//@formatter:on
		Either<String, MarkupContent> result = SnippetUtils.beautifyDocument(raw);

		//@formatter:off
		String expected = "for (int i = 0; i < args.length; i++) {\n" +
				"\t\n" +
				"}";
		//@formatter:on

		assertEquals(result.getLeft(), expected);
	}

	@Test
	public void testSelectedTextPlaceholder() {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isSupportsCompletionDocumentationMarkdown()).thenReturn(Boolean.FALSE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		//@formatter:off
		String raw = "for (${1:int} ${2:i} = ${3:0}; ${2:i} < ${4:args.length}; ${2:i}++) {\n" +
				"\t$TM_SELECTED_TEXT${0}\n" +
				"}";
		//@formatter:on
		Either<String, MarkupContent> result = SnippetUtils.beautifyDocument(raw);

		//@formatter:off
		String expected = "for (int i = 0; i < args.length; i++) {\n" +
				"\t\n" +
				"}";
		//@formatter:on

		assertEquals(result.getLeft(), expected);
	}
}
