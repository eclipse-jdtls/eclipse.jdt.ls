/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.FastJavaPartitionScanner
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.partition;


import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;

/**
 * This scanner recognizes the JavaDoc comments, Java multi line comments, Java single line comments,
 * Java strings and Java characters.
 */
public class FastJavaPartitionScanner implements IPartitionTokenScanner, IJavaPartitions {

	// states
	private static final int JAVA= 0;
	private static final int SINGLE_LINE_COMMENT= 1;
	private static final int MULTI_LINE_COMMENT= 2;
	private static final int JAVADOC= 3;
	private static final int CHARACTER= 4;
	private static final int STRING= 5;
	private static final int MULTI_LINE_STRING= 6;

	// beginning of prefixes and postfixes
	private static final int NONE= 0;
	private static final int BACKSLASH= 1; // postfix for STRING and CHARACTER
	private static final int SLASH= 2; // prefix for SINGLE_LINE or MULTI_LINE or JAVADOC
	private static final int SLASH_STAR= 3; // prefix for MULTI_LINE_COMMENT or JAVADOC
	private static final int SLASH_STAR_STAR= 4; // prefix for MULTI_LINE_COMMENT or JAVADOC
	private static final int STAR= 5; // postfix for MULTI_LINE_COMMENT or JAVADOC
	private static final int CARRIAGE_RETURN=6; // postfix for STRING, CHARACTER and SINGLE_LINE_COMMENT
	private static final int TRIPLE_QUOTE= 9; // prefix for TextBlock.

	/** The scanner. */
	private final BufferedDocumentScanner fScanner= new BufferedDocumentScanner(1000);	// faster implementation

	private IDocument fCurrentDocument;

	/** The offset of the last returned token. */
	private int fTokenOffset;
	/** The length of the last returned token. */
	private int fTokenLength;

	/** The state of the scanner. */
	private int fState;
	/** The last significant characters read. */
	private int fLast;
	/** The amount of characters already read on first call to nextToken(). */
	private int fPrefixLength;

	// emulate JavaPartitionScanner
	private boolean fEmulate= false;
	private int fJavaOffset;
	private int fJavaLength;

	private IJavaProject fJavaProject;

	private final IToken[] fTokens= new IToken[] {
		new Token(null),
		new Token(JAVA_SINGLE_LINE_COMMENT),
		new Token(JAVA_MULTI_LINE_COMMENT),
		new Token(JAVA_DOC),
		new Token(JAVA_CHARACTER),
		new Token(JAVA_STRING),
		new Token(JAVA_MULTI_LINE_STRING)
	};

	public FastJavaPartitionScanner(boolean emulate) {
	    fEmulate= emulate;
	}

	public FastJavaPartitionScanner() {
	    this(false);
	}

	public FastJavaPartitionScanner(IJavaProject javaProject) {
		this(false);
		fJavaProject= javaProject;
	}

