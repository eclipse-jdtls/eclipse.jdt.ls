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
 * Tests Javadoc to Plain text conversion
 *
 * @author Fred Bricon
 */
public class JavaDoc2PlainTextConverterTest extends AbstractJavadocConverterTest {

	private static final String PLAINTEXT_0 = "This Javadoc contains some  code , a link to IOException and a table \n" +
			"header 1 header 2 \n" +
			"data 1 data 2 \n" +
			" literally <b>literal</b> and now a list: \n" +
			" * Coffee \n" +
			"   - Mocha \n" +
			"   - Latte \n" +
			" * Tea \n" +
			"   - Darjeeling \n" +
			"   - Early Grey \n" +
			" * Parameters:\n" +
			"   - param1 the first parameter\n" +
			"   - param2 the 2nd parameter\n" +
			"   - param3\n" +
			" * Returns:\n" +
			"   - some kind of result\n" +
			" * Throws:\n" +
			"   - NastyException a nasty exception\n" +
			"   - IOException another nasty exception\n" +
			" * Author:\n" +
			"   - Ralf <mailto:foo@bar.com>\n" +
			"   - Andrew <mailto:bar@foo.com>\n" +
			" * Since:\n" +
			"   - 1.0\n" +
			"   - 0\n" +
			" * @unknown\n" +
			"   - unknown tag\n" +
			" * @unknown\n" +
			"   - another unknown tag";

	@Test
	public void testBoundaries() throws IOException {
		assertTrue(new JavaDoc2PlainTextConverter("").getAsString().isEmpty());
		assertNull(new JavaDoc2PlainTextConverter((Reader) null).getAsString());
		assertNull(new JavaDoc2PlainTextConverter((Reader) null).getAsReader());
		assertNull(new JavaDoc2PlainTextConverter((String) null).getAsString());
		assertNull(new JavaDoc2PlainTextConverter((String) null).getAsReader());
	}

	@Test
	public void testGetAsString() throws IOException {
		String result = new JavaDoc2PlainTextConverter(RAW_JAVADOC_0).getAsString();
		assertEquals(Util.convertToIndependentLineDelimiter(PLAINTEXT_0),
				Util.convertToIndependentLineDelimiter(result));
	}

	@Test
	public void testGetAsReader() throws IOException {
		JavaDoc2PlainTextConverter converter = new JavaDoc2PlainTextConverter(RAW_JAVADOC_0);
		Reader reader1 = converter.getAsReader();
		Reader reader2 = converter.getAsReader();
		assertNotSame(reader1, reader2);
	}
}
