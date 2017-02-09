/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc.html;

import java.io.IOException;
import java.io.Reader;

/**
 * Copied into this package from <code>org.eclipse.jface.internal.text.html.SubstitutionTextReader</code>.
 */
/**
 * Reads the text contents from a reader and computes for each character
 * a potential substitution. The substitution may eat more characters than
 * only the one passed into the computation routine.
 * <p>
 * Moved into this package from <code>org.eclipse.jface.internal.text.revisions</code>.</p>
 */
public abstract class SubstitutionTextReader extends SingleCharReader {

	protected static final String LINE_DELIM= System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$

	private Reader fReader;
	protected boolean fWasWhiteSpace;
	private int fCharAfterWhiteSpace;

	/**
	 * Tells whether white space characters are skipped.
	 */
	private boolean fSkipWhiteSpace= true;

	private boolean fReadFromBuffer;

	private StringBuilder fBuilder;

	private int fIndex;


	protected SubstitutionTextReader(Reader reader) {
		fReader= reader;
		fBuilder= new StringBuilder();
		fIndex= 0;
		fReadFromBuffer= false;
		fCharAfterWhiteSpace= -1;
		fWasWhiteSpace= true;
	}

	/**
	 * Computes the substitution for the given character and if necessary
	 * subsequent characters. Implementation should use <code>nextChar</code>
	 * to read subsequent characters.
	 *
	 * @param c the character to be substituted
	 * @return the substitution for <code>c</code>
	 * @throws IOException in case computing the substitution fails
	 */
	protected abstract String computeSubstitution(int c) throws IOException;

	/**
	 * Returns the internal reader.
	 *
	 * @return the internal reader
	 */
	protected Reader getReader() {
		return fReader;
	}

	/**
	 * Returns the next character.
	 * @return the next character
	 * @throws IOException in case reading the character fails
	 */
	protected int nextChar() throws IOException {
		fReadFromBuffer= (fBuilder.length() > 0);
		if (fReadFromBuffer) {
			char ch= fBuilder.charAt(fIndex++);
			if (fIndex >= fBuilder.length()) {
				fBuilder.setLength(0);
				fIndex= 0;
			}
			return ch;
		}

		int ch= fCharAfterWhiteSpace;
		if (ch == -1) {
			ch= fReader.read();
		}
		if (fSkipWhiteSpace && Character.isWhitespace((char)ch)) {
			do {
				ch= fReader.read();
			} while (Character.isWhitespace((char)ch));
			if (ch != -1) {
				fCharAfterWhiteSpace= ch;
				return ' ';
			}
		} else {
			fCharAfterWhiteSpace= -1;
		}
		return ch;
	}

	/**
	 * @see Reader#read()
	 */
	@Override
	public int read() throws IOException {
		int c;
		do {

			c= nextChar();
			while (!fReadFromBuffer && c != -1) {
				String s= computeSubstitution(c);
				if (s == null)
					break;
				if (s.length() > 0)
					fBuilder.insert(0, s);
				c= nextChar();
			}

		} while (fSkipWhiteSpace && fWasWhiteSpace && (c == ' '));
		fWasWhiteSpace= (c == ' ' || c == '\r' || c == '\n');
		return c;
	}

	/**
	 * @see Reader#ready()
	 */
	@Override
	public boolean ready() throws IOException {
		return fReader.ready();
	}

	/**
	 * @see Reader#close()
	 */
	@Override
	public void close() throws IOException {
		fReader.close();
	}

	/**
	 * @see Reader#reset()
	 */
	@Override
	public void reset() throws IOException {
		fReader.reset();
		fWasWhiteSpace= true;
		fCharAfterWhiteSpace= -1;
		fBuilder.setLength(0);
		fIndex= 0;
	}

	protected final void setSkipWhitespace(boolean state) {
		fSkipWhiteSpace= state;
	}

	protected final boolean isSkippingWhitespace() {
		return fSkipWhiteSpace;
	}
}
