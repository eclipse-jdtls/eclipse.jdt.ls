/*******************************************************************************
 * Copyright (c) 2016-2025 Red Hat Inc. and others.
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
import java.util.Arrays;
import java.util.Set;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.html2md.converter.HtmlConverterOptions;
import com.vladsch.flexmark.html2md.converter.HtmlMarkdownWriter;
import com.vladsch.flexmark.html2md.converter.HtmlNodeConverterContext;
import com.vladsch.flexmark.html2md.converter.HtmlNodeRenderer;
import com.vladsch.flexmark.html2md.converter.HtmlNodeRendererHandler;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import com.vladsch.flexmark.util.sequence.RepeatedSequence;

/**
 * Converts JavaDoc tags into Markdown equivalent.
 *
 * @author Fred Bricon
 */
public class JavaDoc2MarkdownConverter extends AbstractJavaDocConverter {
	private static final String MARKDOWN_SPACE = "&nbsp;";
	private static final String LINEBREAK = "\n";
	private static final String DOUBLE_SPACE = "  ";

	private final FlexmarkHtmlConverter converter;
	private final DataHolder flexmarkOptions;
	private final HtmlConverterOptions myHtmlConverterOptions;

	public JavaDoc2MarkdownConverter(Reader reader) {
		super(reader);
		flexmarkOptions = initOptions();
		myHtmlConverterOptions = new HtmlConverterOptions(flexmarkOptions);
		converter = initConverter(flexmarkOptions);
	}

	public JavaDoc2MarkdownConverter(String javadoc) {
		super(javadoc);
		flexmarkOptions = initOptions();
		myHtmlConverterOptions = new HtmlConverterOptions(flexmarkOptions);
		converter = initConverter(flexmarkOptions);
	}

	private static DataHolder initOptions() {
		MutableDataSet flexmarkOptions = new MutableDataSet();

		String[] unwrappedTags = FlexmarkHtmlConverter.UNWRAPPED_TAGS.getDefaultValue();
		String[] extendedUnwrappedTags = Arrays.copyOf(unwrappedTags, unwrappedTags.length + 1);
		// Add <abbr> to list of unwrapped tags
		extendedUnwrappedTags[unwrappedTags.length] = FlexmarkHtmlConverter.ABBR_NODE;

		flexmarkOptions.set(FlexmarkHtmlConverter.UNWRAPPED_TAGS, extendedUnwrappedTags);
		flexmarkOptions.set(FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID, false);
		flexmarkOptions.set(FlexmarkHtmlConverter.TYPOGRAPHIC_SMARTS, false);
		return flexmarkOptions;
	}

	private FlexmarkHtmlConverter initConverter(DataHolder flexmarkOptions) {
		var converter = FlexmarkHtmlConverter.builder(flexmarkOptions).htmlNodeRendererFactory(options -> new HtmlNodeRenderer() {
			@Override
			public Set<HtmlNodeRendererHandler<?>> getHtmlNodeRendererHandlers() {
				return Set.of(
						// Treat <tt> tags like <code> tags (inline code with backticks)
						new HtmlNodeRendererHandler<>("tt", Element.class, JavaDoc2MarkdownConverter.this::processTt),
						// Treat <dfn> tags as italic text
						new HtmlNodeRendererHandler<>("dfn", Element.class, JavaDoc2MarkdownConverter.this::processDfn),
						new HtmlNodeRendererHandler<>(FlexmarkHtmlConverter.DL_NODE, Element.class, JavaDoc2MarkdownConverter.this::processDl));
			}
		}).build();
		return converter;
	}

	@Override
	public String convert(String html) {
		Document document = Jsoup.parse(html);
		sanitize(document);
		StringBuilder markdown = new StringBuilder();
		JavaLanguageServerPlugin.logInfo("Converting html to markdown");
		converter.convert(document, markdown, -1);
		return fixSnippet(markdown.toString());
	}