	/*
	 * @see org.eclipse.jface.text.rules.ITokenScanner#nextToken()
	 */
	@Override
	public IToken nextToken() {

		// emulate JavaPartitionScanner
		if (fEmulate) {
			if (fJavaOffset != -1 && fTokenOffset + fTokenLength != fJavaOffset + fJavaLength) {
				fTokenOffset += fTokenLength;
				return fTokens[JAVA];
			} else {
				fJavaOffset= -1;
				fJavaLength= 0;
			}
		}

		fTokenOffset += fTokenLength;
		fTokenLength= fPrefixLength;

		while (true) {
			final int ch= fScanner.read();

			// characters
	 		switch (ch) {
	 		case ICharacterScanner.EOF:
		 		if (fTokenLength > 0) {
		 			fLast= NONE; // ignore last
		 			return preFix(fState, JAVA, NONE, 0);

		 		} else {
		 			fLast= NONE;
		 			fPrefixLength= 0;
					return Token.EOF;
		 		}

	 		case '\r':
	 			// emulate JavaPartitionScanner
	 			if (!fEmulate && fLast != CARRIAGE_RETURN) {
						fLast= CARRIAGE_RETURN;
						fTokenLength++;
	 					continue;

	 			} else {

					switch (fState) {
					case SINGLE_LINE_COMMENT:
					case CHARACTER:
					case STRING:
						if (fTokenLength > 0) {
							IToken token= fTokens[fState];

				 			// emulate JavaPartitionScanner
							if (fEmulate) {
								fTokenLength++;
								fLast= NONE;
								fPrefixLength= 0;
							} else {
								fLast= CARRIAGE_RETURN;
								fPrefixLength= 1;
							}

							fState= JAVA;
							return token;

						} else {
							consume();
							continue;
						}

					default:
						consume();
						continue;
					}
	 			}

	 		case '\n':
				switch (fState) {
				case SINGLE_LINE_COMMENT:
				case CHARACTER:
				case STRING:
					// assert(fTokenLength > 0);
					return postFix(fState);

				default:
					consume();
					continue;
				}

			default:
				if (!fEmulate && fLast == CARRIAGE_RETURN) {
					switch (fState) {
					case SINGLE_LINE_COMMENT:
					case CHARACTER:
					case STRING:

						int last;
						int newState;
						switch (ch) {
						case '/':
							last= SLASH;
							newState= JAVA;
							break;

						case '*':
							last= STAR;
							newState= JAVA;
							break;

						case '\'':
							last= NONE;
							newState= CHARACTER;
							break;

						case '"':
							last= NONE;
							newState= STRING;
							break;

						case '\r':
							last= CARRIAGE_RETURN;
							newState= JAVA;
							break;

						case '\\':
							last= BACKSLASH;
							newState= JAVA;
							break;

						default:
							last= NONE;
							newState= JAVA;
							break;
						}

						fLast= NONE; // ignore fLast
						return preFix(fState, newState, last, 1);

					default:
						break;
					}
				}
			}

			// states
	 		switch (fState) {
	 		case JAVA:
				switch (ch) {
				case '/':
					if (fLast == SLASH) {
						if (fTokenLength - getLastLength(fLast) > 0) {
							return preFix(JAVA, SINGLE_LINE_COMMENT, NONE, 2);
						} else {
							preFix(JAVA, SINGLE_LINE_COMMENT, NONE, 2);
							fTokenOffset += fTokenLength;
							fTokenLength= fPrefixLength;
							break;
						}

					} else {
						fTokenLength++;
						fLast= SLASH;
						break;
					}

				case '*':
					if (fLast == SLASH) {
						if (fTokenLength - getLastLength(fLast) > 0) {
							return preFix(JAVA, MULTI_LINE_COMMENT, SLASH_STAR, 2);
						} else {
							preFix(JAVA, MULTI_LINE_COMMENT, SLASH_STAR, 2);
							fTokenOffset += fTokenLength;
							fTokenLength= fPrefixLength;
							break;
						}

					} else {
						consume();
						break;
					}

				case '\'':
					fLast= NONE; // ignore fLast
					if (fTokenLength > 0) {
						return preFix(JAVA, CHARACTER, NONE, 1);
					} else {
						preFix(JAVA, CHARACTER, NONE, 1);
						fTokenOffset += fTokenLength;
						fTokenLength= fPrefixLength;
						break;
					}

				case '"':
					boolean isTextBlockBeginning= scanForTextBlockBeginning();
					if (isTextBlockBeginning) {
						if (fTokenLength - getLastLength(fLast) > 0) {
							return preFix(JAVA, MULTI_LINE_STRING, TRIPLE_QUOTE, 3);
						} else {
							preFix(JAVA, MULTI_LINE_STRING, TRIPLE_QUOTE, 3);
							fTokenOffset+= fTokenLength;
							fTokenLength= fPrefixLength;
							break;
						}
					}
					else {
						fLast= NONE; // ignore fLast
						if (fTokenLength > 0) {
							return preFix(JAVA, STRING, NONE, 1);
						} else {
							preFix(JAVA, STRING, NONE, 1);
							fTokenOffset += fTokenLength;
							fTokenLength= fPrefixLength;
							break;
						}
					}

				default:
					consume();
					break;
				}
				break;

	 		case SINGLE_LINE_COMMENT:
				consume();
				break;

	 		case JAVADOC:
				switch (ch) {
				case '/':
					switch (fLast) {
					case SLASH_STAR_STAR:
						return postFix(MULTI_LINE_COMMENT);

					case STAR:
						return postFix(JAVADOC);

					default:
						consume();
						break;
					}
					break;

				case '*':
					fTokenLength++;
					fLast= STAR;
					break;

				default:
					consume();
					break;
				}
				break;

	 		case MULTI_LINE_COMMENT:
				switch (ch) {
				case '*':
					if (fLast == SLASH_STAR) {
						fLast= SLASH_STAR_STAR;
						fTokenLength++;
						fState= JAVADOC;
					} else {
						fTokenLength++;
						fLast= STAR;
					}
					break;

				case '/':
					if (fLast == STAR) {
						return postFix(MULTI_LINE_COMMENT);
					} else {
						consume();
						break;
					}

				default:
					consume();
					break;
				}
				break;

	 		case STRING:
	 			switch (ch) {
	 			case '\\':
					fLast= (fLast == BACKSLASH) ? NONE : BACKSLASH;
					fTokenLength++;
					if (scanForUnicodeSlash()) {
						fTokenLength= fTokenLength + 5;
					}
					break;

				case '\"':
	 				if (fLast != BACKSLASH) {
	 					return postFix(STRING);

		 			} else {
						consume();
						break;
	 				}

		 		default:
					consume();
	 				break;
	 			}
	 			break;

	 		case CHARACTER:
	 			switch (ch) {
				case '\\':
					fLast= (fLast == BACKSLASH) ? NONE : BACKSLASH;
					fTokenLength++;
					if (scanForUnicodeSlash()) {
						fTokenLength= fTokenLength + 5;
					}
					break;

	 			case '\'':
	 				if (fLast != BACKSLASH) {
	 					return postFix(CHARACTER);

	 				} else {
						consume();
		 				break;
	 				}

	 			default:
					consume();
	 				break;
	 			}
	 			break;

	 		case MULTI_LINE_STRING:
				switch (ch) {
				case '\\':
						fLast= (fLast == BACKSLASH) ? NONE : BACKSLASH;
						fTokenLength++;
						if (scanForUnicodeSlash()) {
							fTokenLength= fTokenLength + 5;
						}
						break;
				case '\"':
					if (fLast == BACKSLASH || !scanForTextBlockClose()) {
	 					consume();
	 					break;
		 			} else {
		 				boolean considerEndQuotes= true;
		 				try {
		 					IDocumentPartitioner docPartitioner= fCurrentDocument.getDocumentPartitioner();
		 					if (fCurrentDocument instanceof IDocumentExtension3) {
		 						docPartitioner= ((IDocumentExtension3)fCurrentDocument).getDocumentPartitioner(IJavaPartitions.JAVA_PARTITIONING);
		 					}
		 					if (docPartitioner instanceof FastJavaPartitioner) {
		 						FastJavaPartitioner fjPartitioner= (FastJavaPartitioner) docPartitioner;
		 						if (!fjPartitioner.hasTextBlockSupportedValueChanged()) {
				 					ITypedRegion originalPartition= TextUtilities.getPartition(fCurrentDocument, IJavaPartitions.JAVA_PARTITIONING, fTokenOffset, false);
									ITypedRegion startingPartition= TextUtilities.getPartition(fCurrentDocument, IJavaPartitions.JAVA_PARTITIONING, fTokenOffset+ fTokenLength+2, false);
									fjPartitioner.resetPositionCache();
									if (!originalPartition.equals(startingPartition)) {
										String startingType= startingPartition.getType();
										if (IJavaPartitions.JAVA_MULTI_LINE_STRING.equals(startingType)) {
											considerEndQuotes= false;
										}
									}
		 						}
		 					}
		 					if (!considerEndQuotes) {
		 						for (int i=0; i< 3; i++) {
									fScanner.unread();
								}
		 					}
						} catch (BadLocationException e) {
							//do nothing
						}
		 				if (considerEndQuotes) {
		 					fTokenLength= fTokenLength + 2;
		 				} else {
		 					fTokenLength= fTokenLength - 1;
		 				}
						return postFix(MULTI_LINE_STRING);
					}
				default:
					consume();
					break;
				}
	 			break;
	 		}
		}
 	}

