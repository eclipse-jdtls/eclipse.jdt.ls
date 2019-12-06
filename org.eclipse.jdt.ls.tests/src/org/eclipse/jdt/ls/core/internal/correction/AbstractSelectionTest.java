/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.correction;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.lsp4j.Range;

public class AbstractSelectionTest extends AbstractQuickFixTest {

	public static final String SQUARE_BRACKET_OPEN = "/*[*/";
	public static final int SQUARE_BRACKET_OPEN_LENGTH = SQUARE_BRACKET_OPEN.length();
	public static final String SQUARE_BRACKET_CLOSE = "/*]*/";
	public static final int SQUARE_BRACKET_CLOSE_LENGTH = SQUARE_BRACKET_CLOSE.length();

	protected static final int VALID_SELECTION = 1;
	protected static final int INVALID_SELECTION = 2;
	protected static final int COMPARE_WITH_OUTPUT = 3;

	protected int[] getSelection(String source) {
		int start = -1;
		int end = -1;
		int includingStart = source.indexOf(SQUARE_BRACKET_OPEN);
		int excludingStart = source.indexOf(SQUARE_BRACKET_CLOSE);
		int includingEnd = source.lastIndexOf(SQUARE_BRACKET_CLOSE);
		int excludingEnd = source.lastIndexOf(SQUARE_BRACKET_OPEN);

		if (includingStart > excludingStart && excludingStart != -1) {
			includingStart = -1;
		} else if (excludingStart > includingStart && includingStart != -1) {
			excludingStart = -1;
		}

		if (includingEnd < excludingEnd) {
			includingEnd = -1;
		} else if (excludingEnd < includingEnd) {
			excludingEnd = -1;
		}

		if (includingStart != -1) {
			start = includingStart;
		} else {
			start = excludingStart + SQUARE_BRACKET_CLOSE_LENGTH;
		}

		if (excludingEnd != -1) {
			end = excludingEnd;
		} else {
			end = includingEnd + SQUARE_BRACKET_CLOSE_LENGTH;
		}

		return new int[] { start, end - start };
	}

	@Override
	protected Range getRange(ICompilationUnit cu, IProblem[] problems) throws JavaModelException {
		int[] ranges = getSelection(cu.getSource());
		return JDTUtils.toRange(cu, ranges[0], ranges[1]);
	}
}
