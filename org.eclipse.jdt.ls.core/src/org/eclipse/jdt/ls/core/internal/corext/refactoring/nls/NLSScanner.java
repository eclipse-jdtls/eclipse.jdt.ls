/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla <b.muskalla@gmx.net> - [nls tooling] Externalize Strings Wizard should not touch annotation arguments - https://bugs.eclipse.org/bugs/show_bug.cgi?id=102132
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.nls;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;


public class NLSScanner {

	@SuppressWarnings("deprecation")
	private static final int InternalTokenNameIdentifier = ITerminalSymbols.TokenNameIdentifier;

	//no instances
	private NLSScanner() {
	}

	public static NLSLine[] scan(ICompilationUnit cu) throws JavaModelException, BadLocationException, InvalidInputException {
		IJavaProject javaProject= cu.getJavaProject();
		IScanner scanner= null;
		if (javaProject != null) {
			String complianceLevel= javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true);
			String sourceLevel= javaProject.getOption(JavaCore.COMPILER_SOURCE, true);
			scanner= ToolFactory.createScanner(true, true, true, sourceLevel, complianceLevel);
		} else {
			scanner= ToolFactory.createScanner(true, true, false, true);
		}
		return scan(scanner, cu.getBuffer().getCharacters());
	}

	public static NLSLine[] scan(String s) throws InvalidInputException, BadLocationException {
		IScanner scanner= ToolFactory.createScanner(true, true, false, true);
		return scan(scanner, s.toCharArray());
	}

	private static NLSLine[] scan(IScanner scanner, char[] content) throws InvalidInputException, BadLocationException {
		List<NLSLine> lines= new ArrayList<>();
		scanner.setSource(content);
		int token= scanner.getNextToken();
		int currentLineNr= -1;
		int previousLineNr= -1;
		NLSLine currentLine= null;
		int nlsElementIndex= 0;

		/*
		 * Stack of int[1] containing either
		 * a) >=0: parenthesis counter per nested annotation level, or
		 * b)  -1: read a '@' or '.' in annotation type, waiting for identifier to complete annotation.
		 */
		LinkedList<int[]> insideAnnotation= new LinkedList<>();
		int defaultCounter= 0; // counting up tokens starting with 'default'

		while (token != ITerminalSymbols.TokenNameEOF) {
			switch (token) {
				// don't NLS inside annotation arguments and after 'default'
				case ITerminalSymbols.TokenNameAT:
					insideAnnotation.add(new int[] { -1 });
					break;
				case ITerminalSymbols.TokenNameinterface:
					insideAnnotation.clear(); //e.g. @interface
					break;

				case InternalTokenNameIdentifier:
					if (! insideAnnotation.isEmpty()) {
						int[] parenCounter= insideAnnotation.getLast();
						if (parenCounter[0] == -1) {
							parenCounter[0]= 0;
						} else if (parenCounter[0] == 0)
						 {
							insideAnnotation.removeLast(); // identifier after annotation name -> was a simple annotation
						}
					}
					break;

				case ITerminalSymbols.TokenNameDOT:
					if (! insideAnnotation.isEmpty()) {
						int[] parenCounter= insideAnnotation.getLast();
						if (parenCounter[0] == 0) {
							parenCounter[0]= -1;
						} else if (parenCounter[0] == -1)
						 {
							insideAnnotation.removeLast(); // '@' '.' -> something's wrong, back out...
						}
					}
					break;

				case ITerminalSymbols.TokenNameLPAREN:
					if (! insideAnnotation.isEmpty()) {
						++insideAnnotation.getLast()[0];
					}
					break;
				case ITerminalSymbols.TokenNameRPAREN:
					if (! insideAnnotation.isEmpty()) {
						int parenCount= --insideAnnotation.getLast()[0];
						if (parenCount <= 0) {
							insideAnnotation.removeLast();
						}

					}
					break;

				case ITerminalSymbols.TokenNamedefault:
					defaultCounter= 1;
					break;
				case ITerminalSymbols.TokenNameCOLON:
					if (defaultCounter == 1) {
						defaultCounter= 0; // reset in switch statement's "default :" ...
					} else if (defaultCounter > 0)
					 {
						defaultCounter++; // ... but not in conditional in annotation member default value
					}
					break;
				case ITerminalSymbols.TokenNameSEMICOLON:
					defaultCounter= 0;
					break;
				case ITerminalSymbols.TokenNameLBRACE:
					if (defaultCounter > 1)
					 {
						defaultCounter= 0; // reset in default interface method declaration
					}
					// ... but not in String[]-typed annotation member default value
					break;


				case ITerminalSymbols.TokenNameStringLiteral:
					if (insideAnnotation.isEmpty() && defaultCounter == 0) {
						currentLineNr= scanner.getLineNumber(scanner.getCurrentTokenStartPosition());
						if (currentLineNr != previousLineNr) {
							currentLine= new NLSLine(currentLineNr - 1);
							lines.add(currentLine);
							previousLineNr= currentLineNr;
							nlsElementIndex= 0;
						}
						String value= new String(scanner.getCurrentTokenSource());
						currentLine.add(
								new NLSElement(
										value,
										scanner.getCurrentTokenStartPosition(),
										scanner.getCurrentTokenEndPosition() + 1 - scanner.getCurrentTokenStartPosition(),
										nlsElementIndex++,
										false));
					}
					break;
				case ITerminalSymbols.TokenNameCOMMENT_LINE:
					defaultCounter= 0;
					if (currentLineNr != scanner.getLineNumber(scanner.getCurrentTokenStartPosition())) {
						break;
					}

					parseTags(currentLine, scanner);
					break;

				case ITerminalSymbols.TokenNameWHITESPACE:
				case ITerminalSymbols.TokenNameCOMMENT_BLOCK:
				case ITerminalSymbols.TokenNameCOMMENT_JAVADOC:
					break;

				default:
					if (defaultCounter > 0) {
						defaultCounter++;
					}
					break;
			}
			token= scanner.getNextToken();
		}
		NLSLine[] result;
		result= lines.toArray(new NLSLine[lines.size()]);
		IDocument document= new Document(String.valueOf(scanner.getSource()));
		for (int i= 0; i < result.length; i++) {
			setTagPositions(document, result[i]);
		}
		return result;
	}

	private static void parseTags(NLSLine line, IScanner scanner) {
		String s= new String(scanner.getCurrentTokenSource());
		int pos= s.indexOf(NLSElement.TAG_PREFIX);
		while (pos != -1) {
			int start= pos + NLSElement.TAG_PREFIX_LENGTH;
			int end= s.indexOf(NLSElement.TAG_POSTFIX, start);
			if (end < 0)
			 {
				return; //no error recovery
			}

			String index= s.substring(start, end);
			int i= 0;
			try {
				i= Integer.parseInt(index) - 1; 	// Tags are one based not zero based.
			} catch (NumberFormatException e) {
				return; //ignore the exception - no error recovery
			}
			if (line.exists(i)) {
				NLSElement element= line.get(i);
				element.setTagPosition(scanner.getCurrentTokenStartPosition() + pos, end - pos + 1);
			} else {
				return; //no error recovery
			}
			pos= s.indexOf(NLSElement.TAG_PREFIX, start);
		}
	}

	private static void setTagPositions(IDocument document, NLSLine line) throws BadLocationException {
		IRegion info= document.getLineInformation(line.getLineNumber());
		int defaultValue= info.getOffset() + info.getLength();
		NLSElement[] elements= line.getElements();
		for (int i= 0; i < elements.length; i++) {
			NLSElement element= elements[i];
			if (!element.hasTag()) {
				element.setTagPosition(computeInsertOffset(elements, i, defaultValue), 0);
			}
		}
	}

	private static int computeInsertOffset(NLSElement[] elements, int index, int defaultValue) {
		NLSElement previousTagged= findPreviousTagged(index, elements);
		if (previousTagged != null) {
			return previousTagged.getTagPosition().getOffset() + previousTagged.getTagPosition().getLength();
		}
		NLSElement nextTagged= findNextTagged(index, elements);
		if (nextTagged != null) {
			return nextTagged.getTagPosition().getOffset();
		}
		return defaultValue;
	}

	private static NLSElement findPreviousTagged(int startIndex, NLSElement[] elements){
		int i= startIndex - 1;
		while (i >= 0){
			if (elements[i].hasTag()) {
				return elements[i];
			}
			i--;
		}
		return null;
	}

	private static NLSElement findNextTagged(int startIndex, NLSElement[] elements){
		int i= startIndex + 1;
		while (i < elements.length){
			if (elements[i].hasTag()) {
				return elements[i];
			}
			i++;
		}
		return null;
	}
}

