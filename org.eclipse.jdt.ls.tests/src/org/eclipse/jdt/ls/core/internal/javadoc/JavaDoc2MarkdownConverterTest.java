/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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

import org.eclipse.jdt.ls.core.internal.Util;
import org.junit.Test;

/**
 * Tests Javadoc to Markdown conversion
 *
 * @author Fred Bricon
 */
public class JavaDoc2MarkdownConverterTest extends AbstractJavadocConverterTest {

	private static final String MARKDOWN_0 = "This Javadoc contains some `code`, a link to `IOException` and a table\n" +
			"\n" +
			"    | header 1 | header 2 |\n" +
			"    | -------- | -------- |\n" +
			"    | data 1   | data 2   |\n" +
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
	public void testGetAsReader() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_0);
		Reader reader1 = converter.getAsReader();
		Reader reader2 = converter.getAsReader();
		assertNotSame(reader1, reader2);
	}
}
