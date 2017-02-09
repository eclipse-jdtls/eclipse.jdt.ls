/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.formatter.IndentManipulation;
import org.eclipse.jdt.ls.core.internal.javadoc.html.SingleCharReader;


/**
 * Reads a java doc comment from a java doc comment. Skips star-character on begin of line.
 */
public class JavaDocCommentReader extends SingleCharReader {

	private IBuffer fBuffer;

	private String fSource;

	private int fCurrPos;

	private int fStartPos;

	private int fEndPos;

	private boolean fWasNewLine;

	public JavaDocCommentReader(IBuffer buf, int start, int end) {
		fBuffer= buf;
		fStartPos= start + 3;
		fEndPos= end - 2;

		reset();
	}

	public JavaDocCommentReader(String source, int start, int end) {
		fSource= source;
		fStartPos= start + 3;
		fEndPos= end - 2;

		reset();
	}

	/**
	 * @see java.io.Reader#read()
	 */
	@Override
	public int read() {
		if (fCurrPos < fEndPos) {
			char ch= getChar(fCurrPos++);
			if (fWasNewLine && !IndentManipulation.isLineDelimiterChar(ch)) {
				while (fCurrPos < fEndPos && Character.isWhitespace(ch)) {
					ch= getChar(fCurrPos++);
				}
				if (ch == '*') {
					if (fCurrPos < fEndPos) {
						do {
							ch= getChar(fCurrPos++);
						} while (ch == '*');
					} else {
						return -1;
					}
				}
			}
			fWasNewLine= IndentManipulation.isLineDelimiterChar(ch);

			return ch;
		}
		return -1;
	}

	/**
	 * @see java.io.Reader#close()
	 */
	@Override
	public void close() {
		fSource = null;
		fBuffer= null;
	}

	/**
	 * @see java.io.Reader#reset()
	 */
	@Override
	public void reset() {
		fCurrPos= fStartPos;
		fWasNewLine= true;
		// skip first line delimiter:
		if (fCurrPos < fEndPos && '\r' == getChar(fCurrPos)) {
			fCurrPos++;
		}
		if (fCurrPos < fEndPos && '\n' == getChar(fCurrPos)) {
			fCurrPos++;
		}
	}

	private char getChar(int pos) {
		if (fBuffer != null) {
			return fBuffer.getChar(fCurrPos);
		}
		return fSource.charAt(pos);
	}

	/**
	 * Returns the offset of the last read character in the passed buffer.
	 *
	 * @return the offset
	 */
	public int getOffset() {
		return fCurrPos;
	}
}
