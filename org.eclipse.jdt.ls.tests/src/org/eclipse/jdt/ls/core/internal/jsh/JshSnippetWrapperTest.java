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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.ls.core.internal.jsh.JshSnippetWrapper.JshWrapResult;
import org.junit.jupiter.api.Test;

public class JshSnippetWrapperTest {

	@Test
	public void testWrappedSourceContainsOriginalContentVerbatim() {
		// Deliberately two bare statements, not variable declarations - those are promoted to
		// fields (a different section of the synthetic source, see testRealisticMixedContentPlaces
		// EachChunkCorrectlyAndRoundTrips below), so they'd no longer appear contiguously together.
		String source = "System.out.println(1);\nSystem.out.println(2);";
		JshWrapResult result = JshSnippetWrapper.wrap(source);
		assertTrue(result.getWrappedSource().contains(source), "Wrapped source should contain the original content verbatim");
	}

	@Test
	public void testWrappedSourceIsSyntacticallyPlausible() {
		JshWrapResult result = JshSnippetWrapper.wrap("int x = 1;");
		String wrapped = result.getWrappedSource();
		assertTrue(wrapped.contains("public class " + JshSnippetWrapper.SCRATCHPAD_CLASS_NAME));
		assertTrue(wrapped.contains(JshSnippetWrapper.SCRATCHPAD_METHOD_NAME + "()"));
		// Class name must match what would be the compilation unit's simple name, same as the
		// production usage (<SCRATCHPAD_CLASS_NAME>.java), so "public class must match filename"
		// never fires for the synthetic unit.
		assertEquals(0, wrapped.indexOf("import"));
	}

	@Test
	public void testOffsetRoundTripsForEveryPositionInOriginal() {
		String source = "int x = 1;\nSystem.out.println(x);\n";
		JshWrapResult result = JshSnippetWrapper.wrap(source);
		for (int originalOffset = 0; originalOffset <= source.length(); originalOffset++) {
			int syntheticOffset = result.toSyntheticOffset(originalOffset);
			assertEquals(originalOffset, result.toOriginalOffset(syntheticOffset),
					"Round trip failed for original offset " + originalOffset);
		}
	}

	@Test
	public void testSyntheticOffsetPointsAtTheRightCharacter() {
		String source = "int x = 1;\nSystem.out.println(x);";
		JshWrapResult result = JshSnippetWrapper.wrap(source);
		int originalOffset = source.indexOf("println");
		int syntheticOffset = result.toSyntheticOffset(originalOffset);
		assertEquals('p', result.getWrappedSource().charAt(syntheticOffset));
	}

	@Test
	public void testToOriginalOffsetReturnsNegativeOneInsideHeader() {
		JshWrapResult result = JshSnippetWrapper.wrap("int x = 1;");
		assertEquals(-1, result.toOriginalOffset(0), "Offset 0 is inside the generated imports/class header");
	}

	@Test
	public void testToOriginalOffsetReturnsNegativeOneInsideFooter() {
		JshWrapResult result = JshSnippetWrapper.wrap("int x = 1;");
		int pastEnd = result.getWrappedSource().length() - 1;
		assertEquals(-1, result.toOriginalOffset(pastEnd), "The last character is inside the generated footer");
	}

	@Test
	public void testToSyntheticOffsetRejectsOutOfRangeInput() {
		JshWrapResult result = JshSnippetWrapper.wrap("int x = 1;");
		assertThrows(IllegalArgumentException.class, () -> result.toSyntheticOffset(-1));
		assertThrows(IllegalArgumentException.class, () -> result.toSyntheticOffset("int x = 1;".length() + 1));
	}

	@Test
	public void testEmptySnippet() {
		JshWrapResult result = JshSnippetWrapper.wrap("");
		// The whole wrapped source is header+footer with nothing in between; offset 0 of the
		// (empty) original content maps to a synthetic offset that round-trips back to 0.
		assertEquals(0, result.toOriginalOffset(result.toSyntheticOffset(0)));
	}

	@Test
	public void testNullSnippetTreatedAsEmpty() {
		JshWrapResult result = JshSnippetWrapper.wrap(null);
		assertEquals(0, result.toOriginalOffset(result.toSyntheticOffset(0)));
	}