	private boolean scanForUnicodeSlash() {
		int count= 0;
		boolean isUnicodeSlash= false;
		try {
			int ch= fScanner.read();
			count++;
			if (ch == 'u') {
				ch= fScanner.read();
				count++;
				if (ch == '0') {
					ch= fScanner.read();
					count++;
					if (ch == '0') {
						ch= fScanner.read();
						count++;
						if (ch == '5') {
							ch= fScanner.read();
							count++;
							if (ch == 'C') {
								isUnicodeSlash= true;
							}
						}
					}
				}
			}
			if (ch == ICharacterScanner.EOF) {
				count--;
			}
		} catch (IndexOutOfBoundsException e) {
			//let it return false;
		} finally {
			if (!isUnicodeSlash) {
				for (int i= count; i > 0; i--) {
					fScanner.unread();
				}
			}
		}
		return isUnicodeSlash;
	}

	private boolean scanForTextBlockBeginning() {
		if (!isTextBlockSupported()) {
			return false;
		}
		int count= 0;
		boolean isTextBlockBeginning= false;
		try {
			// Don't change the position and current character unless we are certain
			// to be dealing with a text block. For producing all errors like before
			// in case of a valid """ but missing \r or \n, just return false and not
			// throw any error.
			count++;
			int ch= fScanner.read();
			if (ch == '\"') {
				count++;
				ch= fScanner.read();
				if (ch == '\"') {
					count++;
					ch= fScanner.read();
					while (Character.isWhitespace(ch)) {
						switch (ch) {
							case 10: /* \ u000a: LINE FEED               */
							case 13: /* \ u000d: CARRIAGE RETURN         */
								isTextBlockBeginning= true;
								break;
							case ICharacterScanner.EOF:
								count--;
								break;
							default:
								count++;
								ch= fScanner.read();
								break;
						}
						if (isTextBlockBeginning) {
							break;
						}
					}
				} else if (ch == ICharacterScanner.EOF) {
					count--;
				}
			} else if (ch == ICharacterScanner.EOF) {
				count--;
			}
		} catch (IndexOutOfBoundsException e) {
			//let it return false;
		} finally {
			int ignore= 0;
			if (isTextBlockBeginning) {
				ignore= 2;
			}
			for (int i= count - ignore; i > 0; i--) {
				fScanner.unread();
			}
		}
		return isTextBlockBeginning;
	}

