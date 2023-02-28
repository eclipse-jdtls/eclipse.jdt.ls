/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.preferences;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeLensCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionItemTagSupportCapabilities;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.FormattingCapabilities;
import org.eclipse.lsp4j.InlayHintCapabilities;
import org.eclipse.lsp4j.InlayHintWorkspaceCapabilities;
import org.eclipse.lsp4j.RangeFormattingCapabilities;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.SignatureHelpCapabilities;
import org.eclipse.lsp4j.SymbolTagSupportCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClientPreferencesTest {

	private ClientPreferences prefs;

	@Mock
	private ClientCapabilities cap;

	@Mock
	private TextDocumentClientCapabilities text;

	@Mock
	private WorkspaceClientCapabilities workspace;

	@Before
	public void setup() {
		when(cap.getTextDocument()).thenReturn(text);
		when(cap.getWorkspace()).thenReturn(workspace);
		prefs = new ClientPreferences(cap);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testClientPreferences() {
		new ClientPreferences(null);
	}

	@Test
	public void testIsV3Supported() throws Exception {
		assertTrue(prefs.isV3Supported());

		prefs = new ClientPreferences(new ClientCapabilities());
		assertFalse(prefs.isV3Supported());
	}

	@Test
	public void testIsExecuteCommandDynamicRegistrationSupported() throws Exception {
		assertTrue(prefs.isV3Supported());
		assertFalse(prefs.isExecuteCommandDynamicRegistrationSupported());
		assertFalse(prefs.isWorkspaceSymbolDynamicRegistered());
		assertFalse(prefs.isWorkspaceChangeWatchedFilesDynamicRegistered());
		assertFalse(prefs.isWorkspaceFoldersSupported());
	}

	@Test
	public void testIsSignatureHelpSupported() throws Exception {
		assertFalse(prefs.isSignatureHelpSupported());
		when(text.getSignatureHelp()).thenReturn(new SignatureHelpCapabilities());
		assertTrue(prefs.isSignatureHelpSupported());
	}

	@Test
	public void testIsCompletionSnippetsSupported() throws Exception {
		assertFalse(prefs.isCompletionSnippetsSupported());
		when(text.getCompletion()).thenReturn(new CompletionCapabilities());
		assertFalse(prefs.isCompletionSnippetsSupported());
		when(text.getCompletion()).thenReturn(new CompletionCapabilities(new CompletionItemCapabilities(true)));
		assertTrue(prefs.isCompletionSnippetsSupported());
	}


	@Test
	public void testIsFormattingDynamicRegistrationSupported() throws Exception {
		assertFalse(prefs.isFormattingDynamicRegistrationSupported());
		when(text.getFormatting()).thenReturn(new FormattingCapabilities());
		assertFalse(prefs.isFormattingDynamicRegistrationSupported());
		when(text.getFormatting()).thenReturn(new FormattingCapabilities(true));
		assertTrue(prefs.isFormattingDynamicRegistrationSupported());
	}

	@Test
	public void testIsRangeFormattingDynamicRegistrationSupported() throws Exception {
		assertFalse(prefs.isRangeFormattingDynamicRegistrationSupported());
		when(text.getRangeFormatting()).thenReturn(new RangeFormattingCapabilities());
		assertFalse(prefs.isRangeFormattingDynamicRegistrationSupported());
		when(text.getRangeFormatting()).thenReturn(new RangeFormattingCapabilities(true));
		assertTrue(prefs.isRangeFormattingDynamicRegistrationSupported());
	}

	@Test
	public void testIsCodeLensDynamicRegistrationSupported() throws Exception {
		assertFalse(prefs.isCodeLensDynamicRegistrationSupported());
		when(text.getCodeLens()).thenReturn(new CodeLensCapabilities());
		assertFalse(prefs.isCodeLensDynamicRegistrationSupported());
		when(text.getCodeLens()).thenReturn(new CodeLensCapabilities(true));
		assertTrue(prefs.isCodeLensDynamicRegistrationSupported());
	}

	@Test
	public void testIsSignatureHelpDynamicRegistrationSupported() throws Exception {
		assertFalse(prefs.isSignatureHelpDynamicRegistrationSupported());
		when(text.getSignatureHelp()).thenReturn(new SignatureHelpCapabilities());
		assertFalse(prefs.isSignatureHelpDynamicRegistrationSupported());
		when(text.getSignatureHelp()).thenReturn(new SignatureHelpCapabilities(true));
		assertTrue(prefs.isSignatureHelpDynamicRegistrationSupported());
	}

	@Test
	public void testIsRenameDynamicRegistrationSupported() throws Exception {
		assertFalse(prefs.isRenameDynamicRegistrationSupported());
		when(text.getRename()).thenReturn(new RenameCapabilities());
		assertFalse(prefs.isRenameDynamicRegistrationSupported());
		when(text.getRename()).thenReturn(new RenameCapabilities(true));
		assertTrue(prefs.isRenameDynamicRegistrationSupported());
	}

	@Test
	public void testIsInlayHintDynamicRegistrationSupported() throws Exception {
		assertFalse(prefs.isInlayHintDynamicRegistered());
		when(text.getInlayHint()).thenReturn(new InlayHintCapabilities(true));
		assertTrue(prefs.isInlayHintDynamicRegistered());
	}

	@Test
	public void testIsInlayHintRefreshSupported() throws Exception {
		assertFalse(prefs.isInlayHintRefreshSupported());
		when(workspace.getInlayHint()).thenReturn(new InlayHintWorkspaceCapabilities(true));
		assertTrue(prefs.isInlayHintRefreshSupported());
	}

	@Test
	public void testIsHierarchicalDocumentSymbolSupported() throws Exception {
		DocumentSymbolCapabilities capabilities = new DocumentSymbolCapabilities();
		assertFalse(prefs.isHierarchicalDocumentSymbolSupported());
		when(text.getDocumentSymbol()).thenReturn(capabilities);
		assertFalse(prefs.isHierarchicalDocumentSymbolSupported());
		capabilities.setHierarchicalDocumentSymbolSupport(false);
		when(text.getDocumentSymbol()).thenReturn(capabilities);
		assertFalse(prefs.isHierarchicalDocumentSymbolSupported());
		capabilities.setHierarchicalDocumentSymbolSupport(true);
		when(text.getDocumentSymbol()).thenReturn(capabilities);
		assertTrue(prefs.isHierarchicalDocumentSymbolSupported());
	}

	@Test
	public void testIsCompletionItemTagSupported() throws Exception {
		assertFalse(prefs.isCompletionItemTagSupported());
		CompletionItemCapabilities itemCapabilities = new CompletionItemCapabilities();
		CompletionCapabilities capabilities = new CompletionCapabilities(itemCapabilities);
		when(text.getCompletion()).thenReturn(capabilities);
		itemCapabilities.setTagSupport(new CompletionItemTagSupportCapabilities());
		assertTrue(prefs.isCompletionItemTagSupported());
	}

	@Test
	public void testIsSymbolTagSupported() throws Exception {
		assertFalse(prefs.isSymbolTagSupported());
		DocumentSymbolCapabilities capabilities = new DocumentSymbolCapabilities();
		when(text.getDocumentSymbol()).thenReturn(capabilities);
		capabilities.setTagSupport(new SymbolTagSupportCapabilities());
		assertTrue(prefs.isSymbolTagSupported());
	}

}