	/**
	 * Regression test for the scope-widening fix (snippet classification replacing v1's
	 * whole-buffer-as-one-method-body wrapping): a realistic script with imports, variable
	 * declarations, a helper method, and a trailing statement must place each where Java actually
	 * requires it, and every original offset must still round-trip correctly despite the reordering.
	 */
	@Test
	public void testRealisticMixedContentPlacesEachChunkCorrectlyAndRoundTrips() {
		String content = "import java.util.concurrent.atomic.AtomicInteger;\n"
				+ "import java.util.List;\n"
				+ "\n"
				+ "String greeting = \"Hello\";\n"
				+ "List<String> items = List.of(\"a\", \"b\");\n"
				+ "\n"
				+ "void printSummary(String msg, List<String> list) {\n"
				+ "    System.out.println(msg);\n"
				+ "}\n"
				+ "\n"
				+ "printSummary(greeting, items);\n";
		JshWrapResult result = JshSnippetWrapper.wrap(content);
		String wrapped = result.getWrappedSource();

		// Imports (both the generated defaults and the hoisted user ones) must come before the
		// class declaration, and the method declaration must be a real class member, not nested
		// inside __run__ - the two placements v1 got wrong.
		int classIndex = wrapped.indexOf("public class " + JshSnippetWrapper.SCRATCHPAD_CLASS_NAME);
		assertTrue(wrapped.indexOf("import java.util.concurrent.atomic.AtomicInteger;") < classIndex,
				"Hoisted user import must appear before the class declaration");
		assertTrue(wrapped.indexOf("import java.util.List;") < classIndex,
				"Hoisted user import must appear before the class declaration");
		int methodIndex = wrapped.indexOf("void printSummary(");
		int runIndex = wrapped.indexOf(JshSnippetWrapper.SCRATCHPAD_METHOD_NAME + "()");
		assertTrue(methodIndex > classIndex && methodIndex < runIndex,
				"The user's method declaration must be a class member, between the class declaration and __run__, not nested inside __run__");
		assertTrue(wrapped.indexOf("printSummary(greeting, items);") > runIndex,
				"The trailing statement must be inside __run__");

		for (int originalOffset = 0; originalOffset <= content.length(); originalOffset++) {
			int syntheticOffset = result.toSyntheticOffset(originalOffset);
			assertEquals(originalOffset, result.toOriginalOffset(syntheticOffset),
					"Round trip failed for original offset " + originalOffset);
		}
	}

	/**
	 * Regression test for a reviewer finding on {@code 6c63e9b0}, later refined by splitting instead
	 * of fully demoting (see the class javadoc): a field that depends on a non-promotable {@code var}
	 * can't be promoted as a normal field-with-initializer (its initializer would run at construction
	 * time, before {@code n} is ever assigned), but it CAN be split - declared as a bare field (so a
	 * later method can still see {@code m}) with the assignment left in {@code __run__} in its
	 * original position (so {@code n} is already assigned by the time it runs).
	 */
	@Test
	public void testFieldDependingOnAVarIsSplitNotFullyDemoted() {
		String content = "var n = 2;\nint m = n;\n";
		JshWrapResult result = JshSnippetWrapper.wrap(content);
		String wrapped = result.getWrappedSource();

		int classIndex = wrapped.indexOf("public class " + JshSnippetWrapper.SCRATCHPAD_CLASS_NAME);
		int runIndex = wrapped.indexOf(JshSnippetWrapper.SCRATCHPAD_METHOD_NAME + "()");
		assertTrue(wrapped.indexOf("var n = 2;") > runIndex, "var n must stay inside __run__");
		int fieldIndex = wrapped.indexOf("int m;");
		assertTrue(fieldIndex > classIndex && fieldIndex < runIndex,
				"m must be declared as a bare field (no initializer) between the class declaration and __run__, so a later method could see it");
		assertTrue(wrapped.indexOf("m = n;") > runIndex,
				"m's assignment must stay inside __run__, in its original position, so n is already assigned when it runs");
	}

	/**
	 * The same dependency, two levels deep: {@code p} depends on {@code m}, which depends on the
	 * {@code var}. Both {@code m} and {@code p} must be split independently, in original order - a
	 * field's eligibility to split depends only on itself (not final, single fragment), not on
	 * whether what it references was itself split or fully demoted.
	 */
	@Test
	public void testTransitiveFieldDependencyChainIsSplit() {
		String content = "var n = 2;\nint m = n;\nint p = m;\n";
		JshWrapResult result = JshSnippetWrapper.wrap(content);
		String wrapped = result.getWrappedSource();

		int classIndex = wrapped.indexOf("public class " + JshSnippetWrapper.SCRATCHPAD_CLASS_NAME);
		int runIndex = wrapped.indexOf(JshSnippetWrapper.SCRATCHPAD_METHOD_NAME + "()");
		assertTrue(wrapped.indexOf("var n = 2;") > runIndex);
		int mFieldIndex = wrapped.indexOf("int m;");
		int pFieldIndex = wrapped.indexOf("int p;");
		assertTrue(mFieldIndex > classIndex && mFieldIndex < runIndex, "m must be declared as a bare field");
		assertTrue(pFieldIndex > classIndex && pFieldIndex < runIndex, "p must be declared as a bare field");
		int mAssignIndex = wrapped.indexOf("m = n;");
		int pAssignIndex = wrapped.indexOf("p = m;");
		assertTrue(mAssignIndex > runIndex, "m's assignment must be inside __run__");
		assertTrue(pAssignIndex > runIndex, "p's assignment must be inside __run__");
		assertTrue(mAssignIndex < pAssignIndex, "assignments must stay in original relative order, m before p");
	}

