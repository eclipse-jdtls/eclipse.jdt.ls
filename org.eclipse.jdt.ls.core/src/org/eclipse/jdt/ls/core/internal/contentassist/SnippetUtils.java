/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.contentassist;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class SnippetUtils {

	private static final String MARKDOWN_LANGUAGE = "java";

	private SnippetUtils() {
	}

	public static Either<String, MarkupContent> beautifyDocument(String raw) {
		// remove the placeholder for the plain cursor like: ${0}, ${1:variable}
		String escapedString = raw.replaceAll("\\$\\{\\d:?(.*)\\}", "$1");

		if (JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences() != null
				&& JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isSupportsCompletionDocumentationMarkdown()) {
			MarkupContent markupContent = new MarkupContent();
			markupContent.setKind(MarkupKind.MARKDOWN);
			markupContent.setValue(String.format("```%s\n%s\n```", MARKDOWN_LANGUAGE, escapedString));
			return Either.forRight(markupContent);
		} else {
			return Either.forLeft(escapedString);
		}
	}
}
