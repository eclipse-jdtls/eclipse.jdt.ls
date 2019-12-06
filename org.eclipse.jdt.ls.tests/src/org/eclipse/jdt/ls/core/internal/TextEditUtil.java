/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

/**
 * @author Fred Bricon
 *
 */
public class TextEditUtil {

	private TextEditUtil() {
		// no instanciation
	}

	public static String apply(ICompilationUnit cu, Collection<? extends TextEdit> edits)
			throws BadLocationException, JavaModelException {
		Assert.isNotNull(cu);
		Assert.isNotNull(edits);
		return apply(cu.getSource(), edits);
	}

	public static String apply(String text, Collection<? extends TextEdit> edits) throws BadLocationException {
		Assert.isNotNull(text);
		Assert.isNotNull(edits);
		return apply(new Document(text), edits);
	}

	public static String apply(Document doc, Collection<? extends TextEdit> edits) throws BadLocationException {
		Assert.isNotNull(doc);
		Assert.isNotNull(edits);
		List<TextEdit> sortedEdits = new ArrayList<>(edits);
		sortByLastEdit(sortedEdits);
		String text = doc.get();
		for (int i = sortedEdits.size() - 1; i >= 0; i--) {
			TextEdit te = sortedEdits.get(i);
			Range r = te.getRange();
			if (r != null && r.getStart() != null && r.getEnd() != null) {
				int start = getOffset(doc, r.getStart());
				int end = getOffset(doc, r.getEnd());
				text = text.substring(0, start)
						+ te.getNewText()
						+ text.substring(end, text.length());
			}
		}
		return text;
	}

	private static int getOffset(Document doc, Position pos) throws BadLocationException {
		return doc.getLineOffset(pos.getLine()) + pos.getCharacter();
	}

	public static void sortByLastEdit(List<TextEdit> edits) {
		Collections.sort(edits, new Comparator<TextEdit>() {

			@Override
			public int compare(TextEdit t1, TextEdit t2) {
				if (t1 == t2) {
					return 0;
				}
				if (t1 == null) {
					return 1;
				}
				if (t2 == null) {
					return -1;
				}
				return compare(t1.getRange(), t2.getRange());
			}

			public int compare(Range r1, Range r2) {
				if (r1 == r2) {
					return 0;
				}
				if (r1 == null) {
					return -1;
				}
				if (r2 == null) {
					return 1;
				}
				int res = compare(r1.getStart(), r2.getStart());
				if (res == 0) {
					res = compare(r1.getEnd(), r2.getEnd());
				}
				return res;
			}

			public int compare(Position p1, Position p2) {
				if (p1 == p2) {
					return 0;
				}
				if (p1 == null) {
					return -1;
				}
				if (p2 == null) {
					return 1;
				}
				int res = p1.getLine() - p2.getLine();
				if (res == 0) {
					res = p1.getCharacter() - p2.getCharacter();
				}
				return res;
			}
		});
	}
}
