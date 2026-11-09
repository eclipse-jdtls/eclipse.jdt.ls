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
package org.eclipse.jdt.ls.core.internal.jsh;

/**
 * Wraps raw .jsh (JShell script) source in a synthetic top-level class/method so it becomes a
 * syntactically valid Java compilation unit, and tracks the character-offset mapping between the
 * original .jsh buffer and the synthetic wrapped source.
 * <p>
 * This is a pure, JDT-independent utility: it only does string synthesis and offset arithmetic.
 * It does not know about compilation units, buffers, or projects - those are wired up separately
 * (see the .jsh content-assist plan tracked in
 * https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3973).
 * <p>
 * The wrapper intentionally only handles the "whole buffer as one method body" case: every line
 * of the .jsh content is treated as a statement inside a single synthetic method. It does not
 * attempt to classify individual snippets (so JShell-style variable redefinition, or snippet-level
 * local class/method declarations interleaved with statements, are out of scope - see the v1 scope
 * notes on the tracking issue).
 */
public final class JshSnippetWrapper {

	/**
	 * Deliberately verbose and underscore-delimited (not '$'-based) to make collisions with real
	 * user code implausible, without relying on '$', which the JVM also uses to encode nested/inner
	 * class names in binary form - a plain top-level class name shouldn't contain it.
	 */
	public static final String SCRATCHPAD_CLASS_NAME = "__JdtLsJshScratchpad__";

	public static final String SCRATCHPAD_METHOD_NAME = "__run__";

	/**
	 * JShell's own default imports (see the JShell tool documentation / {@code jdk.jshell}), so
	 * completion and diagnostics behave the same way they would in a real JShell session.
	 */
	private static final String HEADER = "import java.io.*;"
			+ "import java.math.*;"
			+ "import java.net.*;"
			+ "import java.nio.file.*;"
			+ "import java.util.*;"
			+ "import java.util.concurrent.*;"
			+ "import java.util.function.*;"
			+ "import java.util.prefs.*;"
			+ "import java.util.regex.*;"
			+ "import java.util.stream.*;"
			+ "public class " + SCRATCHPAD_CLASS_NAME + " { void " + SCRATCHPAD_METHOD_NAME + "() throws Exception {\n";

	private static final String FOOTER = "\n}}\n";

	private JshSnippetWrapper() {
	}

	/**
	 * Wraps the given .jsh source. The original text is inserted verbatim between the generated
	 * header and footer - no transformation is applied to it.
	 *
	 * @param jshSource the raw .jsh buffer content; {@code null} is treated as empty
	 * @return the wrapped source plus the offset-mapping metadata needed to translate positions
	 *         between the original and wrapped buffers
	 */
	public static JshWrapResult wrap(String jshSource) {
		String source = jshSource == null ? "" : jshSource;
		String wrapped = HEADER + source + FOOTER;
		return new JshWrapResult(wrapped, HEADER.length(), source.length());
	}

	/**
	 * The result of wrapping a .jsh buffer: the synthesized source, plus offset translation
	 * between the original buffer and the synthesized one.
	 */
	public static final class JshWrapResult {

		private final String wrappedSource;
		private final int headerLength;
		private final int originalLength;

		private JshWrapResult(String wrappedSource, int headerLength, int originalLength) {
			this.wrappedSource = wrappedSource;
			this.headerLength = headerLength;
			this.originalLength = originalLength;
		}

		public String getWrappedSource() {
			return wrappedSource;
		}

		/**
		 * @param originalOffset a character offset into the original .jsh buffer, in
		 *            {@code [0, originalLength]}
		 * @return the corresponding offset into the wrapped source
		 * @throws IllegalArgumentException if {@code originalOffset} is out of range
		 */
		public int toSyntheticOffset(int originalOffset) {
			if (originalOffset < 0 || originalOffset > originalLength) {
				throw new IllegalArgumentException("originalOffset " + originalOffset + " out of range [0, " + originalLength + "]");
			}
			return headerLength + originalOffset;
		}

		/**
		 * @param syntheticOffset a character offset into the wrapped source
		 * @return the corresponding offset into the original .jsh buffer, or {@code -1} if
		 *         {@code syntheticOffset} falls inside the generated header or footer rather than
		 *         the original content
		 */
		public int toOriginalOffset(int syntheticOffset) {
			int originalOffset = syntheticOffset - headerLength;
			if (originalOffset < 0 || originalOffset > originalLength) {
				return -1;
			}
			return originalOffset;
		}
	}

}
