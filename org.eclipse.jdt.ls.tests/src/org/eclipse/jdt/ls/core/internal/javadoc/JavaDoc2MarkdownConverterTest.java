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
			"""
		This Javadoc contains some ` code `, a link to `IOException` and a table

		| header 1 | header 2 |
		|----------|----------|
		| data 1   | data 2   |

		<br />

		literally \\<b\\>literal\\</b\\> and now a list:

		* **Coffee**
		  * Mocha
		  * Latte
		* Tea
		  * Darjeeling
		  * Early Grey

		<!-- -->

		* **Parameters:**
		  * **param1** the first parameter
		  * **param2** the 2nd parameter
		  * **param3**
		* **Returns:**
		  * some kind of result
		* **Throws:**
		  * NastyException a nasty exception
		  * IOException another nasty exception
		* **Author:**
		  * [Ralf](mailto:foo@bar.com)
		  * [Andrew](mailto:bar@foo.com)
		* **Since:**
		  * 1.0
		  * 0
		* @unknown
		  * unknown tag
		* @unknown
		  * another unknown tag""";

	private static final String MARKDOWN_TABLE_0=
			"""
		| Header 1 | Header 2 |
		|----------|----------|
		| Row 1A   | Row 1B   |
		| Row 2A   | Row 2B   |""";

	private static final String MARKDOWN_TABLE_1=
			"""
		|        |        |
		|--------|--------|
		| Row 0A | Row 0B |
		| Row 1A | Row 1B |
		| Row 2A | Row 2B |""";
	//@formatter:on

	static final String RAW_JAVADOC_HTML_1 = "<a href=\"file://some_location\">File</a>";
	static final String RAW_JAVADOC_HTML_2 = "<a href=\"jdt://some_location\">JDT</a>";
	static final String RAW_JAVADOC_HTML_SEE = "@see <a href=\"https://docs.oracle.com/javase/7/docs/api/\">Online docs for java</a>";
	static final String RAW_JAVADOC_HTML_PARAM = "@param someString the string to enter";
	static final String RAW_JAVADOC_HTML_SINCE = "@since 0.0.1";
	static final String RAW_JAVADOC_HTML_VERSION = "@version 0.0.1";
	static final String RAW_JAVADOC_HTML_THROWS = "@throws IOException";
	static final String RAW_JAVADOC_HTML_AUTHOR = """
			@author author one
			@author author two
			@author author three
			""";

	static final String RAW_JAVADOC_HTML_MIX = """
			A super important method.

			@see java.lang.String#split(String, int)
			@see java.lang.String#split(String)
			@author      JSR-666 Expert Group
			@author      Chuck Norris
			@since       1.666
			@spec        JSR-666""";

	@Test
	public void testBoundaries() throws IOException {
		assertTrue(new JavaDoc2MarkdownConverter("").getAsString().isEmpty());
		assertNull(new JavaDoc2MarkdownConverter((Reader)null).getAsString());
		assertNull(new JavaDoc2MarkdownConverter((String)null).getAsString());
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

		assertEquals("* **See Also:**\n  * [Online docs for java](https://docs.oracle.com/javase/7/docs/api/)", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testParamTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_PARAM);
		String convertedMarkdown = converter.getAsString();

		assertEquals("* **Parameters:**\n  * **someString** the string to enter", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testSinceTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_SINCE);
		String convertedMarkdown = converter.getAsString();

		assertEquals("* **Since:**\n  * 0.0.1", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testVersionTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_VERSION);
		String convertedMarkdown = converter.getAsString();

		assertEquals("* @version\n  * 0.0.1", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testThrowsTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_THROWS);
		String convertedMarkdown = converter.getAsString();

		assertEquals("* **Throws:**\n  * IOException", ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testAuthorTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_AUTHOR);
		String convertedMarkdown = converter.getAsString();
		String expected = """
				* **Author:**
				  * author one
				  * author two
				  * author three""";

		assertEquals(expected, ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testMixedTag() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(RAW_JAVADOC_HTML_MIX);
		String convertedMarkdown = converter.getAsString();
		String expected = """
				A super important method.

				* **See Also:**
				  * java.lang.String.split(String, int)
				  * java.lang.String.split(String)
				* **Author:**
				  * JSR-666 Expert Group
				  * Chuck Norris
				* **Since:**
				  * 1.666
				* @spec
				  * JSR-666""";

		assertEquals(expected, ResourceUtils.dos2Unix(convertedMarkdown));
	}

	@Test
	public void testCodeTag() throws IOException {
		var javadoc = """
				This is a method that does something.
				<pre>
				int x = 10;
				System.out.println(x);
				</pre>
				""";
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(javadoc);
		String convertedMarkdown = converter.getAsString();
		assertEquals("""
				This is a method that does something.

				```
				int x = 10;
				System.out.println(x);
				```""", convertedMarkdown);
	}

	private static String CHARSET_HTML_JAVADOC = """
			<div class="block">A named mapping between sequences of sixteen-bit Unicode <a href="../../lang/Character.html#unicode">code units</a> and sequences of
			bytes.  This class defines methods for creating decoders and encoders and
			for retrieving the various names associated with a charset.  Instances of
			this class are immutable.

			<p> This class also defines static methods for testing whether a particular
			charset is supported, for locating charset instances by name, and for
			constructing a map that contains every charset for which support is
			available in the current Java virtual machine.  Support for new charsets can
			be added via the service-provider interface defined in the <a href="../../../java/nio/charset/spi/CharsetProvider.html" title="class in java.nio.charset.spi"><code>CharsetProvider</code></a> class.

			</p><p> All of the methods defined in this class are safe for use by multiple
			concurrent threads.


			<a name="names"></a><a name="charenc"></a>
			</p><h2>Charset names</h2>

			<p> Charsets are named by strings composed of the following characters:

			</p><ul>

			  <li> The uppercase letters <tt>'A'</tt> through <tt>'Z'</tt>
			       (<tt>'\u0041'</tt>&nbsp;through&nbsp;<tt>'\u005a'</tt>),

			  </li><li> The lowercase letters <tt>'a'</tt> through <tt>'z'</tt>
			       (<tt>'\u0061'</tt>&nbsp;through&nbsp;<tt>'\u007a'</tt>),

			  </li><li> The digits <tt>'0'</tt> through <tt>'9'</tt>
			       (<tt>'\u0030'</tt>&nbsp;through&nbsp;<tt>'\u0039'</tt>),

			  </li><li> The dash character <tt>'-'</tt>
			       (<tt>'\u002d'</tt>,&nbsp;<small>HYPHEN-MINUS</small>),

			  </li><li> The plus character <tt>'+'</tt>
			       (<tt>'\u002b'</tt>,&nbsp;<small>PLUS SIGN</small>),

			  </li><li> The period character <tt>'.'</tt>
			       (<tt>'\u002e'</tt>,&nbsp;<small>FULL STOP</small>),

			  </li><li> The colon character <tt>':'</tt>
			       (<tt>'\u003a'</tt>,&nbsp;<small>COLON</small>), and

			  </li><li> The underscore character <tt>'_'</tt>
			       (<tt>'\u005f'</tt>,&nbsp;<small>LOW&nbsp;LINE</small>).

			</li></ul>

			A charset name must begin with either a letter or a digit.  The empty string
			is not a legal charset name.  Charset names are not case-sensitive; that is,
			case is always ignored when comparing charset names.  Charset names
			generally follow the conventions documented in <a href="http://www.ietf.org/rfc/rfc2278.txt"><i>RFC&nbsp;2278:&nbsp;IANA Charset
			Registration Procedures</i></a>.

			<p></p><p> Every charset has a <i>canonical name</i> and may also have one or more
			<i>aliases</i>.  The canonical name is returned by the <a href="../../../java/nio/charset/Charset.html#name--"><code>name</code></a> method
			of this class.  Canonical names are, by convention, usually in upper case.
			The aliases of a charset are returned by the <a href="../../../java/nio/charset/Charset.html#aliases--"><code>aliases</code></a>
			method.

			</p><p><a name="hn">Some charsets have an <i>historical name</i> that is defined for
			compatibility with previous versions of the Java platform.</a>  A charset's
			historical name is either its canonical name or one of its aliases.  The
			historical name is returned by the <tt>getEncoding()</tt> methods of the
			<a href="../../../java/io/InputStreamReader.html#getEncoding--"><code>InputStreamReader</code></a> and <a href="../../../java/io/OutputStreamWriter.html#getEncoding--"><code>OutputStreamWriter</code></a> classes.

			</p><p><a name="iana"> </a>If a charset listed in the <a href="http://www.iana.org/assignments/character-sets"><i>IANA Charset
			Registry</i></a> is supported by an implementation of the Java platform then
			its canonical name must be the name listed in the registry. Many charsets
			are given more than one name in the registry, in which case the registry
			identifies one of the names as <i>MIME-preferred</i>.  If a charset has more
			than one registry name then its canonical name must be the MIME-preferred
			name and the other names in the registry must be valid aliases.  If a
			supported charset is not listed in the IANA registry then its canonical name
			must begin with one of the strings <tt>"X-"</tt> or <tt>"x-"</tt>.

			</p><p> The IANA charset registry does change over time, and so the canonical
			name and the aliases of a particular charset may also change over time.  To
			ensure compatibility it is recommended that no alias ever be removed from a
			charset, and that if the canonical name of a charset is changed then its
			previous canonical name be made into an alias.


			</p><h2>Standard charsets</h2>



			<p><a name="standard">Every implementation of the Java platform is required to support the
			following standard charsets.</a>  Consult the release documentation for your
			implementation to see if any other charsets are supported.  The behavior
			of such optional charsets may differ between implementations.

			</p><blockquote><table width="80%" summary="Description of standard charsets">
			<tbody><tr><th align="left">Charset</th><th align="left">Description</th></tr>
			<tr><td valign="top"><tt>US-ASCII</tt></td>
			    <td>Seven-bit ASCII, a.k.a. <tt>ISO646-US</tt>,
			        a.k.a. the Basic Latin block of the Unicode character set</td></tr>
			<tr><td valign="top"><tt>ISO-8859-1&nbsp;&nbsp;</tt></td>
			    <td>ISO Latin Alphabet No. 1, a.k.a. <tt>ISO-LATIN-1</tt></td></tr>
			<tr><td valign="top"><tt>UTF-8</tt></td>
			    <td>Eight-bit UCS Transformation Format</td></tr>
			<tr><td valign="top"><tt>UTF-16BE</tt></td>
			    <td>Sixteen-bit UCS Transformation Format,
			        big-endian byte&nbsp;order</td></tr>
			<tr><td valign="top"><tt>UTF-16LE</tt></td>
			    <td>Sixteen-bit UCS Transformation Format,
			        little-endian byte&nbsp;order</td></tr>
			<tr><td valign="top"><tt>UTF-16</tt></td>
			    <td>Sixteen-bit UCS Transformation Format,
			        byte&nbsp;order identified by an optional byte-order mark</td></tr>
			</tbody></table></blockquote>

			<p></p><p> The <tt>UTF-8</tt> charset is specified by <a href="http://www.ietf.org/rfc/rfc2279.txt"><i>RFC&nbsp;2279</i></a>; the
			transformation format upon which it is based is specified in
			Amendment&nbsp;2 of ISO&nbsp;10646-1 and is also described in the <a href="http://www.unicode.org/unicode/standard/standard.html"><i>Unicode
			Standard</i></a>.

			</p><p> The <tt>UTF-16</tt> charsets are specified by <a href="http://www.ietf.org/rfc/rfc2781.txt"><i>RFC&nbsp;2781</i></a>; the
			transformation formats upon which they are based are specified in
			Amendment&nbsp;1 of ISO&nbsp;10646-1 and are also described in the <a href="http://www.unicode.org/unicode/standard/standard.html"><i>Unicode
			Standard</i></a>.

			</p><p> The <tt>UTF-16</tt> charsets use sixteen-bit quantities and are
			therefore sensitive to byte order.  In these encodings the byte order of a
			stream may be indicated by an initial <i>byte-order mark</i> represented by
			the Unicode character <tt>'\uFEFF'</tt>.  Byte-order marks are handled
			as follows:

			</p><ul>

			  <li><p> When decoding, the <tt>UTF-16BE</tt> and <tt>UTF-16LE</tt>
			  charsets interpret the initial byte-order marks as a <small>ZERO-WIDTH
			  NON-BREAKING SPACE</small>; when encoding, they do not write
			  byte-order marks. </p></li>


			  <li><p> When decoding, the <tt>UTF-16</tt> charset interprets the
			  byte-order mark at the beginning of the input stream to indicate the
			  byte-order of the stream but defaults to big-endian if there is no
			  byte-order mark; when encoding, it uses big-endian byte order and writes
			  a big-endian byte-order mark. </p></li>

			</ul>

			In any case, byte order marks occurring after the first element of an
			input sequence are not omitted since the same code is used to represent
			<small>ZERO-WIDTH NON-BREAKING SPACE</small>.

			<p></p><p> Every instance of the Java virtual machine has a default charset, which
			may or may not be one of the standard charsets.  The default charset is
			determined during virtual-machine startup and typically depends upon the
			locale and charset being used by the underlying operating system. </p>

			<p>The <a href="../../../java/nio/charset/StandardCharsets.html" title="class in java.nio.charset"><code>StandardCharsets</code></a> class defines constants for each of the
			standard charsets.

			</p><h2>Terminology</h2>

			<p> The name of this class is taken from the terms used in
			<a href="http://www.ietf.org/rfc/rfc2278.txt"><i>RFC&nbsp;2278</i></a>.
			In that document a <i>charset</i> is defined as the combination of
			one or more coded character sets and a character-encoding scheme.
			(This definition is confusing; some other software systems define
			<i>charset</i> as a synonym for <i>coded character set</i>.)

			</p><p> A <i>coded character set</i> is a mapping between a set of abstract
			characters and a set of integers.  US-ASCII, ISO&nbsp;8859-1,
			JIS&nbsp;X&nbsp;0201, and Unicode are examples of coded character sets.

			</p><p> Some standards have defined a <i>character set</i> to be simply a
			set of abstract characters without an associated assigned numbering.
			An alphabet is an example of such a character set.  However, the subtle
			distinction between <i>character set</i> and <i>coded character set</i>
			is rarely used in practice; the former has become a short form for the
			latter, including in the Java API specification.

			</p><p> A <i>character-encoding scheme</i> is a mapping between one or more
			coded character sets and a set of octet (eight-bit byte) sequences.
			UTF-8, UTF-16, ISO&nbsp;2022, and EUC are examples of
			character-encoding schemes.  Encoding schemes are often associated with
			a particular coded character set; UTF-8, for example, is used only to
			encode Unicode.  Some schemes, however, are associated with multiple
			coded character sets; EUC, for example, can be used to encode
			characters in a variety of Asian coded character sets.

			</p><p> When a coded character set is used exclusively with a single
			character-encoding scheme then the corresponding charset is usually
			named for the coded character set; otherwise a charset is usually named
			for the encoding scheme and, possibly, the locale of the coded
			character sets that it supports.  Hence <tt>US-ASCII</tt> is both the
			name of a coded character set and of the charset that encodes it, while
			<tt>EUC-JP</tt> is the name of the charset that encodes the
			JIS&nbsp;X&nbsp;0201, JIS&nbsp;X&nbsp;0208, and JIS&nbsp;X&nbsp;0212
			coded character sets for the Japanese language.

			</p><p> The native character encoding of the Java programming language is
			UTF-16.  A charset in the Java platform therefore defines a mapping
			between sequences of sixteen-bit UTF-16 code units (that is, sequences
			of chars) and sequences of bytes. </p></div>
					""";

	private static final String CHARSET_MD_JAVADOC = """
			A named mapping between sequences of sixteen-bit Unicode [code units](../../lang/Character.html#unicode) and sequences of bytes. This class defines methods for creating decoders and encoders and for retrieving the various names associated with a charset. Instances of this class are immutable.

			This class also defines static methods for testing whether a particular
			charset is supported, for locating charset instances by name, and for
			constructing a map that contains every charset for which support is
			available in the current Java virtual machine. Support for new charsets can
			be added via the service-provider interface defined in the [`CharsetProvider`](../../../java/nio/charset/spi/CharsetProvider.html "class in java.nio.charset.spi") class.

			All of the methods defined in this class are safe for use by multiple
			concurrent threads.


			Charset names
			-------------

			Charsets are named by strings composed of the following characters:

			* The uppercase letters `'A'` through `'Z'` (`'A'` through `'Z'`),
			* The lowercase letters `'a'` through `'z'` (`'a'` through `'z'`),
			* The digits `'0'` through `'9'` (`'0'` through `'9'`),
			* The dash character `'-'` (`'-'`, HYPHEN-MINUS),
			* The plus character `'+'` (`'+'`, PLUS SIGN),
			* The period character `'.'` (`'.'`, FULL STOP),
			* The colon character `':'` (`':'`, COLON), and
			* The underscore character `'_'` (`'_'`, LOW LINE).

			A charset name must begin with either a letter or a digit. The empty string is not a legal charset name. Charset names are not case-sensitive; that is, case is always ignored when comparing charset names. Charset names generally follow the conventions documented in [*RFC 2278: IANA Charset
			Registration Procedures*](http://www.ietf.org/rfc/rfc2278.txt).

			<br />

			Every charset has a *canonical name* and may also have one or more
			*aliases* . The canonical name is returned by the [`name`](../../../java/nio/charset/Charset.html#name--) method
			of this class. Canonical names are, by convention, usually in upper case.
			The aliases of a charset are returned by the [`aliases`](../../../java/nio/charset/Charset.html#aliases--)
			method.

			Some charsets have an *historical name* that is defined for
			compatibility with previous versions of the Java platform. A charset's
			historical name is either its canonical name or one of its aliases. The
			historical name is returned by the `getEncoding()` methods of the
			[`InputStreamReader`](../../../java/io/InputStreamReader.html#getEncoding--) and [`OutputStreamWriter`](../../../java/io/OutputStreamWriter.html#getEncoding--) classes.

			If a charset listed in the [*IANA Charset
			Registry*](http://www.iana.org/assignments/character-sets) is supported by an implementation of the Java platform then
			its canonical name must be the name listed in the registry. Many charsets
			are given more than one name in the registry, in which case the registry
			identifies one of the names as *MIME-preferred* . If a charset has more
			than one registry name then its canonical name must be the MIME-preferred
			name and the other names in the registry must be valid aliases. If a
			supported charset is not listed in the IANA registry then its canonical name
			must begin with one of the strings `"X-"` or `"x-"`.

			The IANA charset registry does change over time, and so the canonical
			name and the aliases of a particular charset may also change over time. To
			ensure compatibility it is recommended that no alias ever be removed from a
			charset, and that if the canonical name of a charset is changed then its
			previous canonical name be made into an alias.


			Standard charsets
			-----------------

			Every implementation of the Java platform is required to support the
			following standard charsets. Consult the release documentation for your
			implementation to see if any other charsets are supported. The behavior
			of such optional charsets may differ between implementations.

			> | Charset       | Description                                                                                    |
			> |:--------------|:-----------------------------------------------------------------------------------------------|
			> | `US-ASCII`    | Seven-bit ASCII, a.k.a. `ISO646-US`, a.k.a. the Basic Latin block of the Unicode character set |
			> | `ISO-8859-1 ` | ISO Latin Alphabet No. 1, a.k.a. `ISO-LATIN-1`                                                 |
			> | `UTF-8`       | Eight-bit UCS Transformation Format                                                            |
			> | `UTF-16BE`    | Sixteen-bit UCS Transformation Format, big-endian byte order                                   |
			> | `UTF-16LE`    | Sixteen-bit UCS Transformation Format, little-endian byte order                                |
			> | `UTF-16`      | Sixteen-bit UCS Transformation Format, byte order identified by an optional byte-order mark    |

			<br />

			The `UTF-8` charset is specified by [*RFC 2279*](http://www.ietf.org/rfc/rfc2279.txt); the
			transformation format upon which it is based is specified in
			Amendment 2 of ISO 10646-1 and is also described in the [*Unicode
			Standard*](http://www.unicode.org/unicode/standard/standard.html).

			The `UTF-16` charsets are specified by [*RFC 2781*](http://www.ietf.org/rfc/rfc2781.txt); the
			transformation formats upon which they are based are specified in
			Amendment 1 of ISO 10646-1 and are also described in the [*Unicode
			Standard*](http://www.unicode.org/unicode/standard/standard.html).

			The `UTF-16` charsets use sixteen-bit quantities and are
			therefore sensitive to byte order. In these encodings the byte order of a
			stream may be indicated by an initial *byte-order mark* represented by
			the Unicode character `'﻿'`. Byte-order marks are handled
			as follows:

			* When decoding, the `UTF-16BE` and `UTF-16LE`
			  charsets interpret the initial byte-order marks as a ZERO-WIDTH NON-BREAKING SPACE; when encoding, they do not write
			  byte-order marks.

			* When decoding, the `UTF-16` charset interprets the
			  byte-order mark at the beginning of the input stream to indicate the
			  byte-order of the stream but defaults to big-endian if there is no
			  byte-order mark; when encoding, it uses big-endian byte order and writes
			  a big-endian byte-order mark.

			In any case, byte order marks occurring after the first element of an input sequence are not omitted since the same code is used to represent ZERO-WIDTH NON-BREAKING SPACE.

			<br />

			Every instance of the Java virtual machine has a default charset, which
			may or may not be one of the standard charsets. The default charset is
			determined during virtual-machine startup and typically depends upon the
			locale and charset being used by the underlying operating system.

			The [`StandardCharsets`](../../../java/nio/charset/StandardCharsets.html "class in java.nio.charset") class defines constants for each of the
			standard charsets.

			Terminology
			-----------

			The name of this class is taken from the terms used in
			[*RFC 2278*](http://www.ietf.org/rfc/rfc2278.txt).
			In that document a *charset* is defined as the combination of
			one or more coded character sets and a character-encoding scheme.
			(This definition is confusing; some other software systems define
			*charset* as a synonym for *coded character set*.)

			A *coded character set* is a mapping between a set of abstract
			characters and a set of integers. US-ASCII, ISO 8859-1,
			JIS X 0201, and Unicode are examples of coded character sets.

			Some standards have defined a *character set* to be simply a
			set of abstract characters without an associated assigned numbering.
			An alphabet is an example of such a character set. However, the subtle
			distinction between *character set* and *coded character set*
			is rarely used in practice; the former has become a short form for the
			latter, including in the Java API specification.

			A *character-encoding scheme* is a mapping between one or more
			coded character sets and a set of octet (eight-bit byte) sequences.
			UTF-8, UTF-16, ISO 2022, and EUC are examples of
			character-encoding schemes. Encoding schemes are often associated with
			a particular coded character set; UTF-8, for example, is used only to
			encode Unicode. Some schemes, however, are associated with multiple
			coded character sets; EUC, for example, can be used to encode
			characters in a variety of Asian coded character sets.

			When a coded character set is used exclusively with a single
			character-encoding scheme then the corresponding charset is usually
			named for the coded character set; otherwise a charset is usually named
			for the encoding scheme and, possibly, the locale of the coded
			character sets that it supports. Hence `US-ASCII` is both the
			name of a coded character set and of the charset that encodes it, while
			`EUC-JP` is the name of the charset that encodes the
			JIS X 0201, JIS X 0208, and JIS X 0212
			coded character sets for the Japanese language.

			The native character encoding of the Java programming language is
			UTF-16. A charset in the Java platform therefore defines a mapping
			between sequences of sixteen-bit UTF-16 code units (that is, sequences
			of chars) and sequences of bytes.""";

	@Test
	public void testComplexJavadoc() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(CHARSET_HTML_JAVADOC);
		String convertedMarkdown = converter.getAsString();
		assertEquals(CHARSET_MD_JAVADOC, convertedMarkdown);
	}

	// @formatter:off
	private static final String WEIRD_TABLES="""
			<table class="borderless">
<caption style="display:none">Regular expression constructs, and what they match</caption>
<thead style="text-align:left">
<tr>
<th id="construct">Construct</th>
<th id="matches">Matches</th>
</tr>
</thead>
<tbody style="text-align:left">

<tr><th colspan="2" style="padding-top:20px" id="characters">Characters</th></tr>

<tr><th style="vertical-align:top; font-weight: normal" id="x"><i>x</i></th>
    <td headers="matches characters x">The character <i>x</i></td></tr>

<tr><th style="vertical-align:top; font-weight: normal" id="backslash"><code>nn</code></th>
    <td headers="matches characters backslash">The backslash character</td></tr>
    <td headers="matches characters ctrl_x">The control character corresponding to <i>x</i></td></tr>

<tr><th colspan="2" style="padding-top:20px" id="classes">Character classes</th></tr>

<tr><th style="vertical-align:top; font-weight:normal" id="simple"><code>[abc]</code></th>
    <td headers="matches classes simple"><code>a</code>, <code>b</code>, or <code>c</code> (simple class)</td></tr>
<tr><th style="vertical-align:top; font-weight:normal" id="negation"><code>[^abc]</code></th>
    <td headers="matches classes negation">Any character except <code>a</code>, <code>b</code>, or <code>c</code> (negation)</td></tr>
<tr><th style="vertical-align:top; font-weight:normal" id="range"><code>[a-zA-Z]</code></th>
    <td headers="matches classes range"><code>a</code> through <code>z</code>
        or <code>A</code> through <code>Z</code>, inclusive (range)</td></tr>
<tr><th style="vertical-align:top; font-weight:normal" id="subtraction2"><code>[a-z&amp;&amp;[^m-p]]</code></th>
    <td headers="matches classes subtraction2"><code>a</code> through <code>z</code>,
         and not <code>m</code> through <code>p</code>: <code>[a-lq-z]</code>(subtraction)</td></tr>

<tr><th colspan="2" style="padding-top:20px" id="predef">Predefined character classes</th></tr>

<tr><th style="vertical-align:top; font-weight:normal" id="any"><code>.</code></th>
    <td headers="matches predef any">Any character (may or may not match <a href="#lt">line terminators</a>)</td></tr>


<tr><th colspan="2" style="padding-top:20px" id="java">java.lang.Character classes (simple <a href="#jcc">java character type</a>)</th></tr>

<tr><th style="vertical-align:top; font-weight:normal" id="javaMirrored"><code>p{javaMirrored}</code></th>
    <td headers="matches java javaMirrored">Equivalent to java.lang.Character.isMirrored()</td></tr>

<tr><th colspan="2" style="padding-top:20px" id="unicode">Classes for Unicode scripts, blocks, categories and binary properties</th></tr>

<tr><th style="vertical-align:top; font-weight:normal" id="not_uppercase"><code>[p{L}&amp;&amp;[^p{Lu}]]</code></th>
    <td headers="matches unicode not_uppercase">Any letter except an

	uppercase letter (subtraction)</td></tr>

<tr><th colspan="2" style="padding-top:20px" id="bounds">Boundary matchers</th></tr>

<tr><th style="vertical-align:top; font-weight:normal" id="begin_line"><code>^</code></th>
    <td headers="matches bounds begin_line">The beginning of a line</td></tr>
<tr><th style="vertical-align:top; font-weight:normal" id="end_line"><code>$</code></th>
    <td headers="matches bounds end_line">The end of a line</td></tr>
    <td headers="matches bounds end_input_except_term">The end of the input but for the final
        <a href="#lt">terminator</a>, if&nbsp;any</td></tr>
			<tr><th style="vertical-align:top; font-weight:normal" id="end_input"><code>\\z</code></th>
    <td headers="matches bounds end_input">The end of the input</td></tr>

<tr><th colspan="2" style="padding-top:20px" id="grapheme">Unicode Extended Grapheme matcher</th></tr>

<tr><th style="vertical-align:top; font-weight:normal" id="grapheme_any"><code>X</code></th>
    <td headers="matches grapheme grapheme_any">Any Unicode extended grapheme cluster</td></tr>

</tbody>
</table>
""";

	private static final String WEIRD_TABLES_MD = """
|                         Construct                         |                              Matches                              |
|-----------------------------------------------------------|-------------------------------------------------------------------|
| **Characters**                                                                                                               ||
| ***x***                                                   | The character *x*                                                 |
| **`nn`**                                                  | The backslash character                                           |
| The control character corresponding to *x*                |
| **Character classes**                                                                                                        ||
| **`[abc]`**                                               | `a`, `b`, or `c` (simple class)                                   |
| **`[^abc]`**                                              | Any character except `a`, `b`, or `c` (negation)                  |
| **`[a-zA-Z]`**                                            | `a` through `z` or `A` through `Z`, inclusive (range)             |
| **`[a-z&&[^m-p]]`**                                       | `a` through `z`, and not `m` through `p`: `[a-lq-z]`(subtraction) |
| **Predefined character classes**                                                                                             ||
| **`.`**                                                   | Any character (may or may not match line terminators)             |
| **java.lang.Character classes (simple java character type)**                                                                 ||
| **`p{javaMirrored}`**                                     | Equivalent to java.lang.Character.isMirrored()                    |
| **Classes for Unicode scripts, blocks, categories and binary properties**                                                    ||
| **`[p{L}&&[^p{Lu}]]`**                                    | Any letter except an uppercase letter (subtraction)               |
| **Boundary matchers**                                                                                                        ||
| **`^`**                                                   | The beginning of a line                                           |
| **`$`**                                                   | The end of a line                                                 |
| The end of the input but for the final terminator, if any |
| **`\\z`**                                                  | The end of the input                                              |
| **Unicode Extended Grapheme matcher**                                                                                        ||
| **`X`**                                                   | Any Unicode extended grapheme cluster                             |
[Regular expression constructs, and what they match]""";


	// @formatter:on
	@Test
	public void testWeirdTablesJavadoc() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(WEIRD_TABLES);
		String convertedMarkdown = converter.getAsString();
		assertEquals(WEIRD_TABLES_MD, convertedMarkdown);
	}


	private static final String WEIRD_LI_HTML = """
<div>
<p> Perl constructs not supported by this class: </p>

 <ul>
    <li><p> Predefined character classes (Unicode character)
			    <p><tt>\\X&nbsp;&nbsp;&nbsp;&nbsp;</tt>Match Unicode
    <a href="http://www.unicode.org/reports/tr18/#Default_Grapheme_Clusters">
    <i>extended grapheme cluster</i></a>
    </p></li>

    <li><p> The backreference constructs, <tt>\\g{</tt><i>n</i><tt>}</tt> for
    the <i>n</i><sup>th</sup><a href="#cg">capturing group</a> and
			    <tt>\\g{</tt><i>name</i><tt>}</tt> for
    <a href="#groupname">named-capturing group</a>.
    </p></li>

    <li><p> The named character construct, <tt>\\N{</tt><i>name</i><tt>}</tt>
    for a Unicode character by its name.
    </p></li>

    <li><p> The conditional constructs
    <tt>(?(</tt><i>condition</i><tt>)</tt><i>X</i><tt>)</tt> and
    <tt>(?(</tt><i>condition</i><tt>)</tt><i>X</i><tt>|</tt><i>Y</i><tt>)</tt>,
    </p></li>

    <li><p> The embedded code constructs <tt>(?{</tt><i>code</i><tt>})</tt>
    and <tt>(??{</tt><i>code</i><tt>})</tt>,</p></li>

    <li><p> The embedded comment syntax <tt>(?#comment)</tt>, and </p></li>

			    <li><p> The preprocessing operations <tt>\\l</tt> <tt>&#92;u</tt>,
			    <tt>\\L</tt>, and <tt>\\U</tt>.  </p></li>

 </ul>

 <p> Constructs supported by this class but not by Perl: </p>
</div>""";

	private static final String WEIRD_LI_MD = """
			Perl constructs not supported by this class:

			* Predefined character classes (Unicode character)

			  `\\X `Match Unicode
			  [*extended grapheme cluster*](http://www.unicode.org/reports/tr18/#Default_Grapheme_Clusters)
			* The backreference constructs, `\\g{`*n* `}` for
			  the *n* ^th^capturing group and
			  `\\g{`*name* `}` for
			  named-capturing group.

			* The named character construct, `\\N{`*name* `}`
			  for a Unicode character by its name.

			* The conditional constructs
			  `(?(`*condition* `)`*X* `)` and
			  `(?(`*condition* `)`*X* `|`*Y* `)`,

			* The embedded code constructs `(?{`*code* `})`
			  and `(??{`*code* `})`,

			* The embedded comment syntax `(?#comment)`, and

			* The preprocessing operations `\\l` `\\u`,
			  `\\L`, and `\\U`.

			Constructs supported by this class but not by Perl:""";

	@Test
	public void testWeirdLiJavadoc() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(WEIRD_LI_HTML);
		String convertedMarkdown = converter.getAsString();
		assertEquals(WEIRD_LI_MD, convertedMarkdown);
	}

	// @formatter:off

	private static final String TABLES_BLOCKQUOTE_JAVADOC = """
			<p> Character classes may appear within other character classes, and
			  may be composed by the union operator (implicit) and the intersection
			  operator (<tt>&amp;&amp;</tt>).
			  The union operator denotes a class that contains every character that is
			  in at least one of its operand classes.  The intersection operator
			  denotes a class that contains every character that is in both of its
			  operand classes.

			  <p> The precedence of character-class operators is as follows, from
			  highest to lowest:

			  <blockquote><table border="0" cellpadding="1" cellspacing="0"
			               summary="Precedence of character class operators.">
			    <tr><th>1&nbsp;&nbsp;&nbsp;&nbsp;</th>
			      <td>Literal escape&nbsp;&nbsp;&nbsp;&nbsp;</td>
			      <td><tt>\\x</tt></td></tr>
			   <tr><th>2&nbsp;&nbsp;&nbsp;&nbsp;</th>
			      <td>Grouping</td>
			      <td><tt>[...]</tt></td></tr>
			   <tr><th>3&nbsp;&nbsp;&nbsp;&nbsp;</th>
			      <td>Range</td>
			      <td><tt>a-z</tt></td></tr>
			    <tr><th>4&nbsp;&nbsp;&nbsp;&nbsp;</th>
			      <td>Union</td>
			      <td><tt>[a-e][i-u]</tt></td></tr>
			    <tr><th>5&nbsp;&nbsp;&nbsp;&nbsp;</th>
			      <td>Intersection</td>
			      <td>{@code [a-z&&[aeiou]]}</td></tr>
			  </table></blockquote>

			  <p> Note that a different set of metacharacters are in effect inside
			  a character class than outside a character class. For instance, the
			  regular expression <tt>.</tt> loses its special meaning inside a
			  character class, while the expression <tt>-</tt> becomes a range
			  forming metacharacter.""";

	private static final String TABLES_BLOCKQUOTE_MD = """
Character classes may appear within other character classes, and
may be composed by the union operator (implicit) and the intersection
operator (`&&`).
The union operator denotes a class that contains every character that is
in at least one of its operand classes. The intersection operator
denotes a class that contains every character that is in both of its
operand classes.

The precedence of character-class operators is as follows, from
highest to lowest:

> |       |                |                  |
> |-------|----------------|------------------|
> | **1** | Literal escape | `\\x`             |
> | **2** | Grouping       | `[...]`          |
> | **3** | Range          | `a-z`            |
> | **4** | Union          | `[a-e][i-u]`     |
> | **5** | Intersection   | `[a-z&&[aeiou]]` |

Note that a different set of metacharacters are in effect inside
a character class than outside a character class. For instance, the
regular expression `.` loses its special meaning inside a
character class, while the expression `-` becomes a range
forming metacharacter.""";

	// @formatter:on

	@Test
	public void testTablesBlockquoteJavadoc() throws IOException {
		JavaDoc2MarkdownConverter converter = new JavaDoc2MarkdownConverter(TABLES_BLOCKQUOTE_JAVADOC);
		String convertedMarkdown = converter.getAsString();
		assertEquals(TABLES_BLOCKQUOTE_MD, convertedMarkdown);
	}

}
