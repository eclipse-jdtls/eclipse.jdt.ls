/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;

public class FoldingRangeHandler {

	private static final Pattern REGION_START_PATTERN = Pattern.compile("^//\\s*#?region|^//\\s+<editor-fold.*>");
	private static final Pattern REGION_END_PATTERN = Pattern.compile("^//\\s*#?endregion|^//\\s+</editor-fold>");

	private static IScanner fScanner;

	private static IScanner getScanner(CompilationUnit unit) {
		if (fScanner == null) {
			fScanner = JDTUtils.createScanner(unit);
		}
		return fScanner;
	}

	public List<FoldingRange> foldingRange(FoldingRangeRequestParams params, IProgressMonitor monitor) {
		List<FoldingRange> $ = new ArrayList<>();
		ITypeRoot unit = null;
		try {
			PreferenceManager preferenceManager = JavaLanguageServerPlugin.getInstance().getPreferencesManager();
			boolean returnCompilationUnit = preferenceManager == null ? false : preferenceManager.isClientSupportsClassFileContent() && (preferenceManager.getPreferences().isIncludeDecompiledSources());
			unit = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri(), returnCompilationUnit, monitor);
			if (unit == null || (monitor != null && monitor.isCanceled())) {
				return $;
			}
			computeFoldingRanges($, unit, monitor);
			return $;
		} finally {
			JDTUtils.discardClassFileWorkingCopy(unit);
		}
	}

	private void computeFoldingRanges(List<FoldingRange> foldingRanges, ITypeRoot unit, IProgressMonitor monitor) {
		try {
			ISourceRange range = unit.getSourceRange();
			if (!SourceRange.isAvailable(range)) {
				return;
			}

			String contents = unit.getSource();
			if (StringUtils.isBlank(contents)) {
				return;
			}

			final int shift = range.getOffset();
			IScanner scanner = getScanner(JDTUtils.getAst(unit, monitor));
			scanner.setSource(contents.toCharArray());
			scanner.resetTo(shift, shift + range.getLength());

			int start = shift;
			int startLine = -1;
			int token = 0;
			int classFileImportStart = -1;
			int classFileImportEnd = -1;
			int singleLineCommentStart = -1;
			int singleLineCommentEnd = -1;
			int prevTokenLine = -1;
			Stack<Integer> regionStarts = new Stack<>();
			while (token != ITerminalSymbols.TokenNameEOF) {
				start = scanner.getCurrentTokenStartPosition();
				startLine = scanner.getLineNumber(start);
				switch (token) {
					case ITerminalSymbols.TokenNameCOMMENT_JAVADOC:
					case ITerminalSymbols.TokenNameCOMMENT_BLOCK:
						int end = scanner.getCurrentTokenEndPosition();
						FoldingRange commentFoldingRange = new FoldingRange(startLine - 1, scanner.getLineNumber(end) - 1);
						commentFoldingRange.setKind(FoldingRangeKind.Comment);
						foldingRanges.add(commentFoldingRange);
						break;
					case ITerminalSymbols.TokenNameCOMMENT_LINE:
						String currentSource = String.valueOf(scanner.getCurrentTokenSource());
						if (REGION_START_PATTERN.matcher(currentSource).lookingAt()) {
							regionStarts.push(start);
						} else if (REGION_END_PATTERN.matcher(currentSource).lookingAt()) {
							if (regionStarts.size() > 0) {
								FoldingRange regionFolding = new FoldingRange(scanner.getLineNumber(regionStarts.pop()) - 1, scanner.getLineNumber(start) - 1);
								regionFolding.setKind(FoldingRangeKind.Region);
								foldingRanges.add(regionFolding);
							}
						} else if (prevTokenLine == startLine) {
							addCommentRangeIfSuitable(foldingRanges, singleLineCommentStart, singleLineCommentEnd);
						} else if (startLine == singleLineCommentEnd + 1) {
							singleLineCommentEnd = startLine;
						} else {
							addCommentRangeIfSuitable(foldingRanges, singleLineCommentStart, singleLineCommentEnd);
							singleLineCommentStart = startLine;
							singleLineCommentEnd = startLine;
						}
						break;
					case ITerminalSymbols.TokenNameimport:
						// Only used for computing import range in .class files
						classFileImportStart = classFileImportStart == -1 ? start : classFileImportStart;
						classFileImportEnd = scanner.getCurrentTokenEndPosition();
					default:
						break;
				}
				prevTokenLine = startLine;
				token = getNextToken(scanner);
			}
			if (unit.getElementType() == IJavaElement.CLASS_FILE && classFileImportStart != -1) {
				FoldingRange importFoldingRange = new FoldingRange(scanner.getLineNumber(classFileImportStart) - 1, scanner.getLineNumber(classFileImportEnd) - 1);
				importFoldingRange.setKind(FoldingRangeKind.Imports);
				foldingRanges.add(importFoldingRange);
			}
			addCommentRangeIfSuitable(foldingRanges, singleLineCommentStart, singleLineCommentEnd);
			computeTypeRootRanges(foldingRanges, unit, scanner);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem with folding range for " + unit.getPath().toPortableString(), e);
			monitor.setCanceled(true);
		}
	}

	private int getNextToken(IScanner scanner) {
		int token = 0;
		while (token == 0) {
			try {
				token = scanner.getNextToken();
			} catch (InvalidInputException e) {
				// ignore
				// JavaLanguageServerPlugin.logException("Problem with folding range", e);
			}
		}
		return token;
	}

	private void computeTypeRootRanges(List<FoldingRange> foldingRanges, ITypeRoot unit, IScanner scanner) throws CoreException {
		if (unit.hasChildren()) {
			for (IJavaElement child : unit.getChildren()) {
				if (child instanceof IImportContainer importContainer) {
					ISourceRange importRange = importContainer.getSourceRange();
					FoldingRange importFoldingRange = new FoldingRange(scanner.getLineNumber(importRange.getOffset()) - 1, scanner.getLineNumber(importRange.getOffset() + importRange.getLength()) - 1);
					importFoldingRange.setKind(FoldingRangeKind.Imports);
					foldingRanges.add(importFoldingRange);
				} else if (child instanceof IType type) {
					computeTypeRanges(foldingRanges, type, scanner);
				}
			}
		}
	}

	private void computeTypeRanges(List<FoldingRange> foldingRanges, IType unit, IScanner scanner) throws CoreException {
		ISourceRange typeRange = unit.getSourceRange();
		foldingRanges.add(new FoldingRange(scanner.getLineNumber(unit.getNameRange().getOffset()) - 1, scanner.getLineNumber(typeRange.getOffset() + typeRange.getLength()) - 1));
		IJavaElement[] children = unit.getChildren();
		for (IJavaElement c : children) {
			if (c instanceof IMethod || c instanceof IInitializer) {
				computeMethodRanges(foldingRanges, (IMember) c, scanner);
			} else if (c instanceof IType type) {
				computeTypeRanges(foldingRanges, type, scanner);
			}
		}
	}

	private void computeMethodRanges(List<FoldingRange> foldingRanges, IMember member, IScanner scanner) throws CoreException {
		ISourceRange sourceRange = member.getSourceRange();
		final int shift = sourceRange.getOffset();
		scanner.resetTo(shift, shift + sourceRange.getLength());

		ISourceRange nameRange = member.getNameRange();
		int nameStart = nameRange != null ? nameRange.getOffset() : sourceRange.getOffset();
		foldingRanges.add(new FoldingRange(scanner.getLineNumber(nameStart) - 1, scanner.getLineNumber(shift + sourceRange.getLength()) - 1));

		int start = shift;
		int token = 0;
		int prevTokenLine = 0;
		Stack<Integer> leftParens = null;
		Stack<Integer> prevCaseLines = new Stack<>();
		Map<Integer, Integer> candidates = new HashMap<>();
		while (token != ITerminalSymbols.TokenNameEOF) {
			start = scanner.getCurrentTokenStartPosition();
			int currentLine = scanner.getLineNumber(start) - 1;
			switch (token) {
				case ITerminalSymbols.TokenNameLBRACE:
					if (leftParens == null) {
						// Start of method body
						leftParens = new Stack<>();
					} else {
						// Start & end overlap (specifically in the case of
						// try/catch and if/else if where the closing brace is on the same line as the following expression)
						// Adjust the previous one for visibility:
						List<Integer> keys = new ArrayList<>();
						if (candidates.containsValue(currentLine)) {
							for (Map.Entry<Integer, Integer> entry : candidates.entrySet()) {
								if (entry.getValue() == currentLine) {
									keys.add(entry.getKey());
								}
							}
							for (Integer key : keys) {
								candidates.remove(key);
								if (key < currentLine - 1) {
									candidates.put(key, currentLine - 1);
								}
							}
						}
						// For curly braces enclosing a case statement, begin the folding range on the line with the case statement
						if (prevTokenLine != currentLine && !prevCaseLines.isEmpty() && prevTokenLine == prevCaseLines.peek()) {
							leftParens.push(prevTokenLine);
						} else {
							leftParens.push(currentLine);
						}
					}
					break;
				case ITerminalSymbols.TokenNameRBRACE:
					int endPos = scanner.getCurrentTokenEndPosition();
					if (leftParens != null && leftParens.size() > 0) {
						int endLine = scanner.getLineNumber(endPos) - 1;
						int startLine = leftParens.pop();
						if (startLine < endLine) {
							candidates.put(startLine, endLine);
						}
						// Assume the last switch case:
						if (!prevCaseLines.isEmpty()) {
							int prevCaseLine = prevCaseLines.peek();
							if (startLine < prevCaseLine) {
								if (endLine - 1 > prevCaseLine) {
									candidates.put(prevCaseLine, endLine - 1);
									prevCaseLines.pop();
								}
							}
						}
					}
					break;
				case ITerminalSymbols.TokenNameswitch:
					prevCaseLines.push(-1);
					break;
				case ITerminalSymbols.TokenNamecase:
				case ITerminalSymbols.TokenNamedefault:
					if (!prevCaseLines.isEmpty()) {
						int prevCaseLine = prevCaseLines.pop();
						if (prevCaseLine != -1 && currentLine - 1 >= prevCaseLine) {
							candidates.put(prevCaseLine, currentLine - 1);
						}
						prevCaseLine = currentLine;
						prevCaseLines.push(prevCaseLine);
					}
					break;
				default:
					break;
			}
			prevTokenLine = currentLine;
			token = getNextToken(scanner);
		}

		for (Map.Entry<Integer, Integer> entry : candidates.entrySet()) {
			foldingRanges.add(new FoldingRange(entry.getKey(), entry.getValue()));
		}
	}

	private void addCommentRangeIfSuitable(List<FoldingRange> foldingRanges, int start, int end) {
		if (end > start) {
			FoldingRange singleLineCommentFoldingRange = new FoldingRange(start - 1, end - 1);
			singleLineCommentFoldingRange.setKind(FoldingRangeKind.Comment);
			foldingRanges.add(singleLineCommentFoldingRange);
		}
	}
}
