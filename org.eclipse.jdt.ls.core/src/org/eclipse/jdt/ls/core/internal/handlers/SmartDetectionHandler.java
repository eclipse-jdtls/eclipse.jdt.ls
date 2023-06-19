/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Based on org.eclipse.jdt.internal.ui.text.java.SmartSemicolonAutoEditStrategy
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTextElement;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.lsp4j.Location;

public class SmartDetectionHandler {
	/**
	 * The identifier of the Java partitioning.
	 */
	private static final String JAVA_PARTITIONING = "___java_partitioning"; //$NON-NLS-1$

	/**
	 * The identifier of the single-line (JLS2: EndOfLineComment) end comment
	 * partition content type.
	 */
	private static final String JAVA_SINGLE_LINE_COMMENT = "__java_singleline_comment"; //$NON-NLS-1$

	/**
	 * The identifier multi-line (JLS2: TraditionalComment) comment partition
	 * content type.
	 */
	private static final String JAVA_MULTI_LINE_COMMENT = "__java_multiline_comment"; //$NON-NLS-1$

	/**
	 * The identifier of the Javadoc (JLS2: DocumentationComment) partition content
	 * type.
	 */
	private static final String JAVA_DOC = "__java_javadoc"; //$NON-NLS-1$

	/**
	 * The identifier of the Java string partition content type.
	 */
	private static final String JAVA_STRING = "__java_string"; //$NON-NLS-1$

	/**
	 * The identifier of the Java character partition content type.
	 */
	private static final String JAVA_CHARACTER = "__java_character"; //$NON-NLS-1$

	private SmartDetectionParams params;

	/**
	 * @param params
	 */
	public SmartDetectionHandler(SmartDetectionParams params) {
		this.params = params;
	}

