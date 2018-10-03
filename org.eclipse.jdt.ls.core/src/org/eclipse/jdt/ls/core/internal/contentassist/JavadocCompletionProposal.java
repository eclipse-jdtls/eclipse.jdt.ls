/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Based on org.eclipse.jdt.internal.ui.text.javadoc.JavaDocAutoIndentStrategy
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Red Hat, Inc - decoupling from jdt.ui
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CodeGeneration;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

public class JavadocCompletionProposal {

	private static final String ASTERISK = "*";
	private static final String WHITESPACES = " \t";
	public static final String JAVA_DOC_COMMENT = "Javadoc comment";

	public List<CompletionItem> getProposals(ICompilationUnit cu, int offset, CompletionProposalRequestor collector, IProgressMonitor monitor) throws JavaModelException {
		if (cu == null) {
			throw new IllegalArgumentException("Compilation unit must not be null"); //$NON-NLS-1$
		}
		List<CompletionItem> result = new ArrayList<>();
		IDocument d = JsonRpcHelpers.toDocument(cu.getBuffer());
		if (offset < 0 || d.getLength() == 0) {
			return result;
		}
		try {
			int p = (offset == d.getLength() ? offset - 1 : offset);
			IRegion line = d.getLineInformationOfOffset(p);
			String lineStr = d.get(line.getOffset(), line.getLength()).trim();
			if (!lineStr.startsWith("/**")) {
				return result;
			}
			if (!hasEndJavadoc(d, offset)) {
				return result;
			}
			String text = collector.getContext().getToken() == null ? "" : new String(collector.getContext().getToken());
			StringBuilder buf = new StringBuilder(text);
			IRegion prefix = findPrefixRange(d, line);
			String indentation = d.get(prefix.getOffset(), prefix.getLength());
			int lengthToAdd = Math.min(offset - prefix.getOffset(), prefix.getLength());
			buf.append(indentation.substring(0, lengthToAdd));
			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(d);
			ICompilationUnit unit = cu;
			try {
				unit.reconcile(ICompilationUnit.NO_AST, false, null, null);
				String string = createJavaDocTags(d, offset, indentation, lineDelimiter, unit);
				if (string != null && !string.trim().equals(ASTERISK)) {
					buf.append(string);
				} else {
					return result;
				}
				int nextNonWS = findEndOfWhiteSpace(d, offset, d.getLength());
				if (!Character.isWhitespace(d.getChar(nextNonWS))) {
					buf.append(lineDelimiter);
				}
			} catch (CoreException e) {
				// ignore
			}
			final CompletionItem ci = new CompletionItem();
			Range range = JDTUtils.toRange(unit, offset, 0);
			boolean isSnippetSupported = JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isCompletionSnippetsSupported();
			String replacement = prepareTemplate(buf.toString(), lineDelimiter, isSnippetSupported);
			ci.setTextEdit(new TextEdit(range, replacement));
			ci.setFilterText(JAVA_DOC_COMMENT);
			ci.setLabel(JAVA_DOC_COMMENT);
			ci.setSortText(SortTextHelper.convertRelevance(0));
			ci.setKind(CompletionItemKind.Snippet);
			ci.setInsertTextFormat(isSnippetSupported ? InsertTextFormat.Snippet : InsertTextFormat.PlainText);
			String documentation = prepareTemplate(buf.toString(), lineDelimiter, false);
			if (documentation.indexOf(lineDelimiter) == 0) {
				documentation = documentation.replaceFirst(lineDelimiter, "");
			}
			ci.setDocumentation(documentation);
			Map<String, String> data = new HashMap<>(3);
			data.put(CompletionResolveHandler.DATA_FIELD_URI, JDTUtils.toURI(cu));
			data.put(CompletionResolveHandler.DATA_FIELD_REQUEST_ID, "0");
			data.put(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID, "0");
			ci.setData(data);
			result.add(ci);
		} catch (BadLocationException excp) {
			// stop work
		}
		return result;
	}

