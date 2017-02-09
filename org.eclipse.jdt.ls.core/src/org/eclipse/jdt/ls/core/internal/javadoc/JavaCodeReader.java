/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;


import java.io.IOException;

import org.eclipse.jdt.ls.core.internal.javadoc.html.SingleCharReader;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

/**
 * Reads from a document either forwards or backwards. May be configured to
 * skip comments and strings.
 */
public class JavaCodeReader extends SingleCharReader {

	/** The EOF character */
	public static final int EOF= -1;

	private boolean fSkipComments= false;
	private boolean fSkipStrings= false;
	private boolean fForward= false;

	private IDocument fDocument;
	private int fOffset;

	private int fEnd= -1;
	private int fCachedLineNumber= -1;
	private int fCachedLineOffset= -1;


	public JavaCodeReader() {
	}

	/**
	 * Returns the offset of the last read character. Should only be called after read has been called.
	 */
	public int getOffset() {
		return fForward ? fOffset -1 : fOffset;
	}

	public void configureForwardReader(IDocument document, int offset, int length, boolean skipComments, boolean skipStrings) throws IOException {
		fDocument= document;
		fOffset= offset;
		fSkipComments= skipComments;
		fSkipStrings= skipStrings;

		fForward= true;
		fEnd= Math.min(fDocument.getLength(), fOffset + length);
	}

	public void configureBackwardReader(IDocument document, int offset, boolean skipComments, boolean skipStrings) throws IOException {
		fDocument= document;
		fOffset= offset;
		fSkipComments= skipComments;
		fSkipStrings= skipStrings;

		fForward= false;
		try {
			fCachedLineNumber= fDocument.getLineOfOffset(fOffset);
		} catch (BadLocationException x) {
			throw new IOException(x.getMessage());
		}
	}

	/*
	 * @see Reader#close()
	 */
	@Override
	public void close() throws IOException {
		fDocument= null;
	}

	/*
	 * @see SingleCharReader#read()
	 */
	@Override
	public int read() throws IOException {
		try {
			return fForward ? readForwards() : readBackwards();
		} catch (BadLocationException x) {
			throw new IOException(x.getMessage());
		}
	}

	private void gotoCommentEnd() throws BadLocationException {
		while (fOffset < fEnd) {
			char current= fDocument.getChar(fOffset++);
			if (current == '*') {
				if (fOffset < fEnd && fDocument.getChar(fOffset) == '/') {
					++ fOffset;
					return;
				}
			}
		}
	}

	private void gotoStringEnd(char delimiter) throws BadLocationException {
		while (fOffset < fEnd) {
			char current= fDocument.getChar(fOffset++);
			if (current == '\\') {
				// ignore escaped characters
				++ fOffset;
			} else if (current == delimiter) {
				return;
			}
		}
	}

	private void gotoLineEnd() throws BadLocationException {
		int line= fDocument.getLineOfOffset(fOffset);
		fOffset= fDocument.getLineOffset(line + 1);
	}

	private int readForwards() throws BadLocationException {
		while (fOffset < fEnd) {
			char current= fDocument.getChar(fOffset++);

			switch (current) {
			case '/':

				if (fSkipComments && fOffset < fEnd) {
					char next= fDocument.getChar(fOffset);
					if (next == '*') {
						// a comment starts, advance to the comment end
						++ fOffset;
						gotoCommentEnd();
						continue;
					} else if (next == '/') {
						// '//'-comment starts, advance to the line end
						gotoLineEnd();
						continue;
					}
				}

				return current;

			case '"':
			case '\'':

				if (fSkipStrings) {
					gotoStringEnd(current);
					continue;
				}

				return current;
			}

			return current;
		}

		return EOF;
	}

	private void handleSingleLineComment() throws BadLocationException {
		int line= fDocument.getLineOfOffset(fOffset);
		if (line < fCachedLineNumber) {
			fCachedLineNumber= line;
			fCachedLineOffset= fDocument.getLineOffset(line);
			int offset= fOffset;
			while (fCachedLineOffset < offset) {
				char current= fDocument.getChar(offset--);
				if (current == '/' && fCachedLineOffset <= offset && fDocument.getChar(offset) == '/') {
					fOffset= offset;
					return;
				}
			}
		}
	}

	private void gotoCommentStart() throws BadLocationException {
		while (0 < fOffset) {
			char current= fDocument.getChar(fOffset--);
			if (current == '*' && 0 <= fOffset && fDocument.getChar(fOffset) == '/')
				return;
		}
	}

	private void gotoStringStart(char delimiter) throws BadLocationException {
		while (0 < fOffset) {
			char current= fDocument.getChar(fOffset);
			if (current == delimiter) {
				if ( !(0 <= fOffset && fDocument.getChar(fOffset -1) == '\\'))
					return;
			}
			-- fOffset;
		}
	}

	private int readBackwards() throws BadLocationException {

		while (0 < fOffset) {
			-- fOffset;

			handleSingleLineComment();

			char current= fDocument.getChar(fOffset);
			switch (current) {
			case '/':

				if (fSkipComments && fOffset > 1) {
					char next= fDocument.getChar(fOffset - 1);
					if (next == '*') {
						// a comment ends, advance to the comment start
						fOffset -= 2;
						gotoCommentStart();
						continue;
					}
				}

				return current;

			case '"':
			case '\'':

				if (fSkipStrings) {
					-- fOffset;
					gotoStringStart(current);
					continue;
				}

				return current;
			}

			return current;
		}

		return EOF;
	}
}