	private boolean scanForTextBlockClose() {
		int count= 0;
		boolean isTextBlockEnd= false;
		try {
			count++;
			int ch= fScanner.read();
			if (ch == '\"') {
				count++;
				ch= fScanner.read();
				if (ch == '\"') {
					isTextBlockEnd= true;
				} else if (ch == ICharacterScanner.EOF) {
					count--;
				}

			} else if (ch == ICharacterScanner.EOF) {
				count--;
			}
		} catch (IndexOutOfBoundsException e) {
			//let it return false;
		} finally {
			if (!isTextBlockEnd) {
				for (int i= count; i > 0; i--) {
					fScanner.unread();
				}
			}

		}
		return isTextBlockEnd;
	}

	private static final int getLastLength(int last) {
		switch (last) {
		default:
			return -1;

		case NONE:
			return 0;

		case CARRIAGE_RETURN:
		case BACKSLASH:
		case SLASH:
		case STAR:
			return 1;

		case SLASH_STAR:
			return 2;

		case SLASH_STAR_STAR:
		case TRIPLE_QUOTE:
			return 3;
		}
	}

	private final void consume() {
		fTokenLength++;
		fLast= NONE;
	}

	private final IToken postFix(int state) {
		fTokenLength++;
		fLast= NONE;
		fState= JAVA;
		fPrefixLength= 0;
		return fTokens[state];
	}

