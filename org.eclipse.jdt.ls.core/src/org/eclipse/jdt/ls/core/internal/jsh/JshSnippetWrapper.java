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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.ls.core.internal.jsh.JshSnippetClassifier.Kind;
import org.eclipse.jdt.ls.core.internal.jsh.JshSnippetSplitter.Chunk;

/**
 * Wraps raw .jsh (JShell script) source in a synthetic top-level class so it becomes a
 * syntactically valid Java compilation unit, and tracks the character-offset mapping between the
 * original .jsh buffer and the synthetic wrapped source.
 * <p>
 * This is a pure, JDT-independent utility (string synthesis and offset arithmetic); it does not
 * know about compilation units, buffers, or projects - those are wired up separately (see the .jsh
 * content-assist plan tracked in https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3973).
 * <p>
 * Unlike v1 (whole-buffer-as-one-method-body wrapping), this classifies each top-level chunk of
 * the buffer - see {@link JshSnippetSplitter}/{@link JshSnippetClassifier} - and places it where
 * Java actually requires it: imports at the top, method/type declarations as real members of the
 * synthetic class (Java has no local-method syntax, so these can't stay inside a method body the
 * way v1 left them), and everything else (variable declarations and bare statements) in one
 * {@value #SCRATCHPAD_METHOD_NAME} method body, in original relative order - exactly v1's
 * behavior for a buffer containing no imports or declarations. Each chunk keeps its own verbatim
 * text (no modifier rewriting needed: {@value #SCRATCHPAD_METHOD_NAME} is an instance method, so
 * it can see promoted declarations directly without them needing to be {@code static}).
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
	private static final String IMPORTS = "import java.io.*;"
			+ "import java.math.*;"
			+ "import java.net.*;"
			+ "import java.nio.file.*;"
			+ "import java.util.*;"
			+ "import java.util.concurrent.*;"
			+ "import java.util.function.*;"
			+ "import java.util.prefs.*;"
			+ "import java.util.regex.*;"
			+ "import java.util.stream.*;";

	private JshSnippetWrapper() {
	}

	/**
	 * Wraps the given .jsh source using {@link #SCRATCHPAD_CLASS_NAME} as the synthetic class
	 * name. Only usable when at most one such unit exists at a time (e.g. tests); production code
	 * backing multiple concurrently open .jsh files must use {@link #wrap(String, String)} with a
	 * name unique per document - see that method for why.
	 */
	public static JshWrapResult wrap(String jshSource) {
		return wrap(jshSource, SCRATCHPAD_CLASS_NAME);
	}

	/**
	 * Wraps the given .jsh source: splits it into top-level chunks, classifies each, and places it
	 * in the synthetic class - see the class javadoc for where each kind lands. Every chunk's text
	 * is inserted verbatim - no transformation is applied to it.
	 *
	 * @param jshSource the raw .jsh buffer content; {@code null} is treated as empty
	 * @param className the synthetic top-level class name to generate. Must be unique per
	 *            concurrently-open .jsh document (not just per project) if the backing
	 *            compilation unit's file name is also derived from it (as
	 *            {@code JshDocumentManager} does) - a file named {@code Foo_2.java} declaring
	 *            {@code public class Foo} is itself a compile error ("type must match file
	 *            name"), independent of anything in the wrapped .jsh content.
	 * @return the wrapped source plus the offset-mapping metadata needed to translate positions
	 *         between the original and wrapped buffers
	 */
	public static JshWrapResult wrap(String jshSource, String className) {
		String source = jshSource == null ? "" : jshSource;
		List<Chunk> chunks = JshSnippetSplitter.split(source);

		List<Chunk> imports = new ArrayList<>();
		List<Chunk> declarations = new ArrayList<>();
		List<Chunk> statements = new ArrayList<>();
		for (Chunk chunk : chunks) {
			Kind kind = JshSnippetClassifier.classify(source.substring(chunk.start(), chunk.end()));
			switch (kind) {
				case IMPORT -> imports.add(chunk);
				case DECLARATION -> declarations.add(chunk);
				case STATEMENT -> statements.add(chunk);
			}
		}

		StringBuilder wrapped = new StringBuilder();
		List<JshWrapResult.Segment> segments = new ArrayList<>(chunks.size());

		wrapped.append(IMPORTS);
		placeChunks(source, imports, wrapped, segments);
		wrapped.append("public class ").append(className).append(" {\n");
		placeChunks(source, declarations, wrapped, segments);
		wrapped.append("void ").append(SCRATCHPAD_METHOD_NAME).append("() throws Exception {\n");
		placeChunks(source, statements, wrapped, segments);
		wrapped.append("\n}}\n");

		return new JshWrapResult(wrapped.toString(), segments, source.length());
	}

	private static void placeChunks(String source, List<Chunk> chunks, StringBuilder wrapped, List<JshWrapResult.Segment> segments) {
		for (Chunk chunk : chunks) {
			int syntheticStart = wrapped.length();
			wrapped.append(source, chunk.start(), chunk.end());
			segments.add(new JshWrapResult.Segment(chunk.start(), chunk.end(), syntheticStart));
		}
	}

	/**
	 * The result of wrapping a .jsh buffer: the synthesized source, plus offset translation between
	 * the original buffer and the synthesized one. Unlike v1's single constant header-length shift,
	 * translation here is piecewise: one {@link Segment} per classified chunk, each mapping a
	 * verbatim-copied range of the original buffer to where it landed in the synthetic source.
	 */
	public static final class JshWrapResult {

		record Segment(int originalStart, int originalEnd, int syntheticStart) {

			boolean containsOriginal(int offset) {
				return (offset >= originalStart && offset < originalEnd) || (originalStart == originalEnd && offset == originalStart);
			}

			int syntheticEnd() {
				return syntheticStart + (originalEnd - originalStart);
			}

			boolean containsSynthetic(int offset) {
				return (offset >= syntheticStart && offset < syntheticEnd()) || (syntheticStart == syntheticEnd() && offset == syntheticStart);
			}
		}

		private final String wrappedSource;
		private final List<Segment> segments;
		private final int originalLength;
		private final Segment lastSegment;

		private JshWrapResult(String wrappedSource, List<Segment> segments, int originalLength) {
			this.wrappedSource = wrappedSource;
			this.segments = segments;
			this.originalLength = originalLength;
			Segment last = null;
			for (Segment segment : segments) {
				if (segment.originalEnd() == originalLength && (last == null || segment.originalStart() >= last.originalStart())) {
					last = segment;
				}
			}
			this.lastSegment = last;
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
			for (Segment segment : segments) {
				if (segment.containsOriginal(originalOffset)) {
					return segment.syntheticStart() + (originalOffset - segment.originalStart());
				}
			}
			// One past the last chunk's last character (e.g. the client's cursor sits at the very
			// end of the document) - same boundary-inclusive convention as v1's single-offset scheme.
			if (originalOffset == originalLength && lastSegment != null) {
				return lastSegment.syntheticEnd();
			}
			throw new IllegalArgumentException("originalOffset " + originalOffset + " not covered by any chunk - this indicates a bug in JshSnippetSplitter's full-coverage guarantee");
		}

		/**
		 * @param syntheticOffset a character offset into the wrapped source
		 * @return the corresponding offset into the original .jsh buffer, or {@code -1} if
		 *         {@code syntheticOffset} falls inside generated scaffolding (imports, the class/
		 *         method declaration lines, or trailing braces) rather than a verbatim chunk of the
		 *         original content
		 */
		public int toOriginalOffset(int syntheticOffset) {
			for (Segment segment : segments) {
				if (segment.containsSynthetic(syntheticOffset)) {
					return segment.originalStart() + (syntheticOffset - segment.syntheticStart());
				}
			}
			if (lastSegment != null && syntheticOffset == lastSegment.syntheticEnd()) {
				return originalLength;
			}
			return -1;
		}
	}

}
