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

import org.eclipse.lsp4j.ClientCapabilities;

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
		return v3supported && capabilities.getTextDocument().getCompletion().getCompletionItem().getSnippetSupport();
	}

	public boolean isV3Supported() {
		return v3supported;
	}

	public boolean isFormattingDynamicRegistrationSupported() {
		return v3supported && capabilities.getTextDocument().getFormatting().getDynamicRegistration();
	}

	public boolean isRangeFormattingDynamicRegistrationSupported() {
		return v3supported && capabilities.getTextDocument().getRangeFormatting().getDynamicRegistration();
	}

	public boolean isCodeLensDynamicRegistrationSupported() {
		return v3supported && capabilities.getTextDocument().getCodeLens().getDynamicRegistration();
	}

	public boolean isSignatureHelpDynamicRegistrationSupported() {
		return v3supported && capabilities.getTextDocument().getSignatureHelp().getDynamicRegistration();
	}
}
