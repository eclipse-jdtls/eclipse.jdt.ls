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

	public ClientPreferences(ClientCapabilities caps) {
		if(caps == null ) {
			throw new IllegalArgumentException("ClientCapabilities can not be null");
		}
		this.capabilities = caps;
		this.v3supported = capabilities.getTextDocument() !=null;
	}

	public boolean isSignatureHelpSupported(){
		return v3supported && capabilities.getTextDocument().getSignatureHelp() !=null;
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
}
