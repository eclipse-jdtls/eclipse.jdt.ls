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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.ls.core.internal.jsh.JshSnippetClassifier.FieldInfo;
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
 * Java actually requires it: imports at the top; method/type/variable declarations as real members
 * of the synthetic class (Java has no local-method syntax, so methods can't stay inside a method
 * body the way v1 left them; variables are promoted too, not just methods/types, so a
 * later-declared method can see an earlier-declared script variable, matching real JShell
 * semantics - the one exception is a {@code var}-typed variable, which can't be a field and stays
 * local to {@value #SCRATCHPAD_METHOD_NAME}); and everything else (bare statements) in one
 * {@value #SCRATCHPAD_METHOD_NAME} method body, in original relative order - exactly v1's
 * behavior for a buffer containing no imports or declarations. Each chunk keeps its own verbatim
 * text (no modifier rewriting needed: {@value #SCRATCHPAD_METHOD_NAME} is an instance method, so
 * it can see promoted declarations directly without them needing to be {@code static}).
 * <p>
 * A {@code var}-typed variable's exclusion from promotion is contagious: a later, otherwise
 * promotable field whose initializer references it (e.g. {@code var n = 2; int m = n;}) can't be
 * promoted as a normal field-with-initializer either - a field initializer runs once, at instance
 * construction time, before {@value #SCRATCHPAD_METHOD_NAME} is ever invoked, so it would capture
 * {@code n}'s default value instead of whatever it's really assigned later in the script.
 * {@link #wrap(String, String)} computes the affected set to a fixed point (via
 * {@link JshSnippetClassifier#parseAsField} and {@link JshSnippetClassifier#referencesAnyIdentifier})
 * so a whole chain of dependent fields (e.g. {@code var n = 2; int m = n; int p = m;}) is caught
 * together, not just the one directly touching the {@code var}. Each affected field is then
 * <em>split</em>, when {@link JshSnippetClassifier.FieldInfo#isSplittable()} allows it, into a bare
 * field declaration (so a later method can still see it as a member) plus an assignment statement
 * left in its original position in {@value #SCRATCHPAD_METHOD_NAME} (so the real value lands at the
 * same point in execution order a real JShell session would give it) - splitting one field doesn't
 * depend on whether the fields it references were themselves split or not, since
 * {@value #SCRATCHPAD_METHOD_NAME} sees both fields and its own locals in the same scope. A
 * {@code final} field, or one with more than one declared name (e.g. {@code int a = n, b = 3;}),
 * can't be legally split (a blank final can only be assigned in a constructor) and falls back to the
 * older behavior of fully demoting the whole chunk to one statement - a narrow, documented residual
 * limitation where a later method still can't see it.
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
	 * in the synthetic class - see the class javadoc for where each kind lands. Almost every chunk's
	 * text is inserted verbatim, with no transformation applied to it - the one exception is a field
	 * whose initializer depends on an excluded {@code var} (see the class javadoc), which is split
	 * into a declaration and an assignment placed in two different locations.
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
		int n = chunks.size();

		String[] texts = new String[n];
		Kind[] kinds = new Kind[n];
		FieldInfo[] fields = new FieldInfo[n];
		for (int i = 0; i < n; i++) {
			texts[i] = source.substring(chunks.get(i).start(), chunks.get(i).end());
			kinds[i] = JshSnippetClassifier.classify(texts[i]);
			fields[i] = JshSnippetClassifier.parseAsField(texts[i]);
		}

		// A field that would otherwise be promoted but whose initializer references a name that
		// isn't (or is no longer) a field - a var excluded from promotion, or another field already
		// demoted by this same loop - can't be promoted either, since that name stays local to
		// __run__ and wouldn't be visible from a field initializer. Iterate to a fixed point so a
		// whole chain of dependent fields (e.g. "var n = 2; int m = n; int p = m;") collapses back
		// to statements together, not just the one directly touching the var.
		Set<String> unavailable = new HashSet<>();
		for (FieldInfo field : fields) {
			if (field != null && field.isVar()) {
				unavailable.addAll(field.names());
			}
		}
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int i = 0; i < n; i++) {
				FieldInfo field = fields[i];
				if (field == null || field.isVar() || unavailable.containsAll(field.names())) {
					continue;
				}
				if (JshSnippetClassifier.referencesAnyIdentifier(texts[i], unavailable)) {
					unavailable.addAll(field.names());
					changed = true;
				}
			}
		}

		List<Piece> imports = new ArrayList<>();
		List<Piece> declarations = new ArrayList<>();
		List<Piece> statements = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Kind kind = kinds[i];
			FieldInfo field = fields[i];
			boolean dependsOnUnavailable = kind == Kind.DECLARATION && field != null && field.names().stream().anyMatch(unavailable::contains);
			if (dependsOnUnavailable && field.isSplittable()) {
				// Every character of the original chunk must stay covered by exactly one Segment (the
				// same "full coverage" guarantee a whole verbatim chunk gets), or a position landing on
				// the "=" or the trailing ";" would hit toSyntheticOffset's "not covered by any chunk"
				// exception. So the separator between name and initializer, and everything from the
				// initializer's end to the chunk's end (the original ";" plus any trailing text the
				// splitter included in the chunk), are copied verbatim too - only the field's own name
				// (echoed into the assignment statement) and the bare declaration's terminator have no
				// original counterpart and are synthesized with no backing segment.
				Chunk chunk = chunks.get(i);
				int chunkStart = chunk.start();
				int nameEnd = chunkStart + field.nameEnd();
				int initializerStart = chunkStart + field.initializerStart();
				int initializerEnd = chunkStart + field.initializerEnd();
				declarations.add(Piece.verbatim(source, chunkStart, nameEnd));
				declarations.add(Piece.synthesized(";\n"));
				statements.add(Piece.synthesized(field.names().get(0)));
				statements.add(Piece.verbatim(source, nameEnd, initializerStart));
				statements.add(Piece.verbatim(source, initializerStart, initializerEnd));
				statements.add(Piece.verbatim(source, initializerEnd, chunk.end()));
				continue;
			}
			if (dependsOnUnavailable) {
				kind = Kind.STATEMENT;
			}
			switch (kind) {
				case IMPORT -> imports.add(Piece.verbatim(source, chunks.get(i)));
				case DECLARATION -> declarations.add(Piece.verbatim(source, chunks.get(i)));
				case STATEMENT -> statements.add(Piece.verbatim(source, chunks.get(i)));
			}
		}

		StringBuilder wrapped = new StringBuilder();
		List<JshWrapResult.Segment> segments = new ArrayList<>(chunks.size());

		wrapped.append(IMPORTS);
		placePieces(imports, wrapped, segments);
		wrapped.append("public class ").append(className).append(" {\n");
		placePieces(declarations, wrapped, segments);
		wrapped.append("void ").append(SCRATCHPAD_METHOD_NAME).append("() throws Exception {\n");
		placePieces(statements, wrapped, segments);
		wrapped.append("\n}}\n");

		return new JshWrapResult(wrapped.toString(), segments, source.length());
	}

	private static void placePieces(List<Piece> pieces, StringBuilder wrapped, List<JshWrapResult.Segment> segments) {
		for (Piece piece : pieces) {
			int syntheticStart = wrapped.length();
			wrapped.append(piece.text());
			if (piece.originalStart() != null) {
				segments.add(new JshWrapResult.Segment(piece.originalStart(), piece.originalEnd(), syntheticStart));
			}
		}
	}

	/**
	 * One piece of text to append into the synthetic wrapped source, with an optional backing range
	 * in the original .jsh buffer. Most chunks produce exactly one verbatim {@link Piece} (text and
	 * original range are the same thing, just copied) - same as before this class could split a
	 * field's declaration from its initializer. A split field instead produces several: a verbatim
	 * piece for the "type name" prefix, synthesized text with no original backing (e.g. the
	 * {@code " = "} and trailing {@code ";\n"}, analogous to other generated scaffolding like the
	 * class header), and a verbatim piece for the initializer expression. {@code originalStart}/
	 * {@code originalEnd} are {@code null} for a synthesized piece, meaning no {@link
	 * JshWrapResult.Segment} is recorded for it - a request landing in that text behaves like a
	 * request landing in any other generated scaffolding (translates to {@code -1}).
	 */
	private record Piece(String text, Integer originalStart, Integer originalEnd) {

		static Piece verbatim(String source, Chunk chunk) {
			return verbatim(source, chunk.start(), chunk.end());
		}

		static Piece verbatim(String source, int start, int end) {
			return new Piece(source.substring(start, end), start, end);
		}

		static Piece synthesized(String text) {
			return new Piece(text, null, null);
		}
	}

	/**
	 * The result of wrapping a .jsh buffer: the synthesized source, plus offset translation between
	 * the original buffer and the synthesized one. Unlike v1's single constant header-length shift,
	 * translation here is piecewise: one {@link Segment} per verbatim-copied {@link Piece}, each
	 * mapping a range of the original buffer to where it landed in the synthetic source - usually one
	 * per chunk, but a split field (see the class javadoc) produces two, one for its declaration and
	 * one for its initializer, since those two pieces of original text land in different places.
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
