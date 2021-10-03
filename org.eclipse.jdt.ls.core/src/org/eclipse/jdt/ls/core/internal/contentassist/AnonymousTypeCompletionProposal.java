/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Code copied from org.eclipse.jdt.internal.ui.text.java.AnonymousTypeCompletionProposal
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Stephan Herrmann - Contribution for Bug 463360 - [override method][null] generating method override should not create redundant null annotations
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;

/**
 * Generates Anonymous Class completion proposals.
 */
public class AnonymousTypeCompletionProposal {

	private ICompilationUnit fCompilationUnit;
	private int fReplacementOffset;
	private boolean fSnippetSupport;

	public AnonymousTypeCompletionProposal(ICompilationUnit cu, int replacementOffset, boolean snippetSupport) {
		fCompilationUnit = cu;
		fReplacementOffset = replacementOffset;
		fSnippetSupport = snippetSupport;
	}

	/*
	 * @see JavaTypeCompletionProposal#updateReplacementString(IDocument,char,int,ImportRewrite)
	 */
	public String updateReplacementString(IDocument document, int offset) throws CoreException, BadLocationException {
		// Construct empty body for performance concern
		// See https://github.com/microsoft/language-server-protocol/issues/1032#issuecomment-648748013
		String newBody = fSnippetSupport ? "{\n\t${0}\n}" : "{\n\n}";

		StringBuilder buf = new StringBuilder("new A() "); //$NON-NLS-1$
		buf.append(newBody);
		// use the code formatter
		String lineDelim = TextUtilities.getDefaultLineDelimiter(document);
		IRegion lineInfo = document.getLineInformationOfOffset(fReplacementOffset);
		Map<String, String> options = fCompilationUnit.getOptions(true);
		String replacementString = CodeFormatterUtil.format(CodeFormatter.K_EXPRESSION, buf.toString(), 0, lineDelim, options);
		int lineEndOffset = lineInfo.getOffset() + lineInfo.getLength();
		int p = offset;
		if (p < document.getLength()) {
			char ch = document.getChar(p);
			while (p < lineEndOffset) {
				if (ch == '(' || ch == ')' || ch == ';' || ch == ',') {
					break;
				}
				ch = document.getChar(++p);
			}
			if (ch != ';' && ch != ',' && ch != ')') {
				replacementString = replacementString + ';';
			}
		}
		int beginIndex = replacementString.indexOf('(');
		replacementString = replacementString.substring(beginIndex);
		return replacementString;
	}

}
