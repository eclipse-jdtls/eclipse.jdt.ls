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
package org.eclipse.jdt.ls.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

/**
 * A set of assertion methods about LSP4J entities
 *
 * @author Fred Bricon
 *
 */
public final class Lsp4jAssertions {

	private Lsp4jAssertions() {
		//no instantiation
	}

	public static void assertRange(int expectedLine, int expectedStart, int expectedEnd, Range range) {
		assertNotNull(range, "Range is null");
		assertPosition(expectedLine, expectedStart, range.getStart());
		assertPosition(expectedLine, expectedEnd, range.getEnd());
	}

	public static void assertPosition(int expectedLine, int expectedChar, Position position) {
		assertNotNull(position, "Position is null");
		assertEquals(expectedLine, position.getLine(), "Unexpected line position from "+position);
		assertEquals(expectedChar, position.getCharacter(), "Unexpected character position from "+position);
	}

	public static void assertTextEdit(int expectedLine, int expectedStart, int expectedEnd, String expectedText, TextEdit edit){
		assertNotNull(edit, "TextEdit is null");
		assertEquals(expectedText, edit.getNewText());
		assertRange(expectedLine, expectedStart, expectedEnd, edit.getRange());

	}

}
