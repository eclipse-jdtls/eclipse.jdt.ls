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
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemDefaults;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;

/**
 * @author snjeza
 *
 */
public final class CompletionUtils {

	private static final String ESCAPE_DOLLAR = "\\\\\\$";
	private static final String DOLLAR = "\\$";

	private CompletionUtils() {
		// No instanciation
	}

	public static String sanitizeCompletion(String replace) {
		return replace == null ? null : replace.replaceAll(DOLLAR, ESCAPE_DOLLAR);
	}

	/**
	 * Set the <code>InsertTextMode</code> to <code>InsertTextMode.AdjustIndentation</code> for the completion item as needed.
	 *
	 * @param item
	 *            the completion item
	 * @param completionItemDefaults
	 *            the completion itemDefaults of the completion list
	 */
	public static void setInsertTextMode(final CompletionItem item, CompletionItemDefaults completionItemDefaults) {
		var clientPreferences = JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences();
		if ((!clientPreferences.isCompletionListItemDefaultsPropertySupport("insertTextMode") ||
				completionItemDefaults.getInsertTextMode() == null ||
				completionItemDefaults.getInsertTextMode() != InsertTextMode.AdjustIndentation) &&
				clientPreferences.getCompletionItemInsertTextModeDefault() != InsertTextMode.AdjustIndentation
		) {
			item.setInsertTextMode(InsertTextMode.AdjustIndentation);
		}
	}

	/**
	 * Set the <code>InsertTextFormat</code> to <code>InsertTextFormat.Snippet</code> for the completion item if supported.
	 *
	 * @param item
	 *            the completion item
	 * @param completionItemDefaults
	 *            the completion itemDefaults of the completion list
	 */
	public static void setInsertTextFormat(final CompletionItem item, CompletionItemDefaults completionItemDefaults) {
		var clientPreferences = JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences();
		InsertTextFormat insertTextFormat = clientPreferences.isCompletionSnippetsSupported() ? InsertTextFormat.Snippet : InsertTextFormat.PlainText;
		if (!clientPreferences.isCompletionListItemDefaultsPropertySupport("insertTextFormat") ||
			completionItemDefaults.getInsertTextFormat() == null ||
			completionItemDefaults.getInsertTextFormat() != insertTextFormat
		) {
			item.setInsertTextFormat(insertTextFormat);
		}
	}

}