	public Object getLocation(IProgressMonitor monitor) {
		if (params == null || params.getUri() == null || params.getPosition() == null) {
			return null;
		}
		// 1: find concerned line / position in java code, location in statement
		try {
			String uri = params.getUri();
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
			IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
			int offset = JsonRpcHelpers.toOffset(document, params.getPosition().getLine(), params.getPosition().getCharacter());
			int pos = offset;
			char fCharacter = ';';
			IRegion line = document.getLineInformationOfOffset(pos);
			ASTNode node = JDTUtils.getHoveredASTNode(unit, new Region(offset, 1));
			if (node == null || node instanceof Comment || node instanceof StringLiteral || node instanceof TextBlock || node instanceof AbstractTextElement) {
				return null;
			}
			if (node instanceof Block) {
				// FIXME
			}
			// 2: choose action based on findings (is for-Statement?)
			// for now: compute the best position to insert the new character
			int positionInLine = computeCharacterPosition(document, line, pos - line.getOffset(), fCharacter);
			int position = positionInLine + line.getOffset();
			// never position before the current position!
			if (position <= pos) {
				return null;
			}
			// never double already existing content
			if (alreadyPresent(document, fCharacter, position)) {
				return null;
			}
			node = JDTUtils.getHoveredASTNode(unit, new Region(position, 1));
			if (node instanceof Comment || node instanceof StringLiteral || node instanceof TextBlock) {
				return null;
			}
			if (node instanceof MethodInvocation) {
				int endOffset = node.getStartPosition() + node.getLength();
				if (endOffset > position) {
					return null;
				}
			}
			Location location = JDTUtils.toLocation(unit, position, 1);
			return new SmartDetectionParams(params.getUri(), location.getRange().getStart());
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e);
		}
		return null;
	}

	/**
	 * Computes the next insert position of the given character in the current line.
	 *
	 * @param document
	 *            the document we are working on
	 * @param line
	 *            the line where the change is being made
	 * @param offset
	 *            the position of the caret in the line when <code>character</code>
	 *            was typed
	 * @param character
	 *            the character to look for
	 * @param partitioning
	 *            the document partitioning
	 * @return the position where <code>character</code> should be inserted /
	 *         replaced
	 */
	private int computeCharacterPosition(IDocument document, IRegion line, int offset, char character) throws BadLocationException {
		String text = document.get(line.getOffset(), line.getLength());
		if (text == null) {
			return 0;
		}
		String partitioning = JAVA_PARTITIONING;
		if (!isDefaultPartition(document, offset + line.getOffset(), partitioning)) {
			return -1;
		}
		int insertPos;
		if (isForStatement(text, offset)) {
			insertPos = -1; // don't do anything in for statements, as semis are vital part of these
		} else {
			int nextPartitionPos = nextPartitionOrLineEnd(document, line, offset, partitioning);
			insertPos = startOfWhitespaceBeforeOffset(text, nextPartitionPos);
			// if there is a semi present, return its location as alreadyPresent() will take it out this way.
			if (insertPos > 0 && text.charAt(insertPos - 1) == character) {
				insertPos = insertPos - 1;
			} else if (insertPos > 0 && text.charAt(insertPos - 1) == '}') {
				int opening = scanBackward(document, insertPos - 1 + line.getOffset(), partitioning, -1, new char[] { '{' });
				if (opening > -1 && opening < offset + line.getOffset()) {
					if (computeArrayInitializationPos(document, line, opening - line.getOffset(), partitioning) == -1) {
						insertPos = offset;
					}
				}
			} else if (insertPos > 0 && text.charAt(insertPos - 1) == '=') {
				return -1;
			}
		}
		return insertPos;
	}

	/**
	 * Computes an insert position for an opening brace if <code>offset</code> maps
	 * to a position in <code>document</code> that looks like being the RHS of an
	 * assignment or like an array definition.
	 *
	 * @param document
	 *            the document being modified
	 * @param line
	 *            the current line under investigation
	 * @param offset
	 *            the offset of the caret position, relative to the line start.
	 * @param partitioning
	 *            the document partitioning
	 * @return an insert position relative to the line start if <code>line</code>
	 *         looks like being an array initialization at <code>offset</code>, -1
	 *         otherwise
	 */
	private int computeArrayInitializationPos(IDocument document, IRegion line, int offset, String partitioning) {
		// search backward while WS, find = (not != <= >= ==) in default partition
		int pos = offset + line.getOffset();
		if (pos == 0) {
			return -1;
		}
		int p = firstNonWhitespaceBackward(document, pos - 1, partitioning, -1);
		if (p == -1) {
			return -1;
		}
		try {
			char ch = document.getChar(p);
			if (ch != '=' && ch != ']') {
				return -1;
			}
			if (p == 0) {
				return offset;
			}
			p = firstNonWhitespaceBackward(document, p - 1, partitioning, -1);
			if (p == -1) {
				return -1;
			}
			ch = document.getChar(p);
			if (Character.isJavaIdentifierPart(ch) || ch == ']' || ch == '[') {
				return offset;
			}
		} catch (BadLocationException e) {
		}
		return -1;
	}

	/**
	 * Finds the highest position in <code>document</code> such that the position is
	 * &lt;= <code>position</code> and &gt; <code>bound</code> and
	 * <code>Character.isWhitespace(document.getChar(pos))</code> evaluates to
	 * <code>false</code> and the position is in the default partition.
	 *
	 * @param document
	 *            the document being modified
	 * @param position
	 *            the first character position in <code>document</code> to be
	 *            considered
	 * @param partitioning
	 *            the document partitioning
	 * @param bound
	 *            the first position in <code>document</code> to not consider any
	 *            more, with <code>bound</code> &lt; <code>position</code>
	 * @return the highest position of one element in <code>chars</code> in
	 *         [<code>position</code>, <code>scanTo</code>) that resides in a Java
	 *         partition, or <code>-1</code> if none can be found
	 */
	private static int firstNonWhitespaceBackward(IDocument document, int position, String partitioning, int bound) {
		Assert.isTrue(position < document.getLength());
		Assert.isTrue(bound >= -1);
		try {
			while (position > bound) {
				char ch = document.getChar(position);
				if (!Character.isWhitespace(ch) && isDefaultPartition(document, position, partitioning)) {
					return position;
				}
				position--;
			}
		} catch (BadLocationException e) {
		}
		return -1;
	}

	/**
	 * Finds the highest position in <code>document</code> such that the position is
	 * &lt;= <code>position</code> and &gt; <code>bound</code> and
	 * <code>document.getChar(position) == ch</code> evaluates to <code>true</code>
	 * for at least one ch in <code>chars</code> and the position is in the default
	 * partition.
	 *
	 * @param document
	 *            the document being modified
	 * @param position
	 *            the first character position in <code>document</code> to be
	 *            considered
	 * @param partitioning
	 *            the document partitioning
	 * @param bound
	 *            the first position in <code>document</code> to not consider any
	 *            more, with <code>scanTo</code> &gt; <code>position</code>
	 * @param chars
	 *            an array of <code>char</code> to search for
	 * @return the highest position of one element in <code>chars</code> in
	 *         (<code>bound</code>, <code>position</code>] that resides in a Java
	 *         partition, or <code>-1</code> if none can be found
	 */
	private static int scanBackward(IDocument document, int position, String partitioning, int bound, char[] chars) {
		Assert.isTrue(bound >= -1);
		Assert.isTrue(position < document.getLength());
		Arrays.sort(chars);
		try {
			while (position > bound) {
				if (Arrays.binarySearch(chars, document.getChar(position)) >= 0 && isDefaultPartition(document, position, partitioning)) {
					return position;
				}
				position--;
			}
		} catch (BadLocationException e) {
		}
		return -1;
	}

	/**
	 * Checks whether <code>position</code> resides in a default (Java) partition of
	 * <code>document</code>.
	 *
	 * @param document
	 *            the document being modified
	 * @param position
	 *            the position to be checked
	 * @param partitioning
	 *            the document partitioning
	 * @return <code>true</code> if <code>position</code> is in the default
	 *         partition of <code>document</code>, <code>false</code> otherwise
	 */
	private static boolean isDefaultPartition(IDocument document, int position, String partitioning) {
		Assert.isTrue(position >= 0);
		Assert.isTrue(position <= document.getLength());
		try {
			// don't use getPartition2 since we're interested in the scanned character's partition
			ITypedRegion region = TextUtilities.getPartition(document, partitioning, position, false);
			return IDocument.DEFAULT_CONTENT_TYPE.equals(region.getType());
		} catch (BadLocationException e) {
		}
		return false;
	}

	/**
	 * Determines whether the current line contains a for statement. Algorithm: any
	 * "for" word in the line is a positive, "for" contained in a string literal
	 * will produce a false positive.
	 *
	 * @param line
	 *            the line where the change is being made
	 * @param offset
	 *            the position of the caret
	 * @return <code>true</code> if <code>line</code> contains <code>for</code>,
	 *         <code>false</code> otherwise
	 */
	private boolean isForStatement(String line, int offset) {
		/* searching for (^|\s)for(\s|$) */
		int forPos = line.indexOf("for"); //$NON-NLS-1$
		if (forPos != -1) {
			if ((forPos == 0 || !Character.isJavaIdentifierPart(line.charAt(forPos - 1))) && (line.length() == forPos + 3 || !Character.isJavaIdentifierPart(line.charAt(forPos + 3)))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the position in <code>text</code> after which there comes only
	 * whitespace, up to <code>offset</code>.
	 *
	 * @param text
	 *            the text being searched
	 * @param offset
	 *            the maximum offset to search for
	 * @return the smallest value <code>v</code> such that
	 *         <code>text.substring(v, offset).trim() == 0</code>
	 */
	private int startOfWhitespaceBeforeOffset(String text, int offset) {
		int i = Math.min(offset, text.length());
		for (; i >= 1; i--) {
			if (!Character.isWhitespace(text.charAt(i - 1))) {
				break;
			}
		}
		return i;
	}

	/**
	 * Checks whether a character to be inserted is already present at the insert
	 * location (perhaps separated by some whitespace from <code>position</code>.
	 *
	 * @param document
	 *            the document we are working on
	 * @param position
	 *            the insert position of <code>ch</code>
	 * @param ch
	 *            the character to be inserted
	 * @return <code>true</code> if <code>ch</code> is already present at
	 *         <code>location</code>, <code>false</code> otherwise
	 */
	private boolean alreadyPresent(IDocument document, char ch, int position) {
		int pos = firstNonWhitespaceForward(document, position, JAVA_PARTITIONING, document.getLength());
		try {
			if (pos != -1 && document.getChar(pos) == ch) {
				return true;
			}
		} catch (BadLocationException e) {
		}
		return false;
	}

	private int firstNonWhitespaceForward(IDocument document, int position, String partitioning, int bound) {
		Assert.isTrue(position >= 0);
		Assert.isTrue(bound <= document.getLength());
		try {
			while (position < bound) {
				char ch = document.getChar(position);
				if (!Character.isWhitespace(ch) && isDefaultPartition(document, position, partitioning)) {
					return position;
				}
				position++;
			}
		} catch (BadLocationException e) {
		}
		return -1;
	}

	/**
	 * Returns a position in the first java partition after the last non-empty and
	 * non-comment partition. There is no non-whitespace from the returned position
	 * to the end of the partition it is contained in.
	 *
	 * @param document
	 *            the document being modified
	 * @param line
	 *            the line under investigation
	 * @param offset
	 *            the caret offset into <code>line</code>
	 * @param partitioning
	 *            the document partitioning
	 * @return the position of the next Java partition, or the end of
	 *         <code>line</code>
	 */
	private static int nextPartitionOrLineEnd(IDocument document, IRegion line, int offset, String partitioning) {
		// run relative to document
		final int docOffset = offset + line.getOffset();
		final int eol = line.getOffset() + line.getLength();
		int nextPartitionPos = eol; // init with line end
		int validPosition = docOffset;
		try {
			ITypedRegion partition = TextUtilities.getPartition(document, partitioning, nextPartitionPos, true);
			validPosition = getValidPositionForPartition(document, partition, eol);
			while (validPosition == -1) {
				nextPartitionPos = partition.getOffset() - 1;
				if (nextPartitionPos < docOffset) {
					validPosition = docOffset;
					break;
				}
				partition = TextUtilities.getPartition(document, partitioning, nextPartitionPos, false);
				validPosition = getValidPositionForPartition(document, partition, eol);
			}
		} catch (BadLocationException e) {
		}
		validPosition = Math.max(validPosition, docOffset);
		// make relative to line
		validPosition -= line.getOffset();
		return validPosition;
	}

	/**
	 * Returns a valid insert location (except for whitespace) in
	 * <code>partition</code> or -1 if there is no valid insert location. An valid
	 * insert location is right after any java string or character partition, or at
	 * the end of a java default partition, but never behind <code>maxOffset</code>.
	 * Comment partitions or empty java partitions do never yield valid insert
	 * positions.
	 *
	 * @param doc
	 *            the document being modified
	 * @param partition
	 *            the current partition
	 * @param maxOffset
	 *            the maximum offset to consider
	 * @return a valid insert location in <code>partition</code>, or -1 if there is
	 *         no valid insert location
	 */
	private static int getValidPositionForPartition(IDocument doc, ITypedRegion partition, int maxOffset) {
		final int INVALID = -1;
		if (JAVA_DOC.equals(partition.getType())) {
			return INVALID;
		}
		if (JAVA_MULTI_LINE_COMMENT.equals(partition.getType())) {
			return INVALID;
		}
		if (JAVA_SINGLE_LINE_COMMENT.equals(partition.getType())) {
			return INVALID;
		}
		int endOffset = Math.min(maxOffset, partition.getOffset() + partition.getLength());
		if (JAVA_CHARACTER.equals(partition.getType())) {
			return endOffset;
		}
		if (JAVA_STRING.equals(partition.getType())) {
			return endOffset;
		}
		if (IDocument.DEFAULT_CONTENT_TYPE.equals(partition.getType())) {
			try {
				if (doc.get(partition.getOffset(), endOffset - partition.getOffset()).trim().length() == 0) {
					return INVALID;
				} else {
					return endOffset;
				}
			} catch (BadLocationException e) {
				return INVALID;
			}
		}
		// default: we don't know anything about the partition - assume valid
		return endOffset;
	}

}
