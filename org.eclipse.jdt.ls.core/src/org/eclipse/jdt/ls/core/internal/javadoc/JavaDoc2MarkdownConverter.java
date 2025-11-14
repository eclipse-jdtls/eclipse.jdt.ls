/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.Reader;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.html2md.converter.HtmlMarkdownWriter;
import com.vladsch.flexmark.html2md.converter.HtmlNodeConverterContext;
import com.vladsch.flexmark.html2md.converter.HtmlNodeRenderer;
import com.vladsch.flexmark.html2md.converter.HtmlNodeRendererHandler;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import com.vladsch.flexmark.util.sequence.RepeatedSequence;

/**
 * Converts JavaDoc tags into Markdown equivalent.
 *
 * @author Fred Bricon
 */
public class JavaDoc2MarkdownConverter extends AbstractJavaDocConverter {

	private static final FlexmarkHtmlConverter CONVERTER = FlexmarkHtmlConverter.builder(new MutableDataSet().set(FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID, false)).htmlNodeRendererFactory(options -> new HtmlNodeRenderer() {
		@Override
		public Set<HtmlNodeRendererHandler<?>> getHtmlNodeRendererHandlers() {
			return Set.of(
					// Treat <tt> tags like <code> tags (inline code with backticks)
					new HtmlNodeRendererHandler<>("tt", Element.class, JavaDoc2MarkdownConverter::processTt));
		}
	}).build();

	public JavaDoc2MarkdownConverter(Reader reader) {
		super(reader);
	}

	public JavaDoc2MarkdownConverter(String javadoc) {
		super(javadoc);
	}

	@Override
	public String convert(String html) {
		Document document = Jsoup.parse(html);
		// Unwrap anchors with :
		// - only name attribute (keep content, remove tag),
		// - using eclipse-javadoc protocol (internal Eclipse links that don't work in non-Eclipse clients),
		// - using # anchors (internal links that don't work in Markdown)
		document.select("a[name]:not([href]), a[href^='eclipse-javadoc:'], a[href^='#']").forEach(anchor -> anchor.unwrap());

		// Add missing table headers and normalize table rows so that
		// - th cells are only allowed in thead,
		// - if there's already a thead, convert all th cells in the tbody to td with bold content
		// - mixed th/td rows are converted to td with bold content
		document.select("table").forEach(JavaDoc2MarkdownConverter::normalizeTableHeaders);

		//Separate consecutive tt/code tags to prevent broken backtick rendering
		separateConsecutiveCodeTags(document);

		StringBuilder markdown = new StringBuilder();
		CONVERTER.convert(document, markdown, -1);
		return markdown.toString();
	}

	/**
	 * Separates consecutive tt/code tags by adding a space between them. This
	 * prevents broken Markdown like `here``there` which would be misparsed.
	 *
	 * @param document
	 *                     the document to process
	 */
	private static void separateConsecutiveCodeTags(Document document) {
		// Process all tt and code tags together
		Elements codeElements = document.select("tt, code");
		for (Element code : codeElements) {
			Element nextSibling = code.nextElementSibling();
			if (nextSibling != null && (isCodeTag(nextSibling.tagName()))) {
				// Check if there's any text between them
				org.jsoup.nodes.Node nextNode = code.nextSibling();
				if (nextNode == nextSibling) {
					// No text node between them, add a space
					code.after(new org.jsoup.nodes.TextNode(" "));
				}
			}
		}
	}

	/**
	 * Checks if a tag name is a code-related tag (tt or code).
	 *
	 * @param tagName
	 *                    the tag name to check
	 * @return true if the tag is tt or code
	 */
	private static boolean isCodeTag(String tagName) {
		return "tt".equals(tagName) || "code".equals(tagName);
	}

	/**
	 * Process &lt;tt&gt; tags as inline code (similar to &lt;code&gt; tags). Wraps
	 * the content in backticks for Markdown inline code syntax.
	 *
	 * @param element
	 *                    the tt element
	 * @param context
	 *                    the conversion context
	 * @param out
	 *                    the markdown writer
	 */
	private static void processTt(Element element, HtmlNodeConverterContext context, HtmlMarkdownWriter out) {
		CharSequence text = element.ownText();
		BasedSequence basedText = BasedSequence.of(text);
		// Count existing backticks to determine how many backticks to use for wrapping
		int backTickCount = getMaxRepeatedChars(basedText, '`', 1);
		CharSequence backTicks = RepeatedSequence.repeatOf("`", backTickCount);
		context.inlineCode(() -> context.processTextNodes(element, false, backTicks));
	}