	private String prepareTemplate(String text, String lineDelimiter, boolean addGap) {
		boolean endWithLineDelimiter = text.endsWith(lineDelimiter);
		String[] lines = text.split(lineDelimiter);
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (addGap) {
				String stripped = StringUtils.stripStart(line, WHITESPACES);
				if (stripped.startsWith(ASTERISK)) {
					if (!stripped.equals(ASTERISK)) {
						int index = line.indexOf(ASTERISK);
						buf.append(line.substring(0, index + 1));
						buf.append(" ${0}");
						buf.append(lineDelimiter);
					}
					addGap = false;
				}
			}
			buf.append(StringUtils.stripEnd(line, WHITESPACES));
			if (i < lines.length - 1 || endWithLineDelimiter) {
				buf.append(lineDelimiter);
			}
		}
		return buf.toString();
	}

	private IRegion findPrefixRange(IDocument document, IRegion line) throws BadLocationException {
		int lineOffset = line.getOffset();
		int lineEnd = lineOffset + line.getLength();
		int indentEnd = findEndOfWhiteSpace(document, lineOffset, lineEnd);
		if (indentEnd < lineEnd && document.getChar(indentEnd) == '*') {
			indentEnd++;
			while (indentEnd < lineEnd && document.getChar(indentEnd) == ' ') {
				indentEnd++;
			}
		}
		return new Region(lineOffset, indentEnd - lineOffset);
	}

	private int findEndOfWhiteSpace(IDocument document, int offset, int end) throws BadLocationException {
		while (offset < end) {
			char c = document.getChar(offset);
			if (c != ' ' && c != '\t') {
				return offset;
			}
			offset++;
		}
		return end;
	}

	private boolean hasEndJavadoc(IDocument document, int offset) throws BadLocationException {
		int pos = -1;
		while (offset < document.getLength()) {
			char c = document.getChar(offset);
			if (!Character.isWhitespace(c) && !(c == '*')) {
				pos = offset;
				break;
			}
			offset++;
		}
		if (document.getLength() >= pos + 2 && document.get(pos - 1, 2).equals("*/")) {
			return true;
		}
		return false;
	}


	private String createJavaDocTags(IDocument document, int offset, String indentation, String lineDelimiter, ICompilationUnit unit) throws CoreException, BadLocationException {
		IJavaElement element = unit.getElementAt(offset);
		if (element == null) {
			return null;
		}
		switch (element.getElementType()) {
			case IJavaElement.TYPE:
				return createTypeTags(document, offset, indentation, lineDelimiter, (IType) element);

			case IJavaElement.METHOD:
				return createMethodTags(document, offset, indentation, lineDelimiter, (IMethod) element);

			default:
				return null;
		}
	}

	private String createTypeTags(IDocument document, int offset, String indentation, String lineDelimiter, IType type) throws CoreException, BadLocationException {
		if (!accept(offset, type)) {
			return null;
		}
		String[] typeParamNames = StubUtility.getTypeParameterNames(type.getTypeParameters());
		String comment = CodeGeneration.getTypeComment(type.getCompilationUnit(), type.getTypeQualifiedName('.'), typeParamNames, lineDelimiter);
		if (comment != null) {
			return prepareTemplateComment(comment.trim(), indentation, type.getJavaProject(), lineDelimiter);
		}
		return null;
	}

	private boolean accept(int offset, IMember member) throws JavaModelException {
		ISourceRange nameRange = member.getNameRange();
		if (nameRange == null) {
			return false;
		}
		int srcOffset = nameRange.getOffset();
		return srcOffset > offset;
	}

	private String createMethodTags(IDocument document, int offset, String indentation, String lineDelimiter, IMethod method) throws CoreException, BadLocationException {
		if (!accept(offset, method)) {
			return null;
		}
		IMethod inheritedMethod = getInheritedMethod(method);
		String comment = CodeGeneration.getMethodComment(method, inheritedMethod, lineDelimiter);
		if (comment != null) {
			comment = comment.trim();
			boolean javadocComment = comment.startsWith("/**"); //$NON-NLS-1$
			if (javadocComment) {
				return prepareTemplateComment(comment, indentation, method.getJavaProject(), lineDelimiter);
			}
		}
		return null;
	}

	private String prepareTemplateComment(String comment, String indentation, IJavaProject project, String lineDelimiter) {
		//	trim comment start and end if any
		if (comment.endsWith("*/")) {
			comment = comment.substring(0, comment.length() - 2);
		}
		comment = comment.trim();
		if (comment.startsWith("/*")) { //$NON-NLS-1$
			if (comment.length() > 2 && comment.charAt(2) == '*') {
				comment = comment.substring(3); // remove '/**'
			} else {
				comment = comment.substring(2); // remove '/*'
			}
		}
		// trim leading spaces, but not new lines
		int nonSpace = 0;
		int len = comment.length();
		while (nonSpace < len && Character.getType(comment.charAt(nonSpace)) == Character.SPACE_SEPARATOR) {
			nonSpace++;
		}
		comment = comment.substring(nonSpace);
		return comment;
	}

	private IMethod getInheritedMethod(IMethod method) throws JavaModelException {
		IType declaringType = method.getDeclaringType();
		MethodOverrideTester tester = SuperTypeHierarchyCache.getMethodOverrideTester(declaringType);
		return tester.findOverriddenMethod(method, true);
	}
}
