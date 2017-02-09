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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;


/**
 * @author IBM Corporation (Markus Keller)
 */
public class FormatterHandler {

	CompletableFuture<List<? extends org.eclipse.lsp4j.TextEdit>> formatting(DocumentFormattingParams params){
		return CompletableFuture.supplyAsync( ()->{
			ICompilationUnit cu = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
			if(cu == null ) {
				return Collections.emptyList();
			}
			return format(cu,params.getOptions(), null);
		});

	}

	CompletableFuture<List<? extends org.eclipse.lsp4j.TextEdit>> rangeFormatting(DocumentRangeFormattingParams params){
		return CompletableFuture.supplyAsync(()->{
			ICompilationUnit cu = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
			if(cu == null )
				return Collections.emptyList();
			return format(cu, params.getOptions(), params.getRange());
		});
	}

	private List<org.eclipse.lsp4j.TextEdit> format(ICompilationUnit cu, FormattingOptions options, Range range) {

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
				return Collections.<org.eclipse.lsp4j.TextEdit>emptyList();
			}
			MultiTextEdit flatEdit = TextEditUtil.flatten(format);
			return convertEdits(flatEdit.getChildren(), document);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return Collections.emptyList();
		}
	}

	private IRegion getRegion(Range range, IDocument document) {
		try {
			int offset = document.getLineOffset(range.getStart().getLine())
					+ range.getStart().getCharacter();
			int endOffset = document.getLineOffset(range.getEnd().getLine())
					+ range.getEnd().getCharacter();
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
		boolean insertSpaces = options.isInsertSpaces();
		if (insertSpaces) {
			eclipseOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, insertSpaces ? JavaCore.SPACE : JavaCore.TAB);
		}
		return eclipseOptions;
	}


	private static List<org.eclipse.lsp4j.TextEdit> convertEdits(TextEdit[] edits, IDocument document) {
		return Arrays.stream(edits).map(t -> convertEdit(t, document)).collect(Collectors.toList());
	}

	private static org.eclipse.lsp4j.TextEdit convertEdit(TextEdit edit, IDocument document) {
		org.eclipse.lsp4j.TextEdit textEdit  = new org.eclipse.lsp4j.TextEdit();
		if (edit instanceof ReplaceEdit) {
			ReplaceEdit replaceEdit = (ReplaceEdit) edit;
			textEdit.setNewText(replaceEdit.getText());
			int offset = edit.getOffset();
			textEdit.setRange( new Range(createPosition(document, offset),
					createPosition(document, offset+edit.getLength())));
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
