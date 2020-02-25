/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.nls.NLSUtil
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.nls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.formatter.IndentManipulation;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Region;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.TextEdit;


public class NLSUtil {

	//no instances
	private NLSUtil() {
	}

	/**
	 * Reads a stream into a String and closes the stream.
	 * @param is the input stream
	 * @param encoding the encoding
	 * @return the contents, or <code>null</code> if an error occurred
	 */
	public static String readString(InputStream is, String encoding) {
		if (is == null) {
			return null;
		}
		BufferedReader reader= null;
		try {
			StringBuilder buffer= new StringBuilder();
			char[] part= new char[2048];
			int read= 0;
			reader= new BufferedReader(new InputStreamReader(is, encoding));

			while ((read= reader.read(part)) != -1) {
				buffer.append(part, 0, read);
			}

			return buffer.toString();

		} catch (IOException ex) {
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ex) {
				}
			}
		}
		return null;
	}

	/**
	 * Creates and returns an NLS tag edit for a string that is at the specified position in a
	 * compilation unit.
	 *
	 * @param cu the compilation unit
	 * @param position position of the string
	 * @return the edit, or <code>null</code> if the string is already NLSed or the edit could not
	 *         be created for some other reason.
	 * @throws CoreException if scanning fails
	 */
	public static TextEdit createNLSEdit(ICompilationUnit cu, int position) throws CoreException {
		NLSLine nlsLine= scanCurrentLine(cu, position);
		if (nlsLine == null) {
			return null;
		}
		NLSElement element= findElement(nlsLine, position);
		if (element.hasTag()) {
			return null;
		}
		NLSElement[] elements= nlsLine.getElements();
		int indexInElementList= Arrays.asList(elements).indexOf(element);
		int editOffset= computeInsertOffset(elements, indexInElementList, cu);
		String editText= ' ' + NLSElement.createTagText(indexInElementList + 1); //tags are 1-based
		return new InsertEdit(editOffset, editText);
	}

	/**
	 * Creates and returns NLS tag edits for strings that are at the specified positions in a
	 * compilation unit.
	 *
	 * @param cu the compilation unit
	 * @param positions positions of the strings
	 * @return the edit, or <code>null</code> if all strings are already NLSed or the edits could
	 *         not be created for some other reason.
	 * @throws CoreException if scanning fails
	 */
	public static TextEdit[] createNLSEdits(ICompilationUnit cu, int[] positions) throws CoreException {
		List<InsertEdit> result= new ArrayList<>();
		try {
			NLSLine[] allLines= NLSScanner.scan(cu);
			for (int i= 0; i < allLines.length; i++) {
				NLSLine line= allLines[i];
				NLSElement[] elements= line.getElements();
				for (int j= 0; j < elements.length; j++) {
					NLSElement element= elements[j];
					if (!element.hasTag()) {
						for (int k= 0; k < positions.length; k++) {
							if (isPositionInElement(element, positions[k])) {
								int editOffset;
								if (j==0) {
									if (elements.length > j+1) {
										editOffset= elements[j+1].getTagPosition().getOffset();
									} else {
										editOffset= findLineEnd(cu, element.getPosition().getOffset());
									}
								} else {
									Region previousPosition= elements[j-1].getTagPosition();
									editOffset=  previousPosition.getOffset() + previousPosition.getLength();
								}
								String editText= ' ' + NLSElement.createTagText(j + 1); //tags are 1-based
								result.add(new InsertEdit(editOffset, editText));
							}
						}
					}
				}
			}
		} catch (InvalidInputException e) {
			return null;
		} catch (BadLocationException e) {
			return null;
		}
		if (result.isEmpty()) {
			return null;
		}

		return result.toArray(new TextEdit[result.size()]);
	}

	private static NLSLine scanCurrentLine(ICompilationUnit cu, int position) throws JavaModelException {
		try {
			Assert.isTrue(position >= 0 && position <= cu.getBuffer().getLength());
			NLSLine[] allLines= NLSScanner.scan(cu);
			for (int i= 0; i < allLines.length; i++) {
				NLSLine line= allLines[i];
				if (findElement(line, position) != null) {
					return line;
				}
			}
			return null;
		} catch (InvalidInputException e) {
			return null;
		} catch (BadLocationException e) {
			return null;
		}
	}

	private static boolean isPositionInElement(NLSElement element, int position) {
		Region elementPosition= element.getPosition();
		return (elementPosition.getOffset() <= position && position <= elementPosition.getOffset() + elementPosition.getLength());
	}

	private static NLSElement findElement(NLSLine line, int position) {
		NLSElement[] elements= line.getElements();
		for (int i= 0; i < elements.length; i++) {
			NLSElement element= elements[i];
			if (isPositionInElement(element, position)) {
				return element;
			}
		}
		return null;
	}

	//we try to find a good place to put the nls tag
	//first, try to find the previous nlsed-string and try putting after its tag
	//if no such string exists, try finding the next nlsed-string try putting before its tag
	//otherwise, find the line end and put the tag there
	private static int computeInsertOffset(NLSElement[] elements, int index, ICompilationUnit cu) throws CoreException {
		NLSElement previousTagged= findPreviousTagged(index, elements);
		if (previousTagged != null) {
			return previousTagged.getTagPosition().getOffset() + previousTagged.getTagPosition().getLength();
		}
		NLSElement nextTagged= findNextTagged(index, elements);
		if (nextTagged != null) {
			return nextTagged.getTagPosition().getOffset();
		}
		return findLineEnd(cu, elements[index].getPosition().getOffset());
	}

	private static NLSElement findPreviousTagged(int startIndex, NLSElement[] elements) {
		int i= startIndex - 1;
		while (i >= 0) {
			if (elements[i].hasTag()) {
				return elements[i];
			}
			i--;
		}
		return null;
	}

	private static NLSElement findNextTagged(int startIndex, NLSElement[] elements) {
		int i= startIndex + 1;
		while (i < elements.length) {
			if (elements[i].hasTag()) {
				return elements[i];
			}
			i++;
		}
		return null;
	}

	private static int findLineEnd(ICompilationUnit cu, int position) throws JavaModelException {
		IBuffer buffer= cu.getBuffer();
		int length= buffer.getLength();
		for (int i= position; i < length; i++) {
			if (IndentManipulation.isLineDelimiterChar(buffer.getChar(i))) {
				return i;
			}
		}
		return length;
	}

	/**
	 * Determine a good insertion position for <code>key</code> into the list of given
	 * <code>keys</code>.
	 *
	 * @param key the key to insert
	 * @param keys a list of {@link String}s
	 * @return the position in <code>keys</code> after which key must be inserted, returns -1 for before
	 * @since 3.4
	 */
	public static int getInsertionPosition(String key, List<String> keys) {
		int result= 0;

		int invertDistance= Integer.MIN_VALUE;
		int i= 0;
		for (Iterator<String> iterator= keys.iterator(); iterator.hasNext();) {
			String string= iterator.next();

			int currentInvertDistance= invertDistance(key, string);
			if (currentInvertDistance > invertDistance) {
				invertDistance= currentInvertDistance;
				if (Collator.getInstance().compare(key, string) >= 0) {
					result= i;
				} else {
					result= i - 1;
				}
			} else if (currentInvertDistance == invertDistance) {
				if (Collator.getInstance().compare(key, string) >= 0) {
					result= i;
				}
			}

			i++;
		}

		return result;
	}

	/**
	 * @param insertKey the key to insert
	 * @param existingKey the existing key
	 * @return the invert distance between <code>insertkey</code> and <code>existingKey</code>,
	 * the higher the closer
	 * @since 3.4
	 */
	public static int invertDistance(String insertKey, String existingKey) {

		int existingKeyLength= existingKey.length();
		int insertKeyLength= insertKey.length();

		int minLen= Math.min(insertKeyLength, existingKeyLength);

		int prefixMatchCount= 0;
		for (int i= 0; i < minLen; i++) {
			if (insertKey.charAt(i) == existingKey.charAt(i)) {
				prefixMatchCount++;
			} else {
				return prefixMatchCount << 16;
			}
		}

		if (insertKeyLength > existingKeyLength && isSeparator(insertKey.charAt(existingKeyLength))) {
			//existing: prefix
			//new:      prefix_xyz
			//insert it after existing key -> prefix match plus one
			return (prefixMatchCount + 1) << 16;
		}

		int existingLonger= existingKeyLength - insertKeyLength;
		// Sort by prefix match length first (<< 16). Existing keys that are longer
		// than the insertion key are not preferred insertion positions.
		return (prefixMatchCount << 16) - Math.max(0, existingLonger);
	}

	private static boolean isSeparator(char ch) {
		return ch == '.' || ch == '-' || ch == '_';
	}
}
