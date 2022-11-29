/*******************************************************************************
 * Copyright (c) 2017-2020 Red Hat Inc. and others.
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

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DiagnosticsTagSupport;
import org.eclipse.lsp4j.DynamicRegistrationCapabilities;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ResourceOperationKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

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

	public boolean isWorkspaceConfigurationSupported() {
		return v3supported && capabilities.getWorkspace() != null && isTrue(capabilities.getWorkspace().getConfiguration());
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

	public boolean isFoldgingRangeDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getFoldingRange());
	}

	public boolean isImplementationDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getImplementation());
	}

	public boolean isSelectionRangeDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getSelectionRange());
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

	public boolean isOverrideMethodsPromptSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("overrideMethodsPromptSupport", "false").toString());
	}

	public boolean isHashCodeEqualsPromptSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("hashCodeEqualsPromptSupport", "false").toString());
	}

	public boolean isAdvancedOrganizeImportsSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("advancedOrganizeImportsSupport", "false").toString());
	}

	public boolean isGenerateToStringPromptSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("generateToStringPromptSupport", "false").toString());
	}

	public boolean isAdvancedGenerateAccessorsSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("advancedGenerateAccessorsSupport", "false").toString());
	}

	public boolean isGenerateConstructorsPromptSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("generateConstructorsPromptSupport", "false").toString());
	}

	public boolean isGenerateDelegateMethodsPromptSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("generateDelegateMethodsPromptSupport", "false").toString());
	}

	public boolean isAdvancedExtractRefactoringSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("advancedExtractRefactoringSupport", "false").toString());
	}

	public boolean isExtractMethodInferSelectionSupported() {
		Object supportList = extendedClientCapabilities.getOrDefault("inferSelectionSupport", new ArrayList<>());
		return supportList instanceof List<?> list && list.contains("extractMethod");
	}

	public boolean isExtractVariableInferSelectionSupported() {
		Object supportList = extendedClientCapabilities.getOrDefault("inferSelectionSupport", new ArrayList<>());
		return supportList instanceof List<?> list && list.contains("extractVariable");
	}

	public boolean isExtractFieldInferSelectionSupported() {
		Object supportList = extendedClientCapabilities.getOrDefault("inferSelectionSupport", new ArrayList<>());
		return supportList instanceof List<?> list && list.contains("extractField");
	}

	public boolean isAdvancedIntroduceParameterRefactoringSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("advancedIntroduceParameterRefactoringSupport", "false").toString());
	}

	public boolean isMoveRefactoringSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("moveRefactoringSupport", "false").toString());
	}

	public boolean isClientHoverProviderRegistered() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("clientHoverProvider", "false").toString());
	}

	public boolean isClientDocumentSymbolProviderRegistered() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("clientDocumentSymbolProvider", "false").toString());
	}

	public boolean isActionableNotificationSupported() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("actionableNotificationSupported", "false").toString());
	}

	public boolean isActionableRuntimeNotificationSupport() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("actionableRuntimeNotificationSupport", "false").toString());
	}

	public boolean shouldLanguageServerExitOnShutdown() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("shouldLanguageServerExitOnShutdown", "false").toString());
	}

	public boolean isGradleChecksumWrapperPromptSupport() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("gradleChecksumWrapperPromptSupport", "false").toString());
	}

	public boolean isResolveAdditionalTextEditsSupport() {
		return Boolean.parseBoolean(extendedClientCapabilities.getOrDefault("resolveAdditionalTextEditsSupport", "false").toString());
	}

	/**
	 * The command which will be triggered when the completion item is selected. Different clients can have different
	 * command ids. The command id is set in the 'onCompletionItemSelectedCommand' field of the extended client capabilities.
	 */
	public String getCompletionItemCommand() {
		return String.valueOf(extendedClientCapabilities.getOrDefault("onCompletionItemSelectedCommand", ""));
	}

	public boolean isSupportsCompletionDocumentationMarkdown() {
		//@formatter:off
		return v3supported && capabilities.getTextDocument().getCompletion() != null
				&& capabilities.getTextDocument().getCompletion().getCompletionItem() != null
				&& capabilities.getTextDocument().getCompletion().getCompletionItem().getDocumentationFormat() != null
				&& capabilities.getTextDocument().getCompletion().getCompletionItem().getDocumentationFormat().contains(MarkupKind.MARKDOWN);
		//@formatter:on
	}

	@Deprecated
	public boolean isWorkspaceEditResourceChangesSupported() {
		return capabilities.getWorkspace() != null && capabilities.getWorkspace().getWorkspaceEdit() != null && isTrue(capabilities.getWorkspace().getWorkspaceEdit().getResourceChanges());
	}

	public boolean isResourceOperationSupported() {
		//@formatter:off
		return capabilities.getWorkspace() != null
				&& capabilities.getWorkspace().getWorkspaceEdit() != null
				&& capabilities.getWorkspace().getWorkspaceEdit().getResourceOperations() != null
				&& capabilities.getWorkspace().getWorkspaceEdit().getResourceOperations().contains(ResourceOperationKind.Create)
				&& capabilities.getWorkspace().getWorkspaceEdit().getResourceOperations().contains(ResourceOperationKind.Rename)
				&& capabilities.getWorkspace().getWorkspaceEdit().getResourceOperations().contains(ResourceOperationKind.Delete);
		//@formatter:on
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
		//@formatter:on
	}

	/**
	 * {@code true} if the client has explicitly set the
	 * {@code textDocument.codeAction.codeActionLiteralSupport.codeActionKind.valueSet}
	 * value. Otherwise, {@code false}.
	 */
	public boolean isSupportedCodeActionKind(String kind) {
		//@formatter:off
		return v3supported && capabilities.getTextDocument().getCodeAction() != null
				&& capabilities.getTextDocument().getCodeAction().getCodeActionLiteralSupport() != null
				&& capabilities.getTextDocument().getCodeAction().getCodeActionLiteralSupport().getCodeActionKind() != null
				&& capabilities.getTextDocument().getCodeAction().getCodeActionLiteralSupport().getCodeActionKind().getValueSet() != null
				&& capabilities.getTextDocument().getCodeAction().getCodeActionLiteralSupport().getCodeActionKind().getValueSet()
				.stream().filter(k -> kind.startsWith(k)).findAny().isPresent();
		//@formatter:on
	}

	/**
	 * {@code true} if the client has explicitly set the
	 * {@code textDocument.publishDiagnostics.tagSupport} to
	 * {@code true} when initializing the LS. Otherwise, {@code false}.
	 */
	public boolean isDiagnosticTagSupported() {
		//@formatter:off
		return v3supported && capabilities.getTextDocument().getPublishDiagnostics() != null
				&& capabilities.getTextDocument().getPublishDiagnostics().getTagSupport() != null
				&& isTagSupported(capabilities.getTextDocument().getPublishDiagnostics().getTagSupport());
		//@formatter:on
	}

	private boolean isTagSupported(Either<Boolean, DiagnosticsTagSupport> tagSupport) {
		return tagSupport.isLeft() ? tagSupport.getLeft() : tagSupport.getRight().getValueSet() != null;
	}

	public boolean isCallHierarchyDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getCallHierarchy());
	}

	public boolean isResolveCodeActionSupported() {
		//@formatter:off
		return v3supported && capabilities.getTextDocument().getCodeAction() != null
			&& capabilities.getTextDocument().getCodeAction().getDataSupport() != null
			&& capabilities.getTextDocument().getCodeAction().getDataSupport().booleanValue()
			&& capabilities.getTextDocument().getCodeAction().getResolveSupport() != null
			&& capabilities.getTextDocument().getCodeAction().getResolveSupport().getProperties() != null
			&& capabilities.getTextDocument().getCodeAction().getResolveSupport().getProperties().contains("edit");
		//@formatter:on
	}

	public boolean isCompletionItemTagSupported() {
		return v3supported
			&& capabilities.getTextDocument().getCompletion() != null
			&& capabilities.getTextDocument().getCompletion().getCompletionItem() != null
			&& capabilities.getTextDocument().getCompletion().getCompletionItem().getTagSupport() != null;
	}

	public boolean isSymbolTagSupported() {
		return v3supported
			&& capabilities.getTextDocument().getDocumentSymbol() != null
			&& capabilities.getTextDocument().getDocumentSymbol().getTagSupport() != null;
	}

	public boolean isCompletionInsertReplaceSupport() {
		return v3supported
			&& capabilities.getTextDocument().getCompletion() != null
			&& capabilities.getTextDocument().getCompletion().getCompletionItem() != null
			&& capabilities.getTextDocument().getCompletion().getCompletionItem().getInsertReplaceSupport() != null
			&& capabilities.getTextDocument().getCompletion().getCompletionItem().getInsertReplaceSupport().booleanValue();
	}

}
