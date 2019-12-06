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

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class SnippetUtils {

	private static final String MARKDOWN_LANGUAGE = "java";

	private static final String TM_SELECTED_TEXT = "\\$TM_SELECTED_TEXT";

	private SnippetUtils() {
	}

	public static Either<String, MarkupContent> beautifyDocument(String raw) {
		// remove the placeholder for the plain cursor like: ${0}, ${1:variable}
		String escapedString = raw.replaceAll("\\$\\{\\d:?(.*?)\\}", "$1");

		// Replace the reserved variable with empty string.
		// See: https://github.com/eclipse/eclipse.jdt.ls/issues/1220
		escapedString = escapedString.replaceAll(TM_SELECTED_TEXT, "");

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
