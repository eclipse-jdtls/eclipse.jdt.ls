/*******************************************************************************
 * Copyright (c) 2017-2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.preferences;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import java.util.Collections;
import java.util.Map;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DynamicRegistrationCapabilities;
import org.eclipse.lsp4j.MarkupKind;

/**
 * A wrapper around {@link ClientCapabilities}
 *
 * @author Gorkem Ercan
 *
 */
public class ClientPreferences {

	private final ClientCapabilities capabilities;
	private final boolean v3supported;
	private Map<String, Object> extendedClientCapabilities;

	public ClientPreferences(ClientCapabilities caps) {
		this(caps, null);
	}

	/**
	 * @param clientCapabilities
	 * @param extendedClientCapabilities
	 */
	public ClientPreferences(ClientCapabilities caps, Map<String, Object> extendedClientCapabilities) {
		if (caps == null) {
			throw new IllegalArgumentException("ClientCapabilities can not be null");
		}
		this.capabilities = caps;
		this.v3supported = capabilities.getTextDocument() != null;
		this.extendedClientCapabilities = extendedClientCapabilities == null ? Collections.emptyMap() : extendedClientCapabilities;
	}

	public boolean isSignatureHelpSupported() {
		return v3supported && capabilities.getTextDocument().getSignatureHelp() != null;
	}

	public boolean isWorkspaceFoldersSupported() {
		return capabilities.getWorkspace() != null && isTrue(capabilities.getWorkspace().getWorkspaceFolders());
	}

	public boolean isCompletionDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getCompletion());
	}

	public boolean isCompletionSnippetsSupported() {
		//@formatter:off
		return v3supported && capabilities.getTextDocument().getCompletion() != null
				&& capabilities.getTextDocument().getCompletion().getCompletionItem() != null
				&& isTrue(capabilities.getTextDocument().getCompletion().getCompletionItem().getSnippetSupport());
		//@formatter:on
	}

	public boolean isV3Supported() {
		return v3supported;
	}

	public boolean isFormattingDynamicRegistrationSupported() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getFormatting());
	}

	public boolean isRangeFormattingDynamicRegistrationSupported() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getRangeFormatting());
	}

	public boolean isOnTypeFormattingDynamicRegistrationSupported() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getOnTypeFormatting());
	}

	public boolean isCodeLensDynamicRegistrationSupported() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getCodeLens());
	}

	public boolean isSignatureHelpDynamicRegistrationSupported() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getSignatureHelp());
	}

	private boolean isDynamicRegistrationSupported(DynamicRegistrationCapabilities capability) {
		return capability != null && isTrue(capability.getDynamicRegistration());
	}

	public boolean isRenameDynamicRegistrationSupported() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getRename());
	}

	public boolean isExecuteCommandDynamicRegistrationSupported() {
		return v3supported && capabilities.getWorkspace() != null && isDynamicRegistrationSupported(capabilities.getWorkspace().getExecuteCommand());
	}

	public boolean isWorkspaceSymbolDynamicRegistered() {
		return v3supported && capabilities.getWorkspace() != null && isDynamicRegistrationSupported(capabilities.getWorkspace().getSymbol());
	}

	public boolean isWorkspaceChangeWatchedFilesDynamicRegistered() {
		return v3supported && capabilities.getWorkspace() != null && isDynamicRegistrationSupported(capabilities.getWorkspace().getDidChangeWatchedFiles());
	}

	public boolean isDocumentSymbolDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getDocumentSymbol());
	}

	public boolean isCodeActionDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getCodeAction());
	}

	public boolean isDefinitionDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getDefinition());
	}

	public boolean isTypeDefinitionDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getTypeDefinition());
	}

	public boolean isHoverDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getHover());
	}

	public boolean isReferencesDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getReferences());
	}

	public boolean isDocumentHighlightDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getDocumentHighlight());
	}

	public boolean isImplementationDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getImplementation());
	}

	public boolean isWillSaveRegistered() {
		return v3supported && capabilities.getTextDocument().getSynchronization() != null && isTrue(capabilities.getTextDocument().getSynchronization().getWillSave());
	}

	public boolean isWillSaveWaitUntilRegistered() {
		return v3supported && capabilities.getTextDocument().getSynchronization() != null && isTrue(capabilities.getTextDocument().getSynchronization().getWillSaveWaitUntil());
	}

	public boolean isWorkspaceApplyEditSupported() {
		return capabilities.getWorkspace() != null && isTrue(capabilities.getWorkspace().getApplyEdit());
	}

	public boolean isProgressReportSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("progressReportProvider", "false").toString());
	}

	public boolean isClassFileContentSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("classFileContentsSupport", "false").toString());
	}

	public boolean isSupportsCompletionDocumentationMarkdown() {
		//@formatter:off
		return v3supported && capabilities.getTextDocument().getCompletion() != null
				&& capabilities.getTextDocument().getCompletion().getCompletionItem() != null
				&& capabilities.getTextDocument().getCompletion().getCompletionItem().getDocumentationFormat() != null
				&& capabilities.getTextDocument().getCompletion().getCompletionItem().getDocumentationFormat().contains(MarkupKind.MARKDOWN);
		//@formatter:on
	}

	public boolean isWorkspaceEditResourceChangesSupported() {
		return capabilities.getWorkspace() != null && capabilities.getWorkspace().getWorkspaceEdit() != null && isTrue(capabilities.getWorkspace().getWorkspaceEdit().getResourceChanges());
	}

	/**
	 * {@code true} if the client has explicitly set the
	 * {@code textDocument.documentSymbol.hierarchicalDocumentSymbolSupport} to
	 * {@code true} when initializing the LS. Otherwise, {@code false}.
	 */
	public boolean isHierarchicalDocumentSymbolSupported() {
		//@formatter:off
		return v3supported
				&& capabilities.getTextDocument().getDocumentSymbol() != null
				&& capabilities.getTextDocument().getDocumentSymbol().getHierarchicalDocumentSymbolSupport() != null
				&& capabilities.getTextDocument().getDocumentSymbol().getHierarchicalDocumentSymbolSupport().booleanValue();
	}

	public boolean isSemanticHighlightingSupported() {
		//@formatter:off
		return v3supported && capabilities.getTextDocument().getSemanticHighlightingCapabilities() != null
				&& capabilities.getTextDocument().getSemanticHighlightingCapabilities().getSemanticHighlighting() != null
				&& capabilities.getTextDocument().getSemanticHighlightingCapabilities().getSemanticHighlighting().booleanValue();
		//@formatter:on
	}

	/**
	 * {@code true} if the client has explicitly set the
	 * {@code textDocument.documentSymbol.hierarchicalDocumentSymbolSupport} to
	 * {@code true} when initializing the LS. Otherwise, {@code false}.
	 */
	public boolean isSupportedCodeActionKind(String kind) {
		//@formatter:off
		return v3supported && capabilities.getTextDocument().getCodeAction() != null
				&& capabilities.getTextDocument().getCodeAction().getCodeActionLiteralSupport() != null
				&& capabilities.getTextDocument().getCodeAction().getCodeActionLiteralSupport().getCodeActionKind() != null
				&& capabilities.getTextDocument().getCodeAction().getCodeActionLiteralSupport().getCodeActionKind().getValueSet() != null
				&& capabilities.getTextDocument().getCodeAction().getCodeActionLiteralSupport().getCodeActionKind().getValueSet().contains(kind);
		//@formatter:on
	}
}
