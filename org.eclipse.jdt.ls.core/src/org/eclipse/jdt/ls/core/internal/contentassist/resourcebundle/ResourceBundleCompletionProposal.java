/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.contentassist.resourcebundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.CompletionUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalRequestor;
import org.eclipse.jdt.ls.core.internal.contentassist.SortTextHelper;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemDefaults;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * Provides completion proposals for resource bundle keys.
 * Detects when completion is triggered inside ResourceBundle.getString() calls
 * and provides completions based on keys found in .properties files in the project.
 *
 * This class orchestrates the work of several helper classes:
 * - ResourceBundleContextDetector: Detects ResourceBundle context
 * - ResourceBundlePropertiesFinder: Finds and processes properties files
 * - ResourceBundleTextProcessor: Processes document text for completion
 */
public class ResourceBundleCompletionProposal {

	private final ResourceBundleContextDetector contextDetector;
	private final ResourceBundlePropertiesFinder propertiesFinder;
	private final ResourceBundleTextProcessor textProcessor;

	public ResourceBundleCompletionProposal() {
		this.contextDetector = new ResourceBundleContextDetector();
		this.propertiesFinder = new ResourceBundlePropertiesFinder();
		this.textProcessor = new ResourceBundleTextProcessor();
	}

	/**
	 * Gets completion proposals for resource bundle keys.
	 *
	 * @param cu the compilation unit
	 * @param offset the offset where completion was triggered
	 * @param collector the completion proposal requestor
	 * @param monitor the progress monitor
	 * @return list of completion items for resource bundle keys
	 */
	public List<CompletionItem> getProposals(ICompilationUnit cu, int offset, CompletionProposalRequestor collector, IProgressMonitor monitor) {
		if (cu == null) {
			return Collections.emptyList();
		}

		List<CompletionItem> result = new ArrayList<>();
		try {
			// Get method invocation context to extract bundle name and locale
			ResourceBundleContextDetector.ResourceBundleContext context = contextDetector.detectContext(cu, offset, monitor);
			if (context == null || context.bundleName == null || context.bundleName.isEmpty()) {
				return result;
			}
			String bundleName = context.bundleName;
			String locale = context.locale;

			// Find all properties files and extract keys with their values
			// Prioritize locale-specific files if locale is detected
			Map<String, String> keyValueMap = propertiesFinder.findResourceBundleKeys(cu.getJavaProject(), bundleName, locale, monitor);
			if (keyValueMap.isEmpty()) {
				return result;
			}

			// Create completion items for the keys
			IDocument document = JsonRpcHelpers.toDocument(cu.getBuffer());
			ResourceBundleTextProcessor.QuotePositions quotes = textProcessor.findQuotePositions(document, offset, context.invocation);
			boolean insideQuotes = quotes.openingQuote() >= 0;
			String prefix = textProcessor.getPrefix(document, offset, quotes);
			Range range = textProcessor.calculateRange(document, offset, prefix, quotes);

			CompletionItemDefaults completionItemDefaults = collector.getCompletionItemDefaults();
			boolean useItemDefaults = shouldUseItemDefaults(range, completionItemDefaults);

			// Filter keys by prefix and create completion items
			// keyValueMap already contains deduplicated keys (from LinkedHashMap)
			for (Map.Entry<String, String> entry : keyValueMap.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				if (prefix.isEmpty() || key.toLowerCase().startsWith(prefix.toLowerCase())) {
					CompletionItem item = createCompletionItem(key, value, range, useItemDefaults, completionItemDefaults, insideQuotes);
					result.add(item);
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error providing resource bundle key completions", e);
		}

		return result;
	}

	/**
	 * Determines whether to use completion item defaults for the edit range.
	 * Uses item defaults if the client supports it and the calculated range matches
	 * the default edit range from the completion item defaults.
	 *
	 * @param range the calculated range for the completion
	 * @param completionItemDefaults the completion item defaults from the collector
	 * @return true if item defaults should be used, false otherwise
	 */
	private boolean shouldUseItemDefaults(Range range, CompletionItemDefaults completionItemDefaults) {
		return JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences()
				.isCompletionListItemDefaultsPropertySupport("editRange")
				&& completionItemDefaults != null
				&& completionItemDefaults.getEditRange() != null
				&& completionItemDefaults.getEditRange().getLeft() != null
				&& range.equals(completionItemDefaults.getEditRange().getLeft());
	}

	/**
	 * Creates a completion item for a resource bundle key.
	 * @param insideQuotes true if we're inside quotes (insert just the key), false if outside quotes (insert "key")
	 */
	private CompletionItem createCompletionItem(String key, String value, Range range, boolean useItemDefaults, CompletionItemDefaults completionItemDefaults, boolean insideQuotes) {
		CompletionItem item = new CompletionItem();
		item.setLabel(key);
		item.setKind(CompletionItemKind.Property);
		// Use very high relevance to get lowest sort text (highest priority)
		// Regular completions use relevance * 16 + offsets (typically < 1,000,000)
		// Using a value close to MAX_RELEVANCE_VALUE ensures resource bundle keys appear first
		item.setSortText(SortTextHelper.convertRelevance(SortTextHelper.MAX_RELEVANCE_VALUE - 1000));
		item.setFilterText(key);

		// If we're not inside quotes, wrap the key in quotes
		String insertText = insideQuotes ? key : "\"" + key + "\"";

		if (useItemDefaults && completionItemDefaults != null) {
			item.setTextEditText(insertText);
		} else {
			item.setTextEdit(Either.forLeft(new TextEdit(range, insertText)));
		}

		CompletionUtils.setInsertTextFormat(item, completionItemDefaults);
		CompletionUtils.setInsertTextMode(item, completionItemDefaults);

		// Set the property value as documentation
		if (value != null) {
			// Format multiline values for markdown: replace "\n" with "  \n"
			String formattedValue = value.replace("\n", "  \n");
			MarkupContent documentation = new MarkupContent(MarkupKind.MARKDOWN, formattedValue);
			item.setDocumentation(documentation);
		}

		return item;
	}

}
