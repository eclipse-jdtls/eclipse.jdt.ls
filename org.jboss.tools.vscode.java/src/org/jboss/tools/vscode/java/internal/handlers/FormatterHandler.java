/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.jboss.tools.langs.DocumentFormattingParams;
import org.jboss.tools.langs.DocumentRangeFormattingParams;
import org.jboss.tools.langs.FormattingOptions;
import org.jboss.tools.langs.Position;
import org.jboss.tools.langs.Range;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

import copied.org.eclipse.jdt.internal.corext.refactoring.util.TextEditUtil;

/**
 * @author IBM Corporation (Markus Keller)
 */
public class FormatterHandler {


	public class DocFormatter implements RequestHandler<DocumentFormattingParams, List<org.jboss.tools.langs.TextEdit>>{

		@Override
		public boolean canHandle(String request) {
			return LSPMethods.DOCUMENT_FORMATTING.getMethod().equals(request);
		}

		@Override
		public List<org.jboss.tools.langs.TextEdit> handle(DocumentFormattingParams param, CancelMonitor cm) {
			ICompilationUnit cu = JDTUtils.resolveCompilationUnit(param.getTextDocument().getUri());
			if(cu == null ) {
				return Collections.emptyList();
			}
			return format(cu,param.getOptions(), null);
		}

	}

	public class RangeFormatter implements RequestHandler<DocumentRangeFormattingParams, List<org.jboss.tools.langs.TextEdit>>{

		@Override
		public boolean canHandle(String request) {
			return LSPMethods.DOCUMENT_RANGE_FORMATTING.getMethod().equals(request);
		}

		@Override
		public List<org.jboss.tools.langs.TextEdit> handle(DocumentRangeFormattingParams param, CancelMonitor cm) {
			ICompilationUnit cu = JDTUtils.resolveCompilationUnit(param.getTextDocument().getUri());
			if(cu == null )
				return Collections.<org.jboss.tools.langs.TextEdit>emptyList();
			return format(cu,param.getOptions(),param.getRange());
		}
	}

	private List<org.jboss.tools.langs.TextEdit> format(ICompilationUnit cu, FormattingOptions options, Range range) {

		CodeFormatter formatter = ToolFactory.createCodeFormatter(getOptions(options,cu));

		try {
			IDocument document = JsonRpcHelpers.toDocument(cu.getBuffer());
			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(document);
			IRegion region = (range == null ? new Region(0,document.getLength()) : getRegion(range,document));
			// could not calculate region abort.
			if(region == null ) return null;
			String sourceToFormat = document.get();
			TextEdit format = formatter.format(CodeFormatter.K_COMPILATION_UNIT, sourceToFormat, region.getOffset(), region.getLength(), 0, lineDelimiter);
			if (format == null || format.getChildren().length == 0) {
				// nothing to return
				return Collections.<org.jboss.tools.langs.TextEdit>emptyList();
			}
			MultiTextEdit flatEdit = TextEditUtil.flatten(format);
			return convertEdits(flatEdit.getChildren(), document);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return Collections.<org.jboss.tools.langs.TextEdit>emptyList();
		}
	}

	private IRegion getRegion(Range range, IDocument document) {
		try {
			int offset = document.getLineOffset(range.getStart().getLine().intValue())
					+ range.getStart().getCharacter().intValue();
			int endOffset = document.getLineOffset(range.getEnd().getLine().intValue())
					+ range.getEnd().getCharacter().intValue();
			int length = endOffset - offset;
			return new Region(offset, length);
		} catch (BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return null;
	}

	private static Map<String, String> getOptions(FormattingOptions options, ICompilationUnit cu) {
		Map<String, String> eclipseOptions = cu.getJavaProject().getOptions(true);
		Integer tabSize = options.getTabSize();
		if (tabSize != null) {
			int tSize = tabSize.intValue();
			if (tSize > 0) {
				eclipseOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, Integer.toString(tSize));
			}
		}
		Boolean insertSpaces = options.getInsertSpaces();
		if (insertSpaces != null) {
			eclipseOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, insertSpaces ? JavaCore.SPACE : JavaCore.TAB);
		}
		return eclipseOptions;
	}


	private static List<org.jboss.tools.langs.TextEdit> convertEdits(TextEdit[] edits, IDocument document) {
		return Arrays.stream(edits).map(t -> convertEdit(t, document)).collect(Collectors.toList());
	}

	private static org.jboss.tools.langs.TextEdit convertEdit(TextEdit edit, IDocument document) {
		org.jboss.tools.langs.TextEdit textEdit  = new org.jboss.tools.langs.TextEdit();
		if (edit instanceof ReplaceEdit) {
			ReplaceEdit replaceEdit = (ReplaceEdit) edit;
			textEdit.setNewText(replaceEdit.getText());
			int offset = edit.getOffset();
			return textEdit.withRange(new Range().withStart(createPosition(document,offset))
					.withEnd(createPosition(document, offset + edit.getLength())));
		}
		return textEdit;
	}


	private static Position createPosition(IDocument document, int offset) {
		Position start =  new Position();
		try {
			int lineOfOffset = document.getLineOfOffset(offset);
			start.setLine( Integer.valueOf(lineOfOffset));
			start.setCharacter(Integer.valueOf( offset - document.getLineOffset(lineOfOffset)));
		} catch (BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return start;
	}
}