	private final IToken preFix(int state, int newState, int last, int prefixLength) {
		// emulate JavaPartitionScanner
		if (fEmulate && state == JAVA && (fTokenLength - getLastLength(fLast) > 0)) {
			fTokenLength -= getLastLength(fLast);
			fJavaOffset= fTokenOffset;
			fJavaLength= fTokenLength;
			fTokenLength= 1;
			fState= newState;
			fPrefixLength= prefixLength;
			fLast= last;
			return fTokens[state];

		} else {
			fTokenLength -= getLastLength(fLast);
			fLast= last;
			fPrefixLength= prefixLength;
			IToken token= fTokens[state];
			fState= newState;
			return token;
		}
	}

	private static int getState(String contentType) {

		if (contentType == null) {
			return JAVA;
		}

		switch (contentType) {
		case JAVA_SINGLE_LINE_COMMENT:
			return SINGLE_LINE_COMMENT;
		case JAVA_MULTI_LINE_COMMENT:
			return MULTI_LINE_COMMENT;
		case JAVA_DOC:
			return JAVADOC;
		case JAVA_STRING:
			return STRING;
		case JAVA_CHARACTER:
			return CHARACTER;
		case JAVA_MULTI_LINE_STRING:
			return MULTI_LINE_STRING;
		default:
			return JAVA;
		}
	}

	/*
	 * @see IPartitionTokenScanner#setPartialRange(IDocument, int, int, String, int)
	 */
	@Override
	public void setPartialRange(IDocument document, int offset, int length, String contentType, int partitionOffset) {
		fCurrentDocument= document;
		fScanner.setRange(document, offset, length);
		fTokenOffset= partitionOffset;
		fTokenLength= 0;
		fPrefixLength= offset - partitionOffset;
		fLast= NONE;

		if (offset == partitionOffset) {
			// restart at beginning of partition
			fState= JAVA;
		} else {
			fState= getState(contentType);
		}

		// emulate JavaPartitionScanner
		if (fEmulate) {
			fJavaOffset= -1;
			fJavaLength= 0;
		}
	}

	/*
	 * @see ITokenScanner#setRange(IDocument, int, int)
	 */
	@Override
	public void setRange(IDocument document, int offset, int length) {
		fCurrentDocument= document;
		fScanner.setRange(document, offset, length);
		fTokenOffset= offset;
		fTokenLength= 0;
		fPrefixLength= 0;
		fLast= NONE;
		fState= JAVA;

		// emulate JavaPartitionScanner
		if (fEmulate) {
			fJavaOffset= -1;
			fJavaLength= 0;
		}
	}

	/*
	 * @see ITokenScanner#getTokenLength()
	 */
	@Override
	public int getTokenLength() {
		return fTokenLength;
	}

	/*
	 * @see ITokenScanner#getTokenOffset()
	 */
	@Override
	public int getTokenOffset() {
		return fTokenOffset;
	}

	private void setJavaProject() {
		if (fJavaProject == null) {
			// FIXME
		}
	}

	public boolean isTextBlockSupported() {
		boolean isAllowed= false;
		setJavaProject();
		if (fJavaProject != null) {
			isAllowed= JavaModelUtil.is15OrHigher(fJavaProject);
		}
		return isAllowed;
	}
}
