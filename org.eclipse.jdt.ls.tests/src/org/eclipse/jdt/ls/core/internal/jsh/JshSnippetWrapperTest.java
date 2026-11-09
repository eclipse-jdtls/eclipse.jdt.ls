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
		String source = "int x = 1;\nSystem.out.println(x);";
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

}