	/**
	 * Sanitizes the provided HTML document by:
	 * <ul>
	 * <li>Unwrapping anchor (<code>&lt;a&gt;</code>) tags that use only
	 * <code>name</code> (legacy anchors), <code>eclipse-javadoc:</code> protocol,
	 * or <code>#</code> hash hrefs, as these are either non-functional or intended
	 * for internal navigation that does not translate to Markdown.</li>
	 * <li>Normalizing <code>&lt;table&gt;</code> elements so that header cells
	 * (<code>&lt;th&gt;</code>) appear only within header rows
	 * (<code>&lt;thead&gt;</code>), and any <code>&lt;th&gt;</code> elements in
	 * <code>&lt;tbody&gt;</code> are converted to bold <code>&lt;td&gt;</code>
	 * cells for consistent Markdown rendering.</li>
	 * <li>Normalizing table rows that mix <code>&lt;th&gt;</code> and
	 * <code>&lt;td&gt;</code> so all cells are rendered as bold
	 * <code>&lt;td&gt;</code> elements for proper Markdown output.</li>
	 * <li>Separating consecutive <code>&lt;tt&gt;</code> and
	 * <code>&lt;code&gt;</code> elements with a space to avoid backtick collision
	 * in Markdown.</li>
	 * </ul>
	 *
	 * @param document
	 *                     the HTML document to sanitize before conversion to
	 *                     Markdown
	 */
	private void sanitize(Document document) {
		// Unwrap anchors with :
		// - only name attribute (keep content, remove tag),
		// - using eclipse-javadoc protocol (internal Eclipse links that don't work in non-Eclipse clients),
		// - using # anchors (internal links that don't work in Markdown)
		document.select("a[name]:not([href]), a[href^='eclipse-javadoc:'], a[href^='#']").forEach(anchor -> anchor.unwrap());

		// Add missing table headers and normalize table rows so that
		// - th cells are only allowed in thead,
		// - if there's already a thead, convert all th cells in the tbody to td with bold content
		// - mixed th/td rows are converted to td with bold content
		document.select("table").forEach(TableHelper::normalizeTableHeaders);

		//Separate consecutive tt/code tags to prevent broken backtick rendering
		separateConsecutiveCodeTags(document);
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
	private void processTt(Element element, HtmlNodeConverterContext context, HtmlMarkdownWriter out) {
		CharSequence text = element.ownText();
		BasedSequence basedText = BasedSequence.of(text);
		// Count existing backticks to determine how many backticks to use for wrapping
		int backTickCount = getMaxRepeatedChars(basedText, '`', 1);
		CharSequence backTicks = RepeatedSequence.repeatOf("`", backTickCount);
		context.inlineCode(() -> context.processTextNodes(element, false, backTicks));
	}

	/**
	 * Surrounds &lt;dfn&gt; content with "_".
	 *
	 * @param element
	 *                    the dfn element
	 * @param context
	 *                    the conversion context
	 * @param out
	 *                    the markdown writer
	 */
	private void processDfn(Element node, HtmlNodeConverterContext context, HtmlMarkdownWriter out) {
		String text = node.text();
		if (text.isBlank()) {
			out.append(text);
			return;
		}
		out.append("_").append(text).append("_");
	}

	/**
	 * Handles custom processing of HTML <code>&lt;dl&gt;</code> lists, including their
	 * <code>&lt;dt&gt;</code> (term) and <code>&lt;dd&gt;</code> (definition) children.
	 * <p>
	 * Converts a <code>&lt;dl&gt;</code> into Markdown, applying a custom format that includes:
	 * <ul>
	 *   <li>Term elements (<code>&lt;dt&gt;</code>) are followed by two spaces before a line break to create a definition-like appearance.</li>
	 *   <li>Definition elements (<code>&lt;dd&gt;</code>) are rendered without a ':' prefix.</li>
	 *   <li>Blank lines or line breaks are inserted as needed to properly separate terms and definitions.</li>
	 * </ul>
	 * This handler is based on the <a href="based on https://github.com/vsch/flexmark-java/blob/bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14/flexmark-html2md-converter/src/main/java/com/vladsch/flexmark/html2md/converter/internal/HtmlConverterCoreNodeRenderer.java#L545-L605">flexmark-java implementation</a> .
	 *
	 * @param element the <code>&lt;dl&gt;</code> element to process
	 * @param context the Markdown conversion context, providing utilities for node handling
	 * @param out     the Markdown writer to append output to
	 */
	private void processDl(Element element, HtmlNodeConverterContext context, HtmlMarkdownWriter out) {
		context.pushState(element);

		Node item;
		boolean lastWasDefinition = true;
		boolean firstItem = true;

		while ((item = context.next()) != null) {
			switch (item.nodeName().toLowerCase()) {
				case FlexmarkHtmlConverter.DT_NODE:
					out.blankLineIf(lastWasDefinition).lineIf(!firstItem);
					context.processTextNodes(item, false);
					out.lineWithTrailingSpaces(2);
					lastWasDefinition = false;
					firstItem = false;
					break;

				case FlexmarkHtmlConverter.DD_NODE:
					handleDefinition((Element) item, context, out);
					lastWasDefinition = true;
					firstItem = false;
					break;

				default:
					//context.processWrapped(item, true, false);
					break;
			}
		}

		context.popState(out);
	}

	private void handleDefinition(Element item, HtmlNodeConverterContext context, HtmlMarkdownWriter out) {
		context.pushState(item);
		int options = out.getOptions();
		Elements children = item.children();
		boolean firstIsPara = false;

		if (!children.isEmpty() && children.get(0).tagName().equalsIgnoreCase(FlexmarkHtmlConverter.P_NODE)) {
			// we need a blank line
			out.blankLine();
			firstIsPara = true;
		}

		int count = myHtmlConverterOptions.listContentIndent ? myHtmlConverterOptions.definitionMarkerSpaces + 1 : 4;
		CharSequence childPrefix = RepeatedSequence.repeatOf(" ", count);

		out.append(' ', myHtmlConverterOptions.definitionMarkerSpaces);//Customized for jdt.ls
		out.pushPrefix();
		out.addPrefix(childPrefix, true);
		out.setOptions(options);
		if (firstIsPara) {
			context.renderChildren(item, true, null);
		} else {
			context.processTextNodes(item, false);
		}
		out.lineWithTrailingSpaces(2);//Customized for jdt.ls
		out.popPrefix();
		context.popState(out);
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

	private String fixSnippet(String value) {
		if (value.contains(JavadocContentAccess2.SNIPPET)) {
			StringBuilder builder = new StringBuilder();
			value.lines().forEach((line) -> {
				if (line.contains(JavadocContentAccess2.SNIPPET)) {
					line = line.stripLeading();
					if (line.startsWith(JavadocContentAccess2.SNIPPET)) {
						line = line.replaceFirst(JavadocContentAccess2.SNIPPET, "");
						line = replaceLeadingSpaces(line);
						if (!line.endsWith(DOUBLE_SPACE)) {
							line += DOUBLE_SPACE;
						}
					}
				}
				builder.append(line);
				builder.append(LINEBREAK);
			});
			value = builder.toString();
		}
		return value;
	}

	private static String replaceLeadingSpaces(String str) {
		int i = 0;
		while (str.length() > i + 1 && str.charAt(i) == ' ') {
			str = str.replaceFirst(" ", MARKDOWN_SPACE);
			i += MARKDOWN_SPACE.length();
		}
		return str;
	}
}