	/**
	 * Gets the maximum number of repeated characters in a sequence. Used to
	 * determine how many backticks are needed to wrap code that contains backticks.
	 *
	 * @param text
	 *                 the text to analyze
	 * @param ch
	 *                 the character to count
	 * @param min
	 *                 the minimum count to return
	 * @return the maximum repeated count + 1, or min if no repeats found
	 */
	private static int getMaxRepeatedChars(CharSequence text, char ch, int min) {
		int maxCount = 0;
		int currentCount = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == ch) {
				currentCount++;
				if (currentCount > maxCount) {
					maxCount = currentCount;
				}
			} else {
				currentCount = 0;
			}
		}
		return maxCount > 0 ? maxCount + 1 : min;
	}

	/**
	 * Adds a new row header if the given table doesn't have any.
	 *
	 * @param table
	 *                  the HTML table to check for a header
	 */
	private static void normalizeTableHeaders(Element table) {

		addMissingTableHeaders(table);

		Element thead = table.select("thead").first();
		if (thead != null) {
			Elements theadRows = thead.select("tr");

			// If thead has more than 1 row, move extra rows to tbody
			if (theadRows.size() > 1) {
				Element tbody = table.select("tbody").first();
				if (tbody == null) {
					tbody = new Element("tbody");
					table.appendChild(tbody);
				}
				// Move all rows except the first one from thead to the beginning of tbody
				for (int i = theadRows.size() - 1; i >= 1; i--) {
					Element row = theadRows.get(i);
					row.remove();
					tbody.prependChild(row);
				}
			}

			// Convert any remaining th cells in tbody to td with bold content
			table.select("tbody th").forEach(JavaDoc2MarkdownConverter::convertThToTd);
		}

		//Convert mixed th/td rows: replace th with bold td for proper Markdown rendering
		table.select("tr").forEach(JavaDoc2MarkdownConverter::normalizeMixedTableRow);
	}

	/**
	 * Adds a new header row if the given table doesn't have any.
	 *
	 * @param table
	 *                  the HTML table to check for a header
	 */
	private static void addMissingTableHeaders(Element table) {
		int numCols = 0;
		Elements theadElements = table.select("thead");
		if (!theadElements.isEmpty()) {
			return;
		}
		Element thead = new Element("thead");

		//Insert thead in 1st position in the table
		table.insertChildren(0, thead);
		Elements tbodyElements = table.select("tbody");
		if (!tbodyElements.isEmpty()) {
			Element tbody = tbodyElements.first();
			Elements tbodyRows = tbody.select("tr");
			if (!tbodyRows.isEmpty()) {
				// Move tbody's header row to thead
				Element potentialHeader = tbodyRows.first();
				int cols1stRow = potentialHeader.childrenSize();
				int thSize = potentialHeader.getElementsByTag("th").size();
				if (thSize == cols1stRow) { // first row contains <th> elements only, move it to the header
					thead.appendChild(potentialHeader);
					return;
				}

				// Find the largest number of columns in any row
				for (Element row : tbodyRows) {
					int colSize = row.getElementsByTag("td").size() + row.getElementsByTag("th").size();
					//Count the number of columns in the row, keeping the biggest count
					if (colSize > numCols) {
						numCols = colSize;
					}
				}
			}
		}
		if (numCols > 0 && thead.childrenSize() == 0) {
			//Create a new header row based on the number of columns already found
			Element newHeader = new Element("tr");
			for (int i = 0; i < numCols; i++) {
				newHeader.appendChild(new Element("th"));
			}
			// Add the new header row to the thead
			thead.appendChild(newHeader);
		}
	}

	/**
	 * Normalizes table rows that contain both th and td elements. Converts th
	 * elements to td with bold content for proper Markdown rendering.
	 *
	 * @param row
	 *                the table row to normalize
	 */
	private static void normalizeMixedTableRow(Element row) {
		Elements thElements = row.getElementsByTag("th");
		Elements tdElements = row.getElementsByTag("td");

		// Only process if row has both th and td elements
		if (!thElements.isEmpty() && !tdElements.isEmpty()) {
			thElements.forEach(JavaDoc2MarkdownConverter::convertThToTd);
		}
	}

	/**
	 * Converts a &lt;th&gt; element to a &lt;td&gt; element with bold content.
	 * Preserves all attributes and wraps the content in a strong tag for bold
	 * formatting.
	 *
	 * @param th
	 *               the &lt;th&gt; element to convert to a &lt;td&gt; element with
	 *               bold content
	 */
	private static void convertThToTd(Element th) {
		// Create a new td element with the same attributes
		Element td = new Element("td");
		td.attributes().addAll(th.attributes());

		// Wrap the content in <strong> to preserve bold formatting
		Element strong = new Element("strong");
		strong.appendChildren(th.childNodes());
		td.appendChild(strong);

		// Replace th with td
		th.replaceWith(td);
	}
}