	/**
	 * A field that doesn't reference the var at all is unaffected and still gets promoted normally -
	 * the dependency check shouldn't over-apply and block unrelated declarations.
	 */
	@Test
	public void testUnrelatedFieldIsStillPromotedDespiteAnEarlierVar() {
		String content = "var n = 2;\nint unrelated = 5;\n";
		JshWrapResult result = JshSnippetWrapper.wrap(content);
		String wrapped = result.getWrappedSource();

		int runIndex = wrapped.indexOf(JshSnippetWrapper.SCRATCHPAD_METHOD_NAME + "()");
		assertTrue(wrapped.indexOf("var n = 2;") > runIndex);
		assertTrue(wrapped.indexOf("int unrelated = 5;") < runIndex, "unrelated doesn't reference n and should still be promoted to a field");
	}

	/**
	 * A blank final field can only be assigned inside a constructor, not an arbitrary method like
	 * __run__ - so a final field depending on a var can't be split, and must fall back to the older
	 * full-demotion behavior instead (a narrow, documented residual limitation: a later method still
	 * can't see it).
	 */
	@Test
	public void testFinalFieldDependingOnAVarFallsBackToFullDemotion() {
		String content = "var n = 2;\nfinal int m = n;\n";
		JshWrapResult result = JshSnippetWrapper.wrap(content);
		String wrapped = result.getWrappedSource();

		int runIndex = wrapped.indexOf(JshSnippetWrapper.SCRATCHPAD_METHOD_NAME + "()");
		assertTrue(wrapped.indexOf("var n = 2;") > runIndex);
		assertTrue(wrapped.indexOf("final int m = n;") > runIndex,
				"a final field depending on a var can't be split and must fall back to full demotion");
		assertEquals(-1, wrapped.indexOf("int m;"), "a final field must not be declared as a bare field");
	}

	/**
	 * A multi-fragment declaration (more than one declared name) can't be split either - there's no
	 * single name/initializer pair to split around - and must fall back to full demotion, same as
	 * before splitting existed.
	 */
	@Test
	public void testMultiFragmentFieldDependingOnAVarFallsBackToFullDemotion() {
		String content = "var n = 2;\nint a = n, b = 3;\n";
		JshWrapResult result = JshSnippetWrapper.wrap(content);
		String wrapped = result.getWrappedSource();

		int runIndex = wrapped.indexOf(JshSnippetWrapper.SCRATCHPAD_METHOD_NAME + "()");
		assertTrue(wrapped.indexOf("var n = 2;") > runIndex);
		assertTrue(wrapped.indexOf("int a = n, b = 3;") > runIndex,
				"a multi-fragment field depending on a var can't be split and must fall back to full demotion");
	}

	/**
	 * Splitting must preserve the "every original offset round-trips" guarantee the whole-chunk
	 * verbatim case already had - the separator between name and initializer, and the terminator
	 * after it, must stay covered by a Segment too, not just the name and the initializer themselves.
	 */
	@Test
	public void testSplitFieldOffsetsStillRoundTrip() {
		String content = "var n = 2;\nint m = n;\nSystem.out.println(m);\n";
		JshWrapResult result = JshSnippetWrapper.wrap(content);
		for (int originalOffset = 0; originalOffset <= content.length(); originalOffset++) {
			int syntheticOffset = result.toSyntheticOffset(originalOffset);
			assertEquals(originalOffset, result.toOriginalOffset(syntheticOffset),
					"Round trip failed for original offset " + originalOffset);
		}
	}

	/**
	 * Regression test: a C-style array declaration ("int values[]", legal Java, dimensions after the
	 * name rather than after the type) must keep its array type on the declaration side and must not
	 * leak a stray "[]" into the assignment statement - splitting used to stop nameEnd right after the
	 * identifier, before the "[]", corrupting both halves.
	 */
	@Test
	public void testSplitPreservesCStyleArrayDeclarationSyntax() {
		String content = "var n = 2;\nint values[] = new int[n];\n";
		JshWrapResult result = JshSnippetWrapper.wrap(content);
		String wrapped = result.getWrappedSource();

		int classIndex = wrapped.indexOf("public class " + JshSnippetWrapper.SCRATCHPAD_CLASS_NAME);
		int runIndex = wrapped.indexOf(JshSnippetWrapper.SCRATCHPAD_METHOD_NAME + "()");
		int fieldIndex = wrapped.indexOf("int values[];");
		assertTrue(fieldIndex > classIndex && fieldIndex < runIndex,
				"values must be declared as a bare array field, array type preserved, between the class declaration and __run__");
		assertTrue(wrapped.indexOf("values = new int[n];") > runIndex,
				"the assignment must be a plain assignment, not \"values[] = ...\"");
		assertEquals(-1, wrapped.indexOf("values[] ="), "the assignment must not contain a stray \"[]\" from the declaration's array dimensions");
	}

}
