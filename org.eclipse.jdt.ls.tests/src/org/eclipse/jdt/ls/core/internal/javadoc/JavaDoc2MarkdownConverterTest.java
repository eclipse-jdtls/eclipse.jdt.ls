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
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.Util;
import org.junit.Test;

/**
 * Tests Javadoc to Markdown conversion
 *
 * @author Fred Bricon
 */
public class JavaDoc2MarkdownConverterTest extends AbstractJavadocConverterTest {
	//@formatter:off
	private static final String MARKDOWN_0 =
			"This Javadoc contains some `code`, a link to `IOException` and a table\n" +
			"\n" +
			"| header 1 | header 2 |\n" +
			"| -------- | -------- |\n" +
			"| data 1   | data 2   |\n" +
			"\n" +
			"\n" +
			"literally <b>literal</b> and now a list:\n" +
			"\n" +
			" *  **Coffee**\n" +
			"    \n" +
			"     *  Mocha\n" +
			"     *  Latte\n" +
			" *  Tea\n" +
			"    \n" +
			"     *  Darjeeling\n" +
			"     *  Early Grey\n" +
			"\n" +
			" *  **Parameters:**\n" +
			"    \n" +
			"     *  **param1** the first parameter\n" +
			"     *  **param2** the 2nd parameter\n" +
			"     *  **param3**\n" +
			" *  **Returns:**\n" +
			"    \n" +
			"     *  some kind of result\n" +
			" *  **Throws:**\n" +
			"    \n" +
			"     *  NastyException a nasty exception\n" +
			"     *  IOException another nasty exception\n" +
			" *  **Author:**\n" +
			"    \n" +
			"     *  [Ralf](mailto:foo@bar.com)\n" +
			"     *  [Andrew](mailto:bar@foo.com)\n" +
			" *  **Since:**\n" +
			"    \n" +
			"     *  1.0\n" +
			"     *  0\n" +
			" *  @unknown\n" +
			"    \n" +
			"     *  unknown tag\n" +
			" *  @unknown\n" +
			"    \n" +
			"     *  another unknown tag";

	private static final String MARKDOWN_TABLE_0=
			"| Header 1 | Header 2 |\n" +
			"| -------- | -------- |\n" +
			"| Row 1A   | Row 1B   |\n" +
			"| Row 2A   | Row 2B   |";

	private static final String MARKDOWN_TABLE_1=
			"|        |        |\n" +
			"| ------ | ------ |\n" +
			"| Row 0A | Row 0B |\n" +
			"| Row 1A | Row 1B |\n" +
			"| Row 2A | Row 2B |";
	//@formatter:on

	static final String RAW_JAVADOC_HTML_1 = "<a href=\"file://some_location\">File</a>";
	static final String RAW_JAVADOC_HTML_2 = "<a href=\"jdt://some_location\">JDT</a>";
	static final String RAW_JAVADOC_HTML_SEE = "@see <a href=\"https://docs.oracle.com/javase/7/docs/api/\">Online docs for java</a>";
	static final String RAW_JAVADOC_HTML_PARAM = "@param someString the string to enter";
	static final String RAW_JAVADOC_HTML_SINCE = "@since 0.0.1";
	static final String RAW_JAVADOC_HTML_VERSION = "@version 0.0.1";
	static final String RAW_JAVADOC_HTML_THROWS = "@throws IOException";
	static final String RAW_JAVADOC_HTML_AUTHOR = "@author someAuthor";

	@Test
	public void testBoundaries() throws IOException {
		assertTrue(new JavaDoc2MarkdownConverter("").getAsString().isEmpty());
		assertNull(new JavaDoc2MarkdownConverter((Reader)null).getAsString());
		assertNull(new JavaDoc2MarkdownConverter((Reader)null).getAsReader());
		assertNull(new JavaDoc2MarkdownConverter((String)null).getAsString());
		assertNull(new JavaDoc2MarkdownConverter((String)null).getAsReader());
	}

	@Test
	public void testGetAsString() throws IOException {
		String result = new JavaDoc2MarkdownConverter(RAW_JAVADOC_0).getAsString();
		assertEquals(Util.convertToIndependentLineDelimiter(MARKDOWN_0), Util.convertToIndependentLineDelimiter(result));
	}

	@Test
	public void testMarkdownTableNoTHEAD() throws IOException {
		String result = new JavaDoc2MarkdownConverter(RAW_JAVADOC_TABLE_0).getAsString();
		assertEquals(Util.convertToIndependentLineDelimiter(MARKDOWN_TABLE_0), Util.convertToIndependentLineDelimiter(result));
	}

	@Test
	public void testMarkdownTableInsertBlankHeader() throws IOException {
		String result = new JavaDoc2MarkdownConverter(RAW_JAVADOC_TABLE_1).getAsString();
		assertEquals(Util.convertToIndependentLineDelimiter(MARKDOWN_TABLE_1), Util.convertToIndependentLineDelimiter(result));
	}

	@Test
	public void testGetAsReader() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_0);
		Reader reader1 = converter.getAsReader();
		Reader reader2 = converter.getAsReader();
		assertNotSame(reader1, reader2);
	}

	private String[] extractLabelAndURIFromLinkMarkdown(String markdown) {
		if (markdown == "") {
			return new String[] { "", "" };
		}

		Pattern pattern = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
		Matcher matcher = pattern.matcher(markdown);
		if (matcher.find() && matcher.groupCount() >= 2) {
			return new String[] { matcher.group(1), matcher.group(2) };
		}
		return new String[] { "", "" };
	}

	@Test
	public void testLinkToFileIsPresent() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_1);
		String convertedMarkdown = converter.getAsString();

		String[] labelAndURIFromMarkdown = extractLabelAndURIFromLinkMarkdown(convertedMarkdown);
		assertEquals("File", labelAndURIFromMarkdown[0]);
		assertEquals("file://some_location", labelAndURIFromMarkdown[1]);
	}

	@Test
	public void testLinkToJdtFileIsPresent() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_2);
		String convertedMarkdown = converter.getAsString();

		String[] labelAndURIFromMarkdown = extractLabelAndURIFromLinkMarkdown(convertedMarkdown);
		assertEquals("JDT", labelAndURIFromMarkdown[0]);
		assertEquals("jdt://some_location", labelAndURIFromMarkdown[1]);
	}

	@Test
	public void testSeeTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_SEE);
		String convertedMarkdown = converter.getAsString();

		assertEquals(" *  **See Also:**\n    \n     *  [Online docs for java](https://docs.oracle.com/javase/7/docs/api/)", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testParamTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_PARAM);
		String convertedMarkdown = converter.getAsString();

		assertEquals(" *  **Parameters:**\n    \n     *  **someString** the string to enter", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testSinceTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_SINCE);
		String convertedMarkdown = converter.getAsString();

		assertEquals(" *  **Since:**\n    \n     *  0.0.1", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testVersionTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_VERSION);
		String convertedMarkdown = converter.getAsString();

		assertEquals(" *  @version\n    \n     *  0.0.1", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testThrowsTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_THROWS);
		String convertedMarkdown = converter.getAsString();

		assertEquals(" *  **Throws:**\n    \n     *  IOException", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testAuthorTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_AUTHOR);
		String convertedMarkdown = converter.getAsString();

		assertEquals(" *  **Author:**\n    \n     *  someAuthor", ResourceUtils.dos2Unix(convertedMarkdown));
	}
}
