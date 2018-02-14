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
package org.eclipse.jdt.ls.core.internal.preferences;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DynamicRegistrationCapabilities;

/**
 * A wrapper around {@link ClientCapabilities}
 *
 * @author Gorkem Ercan
 *
 */
public class ClientPreferences {

	private final ClientCapabilities capabilities;
	private final boolean v3supported;
	private boolean hasWorkspaceFolderCapability;

	public ClientPreferences(ClientCapabilities caps) {
		if (caps == null) {
			throw new IllegalArgumentException("ClientCapabilities can not be null");
		}
		this.capabilities = caps;
		this.v3supported = capabilities.getTextDocument() != null;
		this.hasWorkspaceFolderCapability = false;
	}

	public boolean isSignatureHelpSupported() {
		return v3supported && capabilities.getTextDocument().getSignatureHelp() != null;
	}

	public boolean isWorkspaceFoldersSupported() {
		return this.hasWorkspaceFolderCapability;
	}

	/* workaround for https://github.com/eclipse/lsp4j/issues/124 */
	public void setWorkspaceFoldersSupported(boolean capability) {
		this.hasWorkspaceFolderCapability = capability;
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
		return v3supported && isDynamicRegistrationSupported(capabilities.getWorkspace().getExecuteCommand());
	}

	public boolean isWorkspaceSymbolDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getWorkspace().getSymbol());
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

	public boolean isHoverDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getHover());
	}

	public boolean isReferencesDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getReferences());
	}

	public boolean isDocumentHighlightDynamicRegistered() {
		return v3supported && isDynamicRegistrationSupported(capabilities.getTextDocument().getDocumentHighlight());
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

}
