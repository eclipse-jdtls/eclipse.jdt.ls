/*******************************************************************************
 * Copyright (c) 2016-2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static java.util.stream.Collectors.toMap;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditUtil;
import org.eclipse.jdt.internal.formatter.DefaultCodeFormatterOptions;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileVersionerCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences.NonStandardJavaFormatting;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * @author IBM Corporation (Markus Keller)
 */
public class FormatterHandler {

	private static final char CLOSING_BRACE = '}';
	private static final char NEW_LINE = '\n';
	private static final char COMMA = ',';
	private static final String SPACE = " ";
	private static final String TAB = "\t";

	private PreferenceManager preferenceManager;

	public FormatterHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<? extends org.eclipse.lsp4j.TextEdit> formatting(DocumentFormattingParams params, IProgressMonitor monitor) {
		return format(params.getTextDocument().getUri(), params.getOptions(), (Range) null, monitor);
	}

	public List<? extends org.eclipse.lsp4j.TextEdit> rangeFormatting(DocumentRangeFormattingParams params, IProgressMonitor monitor) {
		return format(params.getTextDocument().getUri(), params.getOptions(), params.getRange(), monitor);
	}

	private List<org.eclipse.lsp4j.TextEdit> format(String uriString, FormattingOptions options, Range range, IProgressMonitor monitor) {
		if (!preferenceManager.getPreferences().isJavaFormatEnabled()) {
			return Collections.emptyList();
		}
		URI uri = JDTUtils.toURI(uriString);
		if (uri == null) {
			return Collections.emptyList();
		}
		NonStandardJavaFormatting nonStandardJavaFormatting = preferenceManager.getClientPreferences().getNonStandardJavaFormatting();
		if (nonStandardJavaFormatting != null && nonStandardJavaFormatting.isValid() && nonStandardJavaFormatting.getSchemes().contains(uri.getScheme())) {
			Object[] params = { uriString };
			Object o = JavaLanguageServerPlugin.getInstance().getClientConnection().executeClientCommand(nonStandardJavaFormatting.getGetContentCallback(), params);
			if (o instanceof String text && !text.isBlank()) {
				return formatJavaCode(text, options, range, monitor);
			}
			return Collections.emptyList();
		}
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		if (cu == null) {
			return Collections.emptyList();
		}
		IRegion region = null;
		IDocument document = null;
		try {
			document = JsonRpcHelpers.toDocument(cu.getBuffer());
			if (document != null) {
				region = (range == null ? new Region(0, document.getLength()) : getRegion(range, document));
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		if (region == null) {
			return Collections.emptyList();
		}

		return format(cu, document, region, options, preferenceManager.getPreferences().isJavaFormatComments(), monitor);
	}

	/**
	 * public only for testing purpose
	 */
	public List<org.eclipse.lsp4j.TextEdit> formatJavaCode(String text, FormattingOptions options, Range range, IProgressMonitor monitor) {
		String startText = "";
		String endText = "";
		String originalText = text;
		if (range != null) {
			Document document = new Document(text);
			IRegion region = getRegion(range, document);
			if (region == null) {
				return Collections.emptyList();
			}
			if (region.getOffset() > 0 || region.getLength() < document.getLength()) {
				if (region.getOffset() > 0) {
					startText = text.substring(0, region.getOffset());
				}
				endText = text.substring(region.getOffset() + region.getLength() + 1, document.getLength());
				try {
					text = document.get(region.getOffset(), region.getLength());
				} catch (BadLocationException e) {
					return Collections.emptyList();
				}
			}
		}
		String formatted = formatString(text, options, monitor);
		if (formatted == null) {
			// check imports
			Pattern importPattern = Pattern.compile("^(import\\s+.*?);", Pattern.MULTILINE);
			Matcher importMatcher = importPattern.matcher(text);
			StringBuilder builder = new StringBuilder();
			int importEnd = 0;
			String lineDelimiter = TextUtilities.determineLineDelimiter(text, "\n");
			while (importMatcher.find()) {
				builder.append(importMatcher.group()).append(lineDelimiter);
				importEnd = importMatcher.end();
			}
			String imports = builder.toString();
			builder = new StringBuilder();
			if (!imports.isBlank()) {
				String res = formatString(imports, options, monitor);
				if (res != null) {
					builder.append(res);
				} else {
					builder.append(imports);
				}
				builder.append(lineDelimiter);
			}
			String remaining = text.substring(importEnd);
			if (!remaining.trim().isBlank()) {
				String res = formatString(remaining.trim(), options, monitor);
				if (res != null) {
					builder.append(res);
				} else {
					builder.append(remaining);
				}
			}
			formatted = builder.toString();
		}
		if (formatted != null) {
			text = originalText;
			formatted = startText + formatted + endText;
			Document document = new Document(text);
			org.eclipse.lsp4j.TextEdit edit = new org.eclipse.lsp4j.TextEdit(new Range(createPosition(document, 0), createPosition(document, document.getLength())), formatted);
			List<org.eclipse.lsp4j.TextEdit> list = Collections.singletonList(edit);
			return list;
		}
		return Collections.emptyList();
	}

	private String formatString(String text, FormattingOptions options, IProgressMonitor monitor) {
		String formatted = format(text, "", "", options, monitor);
		if (formatted == null) {
			String prefix = "// PREFIX\npublic class Temporary { {\n";
			String suffix = "// SUFFIX\n} }";
			formatted = format(text, prefix, suffix, options, monitor);
			if (formatted == null) {
				prefix = "// PREFIX\npublic class Temporary { public void run() {\n";
				suffix = "// SUFFIX\n} }";
				formatted = format(text, prefix, suffix, options, monitor);
				if (formatted == null) {
					prefix = "// PREFIX\npublic class Temporary {\n";
					suffix = "// SUFFIX\n}";
					formatted = format(text, prefix, suffix, options, monitor);
				}
			}
		}
		return formatted;
	}

	private String format(String text, String prefix, String suffix, FormattingOptions options, IProgressMonitor monitor) {
		String content = prefix + text + suffix;
		IDocument document = new Document();
		document.set(content);
		CodeFormatter formatter = ToolFactory.createCodeFormatter(options);
		int kind = CodeFormatter.K_COMPILATION_UNIT;
		if (preferenceManager.getPreferences().isJavaFormatComments()) {
			kind = kind | CodeFormatter.F_INCLUDE_COMMENTS;
		}
		TextEdit format = formatter.format(kind, content, prefix.length(), text.length(), 0, TextUtilities.getDefaultLineDelimiter(document));
		if (format == null || format.getChildren().length == 0 || monitor.isCanceled()) {
			return null;
		}
		try {
			format.apply(document);
		} catch (MalformedTreeException | BadLocationException e) {
			JavaLanguageServerPlugin.logException(e);
			return null;
		}
		text = document.get();
		text = text.replace(suffix, "");
		text = text.replace(prefix, "");
		int indent = 0;
		while (indent < text.length() && (text.charAt(indent) == ' ' || text.charAt(indent) == '\t')) {
			indent++;
		}
		String[] lines = text.split("\n");
		StringBuilder builder = new StringBuilder();
		for (String line : lines) {
			if (line.length() > 0 && line.length() >= indent && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
				builder.append(line.substring(indent)).append("\n");
			} else {
				builder.append(line).append("\n");
			}
		}
		text = builder.toString();
		return text;
	}

	private List<org.eclipse.lsp4j.TextEdit> format(ICompilationUnit cu, IDocument document, IRegion region, FormattingOptions options, boolean includeComments, IProgressMonitor monitor) {
		if (cu == null || document == null || region == null || monitor.isCanceled()) {
			return Collections.emptyList();
		}

		CodeFormatter formatter = ToolFactory.createCodeFormatter(getOptions(options, cu));

		String lineDelimiter = TextUtilities.getDefaultLineDelimiter(document);
		String sourceToFormat = document.get();
		int kind = getFormattingKind(cu, includeComments);
		TextEdit format = formatter.format(kind, sourceToFormat, region.getOffset(), region.getLength(), 0, lineDelimiter);
		InsertEdit commaIndentationEdit = computeIndentationIfCommaPresent(cu, document, region, options);
		if (commaIndentationEdit != null) {
			format.addChild(commaIndentationEdit);
		}
		if (format == null || format.getChildren().length == 0 || monitor.isCanceled()) {
			// nothing to return
			return Collections.<org.eclipse.lsp4j.TextEdit>emptyList();
		}
		MultiTextEdit flatEdit = TextEditUtil.flatten(format);
		return convertEdits(flatEdit.getChildren(), document);
	}

	private InsertEdit computeIndentationIfCommaPresent(ICompilationUnit cu, IDocument document, IRegion region, FormattingOptions options) {
		int length = region.getLength();
		try {
			if (document.getChar(region.getOffset() + length) == NEW_LINE) {
				for (int i = region.getOffset() + length - 1; i >= region.getOffset(); i--) {
					char lastCharacter = document.getChar(i);
					if (Character.isWhitespace(lastCharacter)) {
						continue;
					} else if (lastCharacter == COMMA) {
						String newText = "";
						int numTabs = Integer.valueOf(getOptions(options, cu).get(DefaultCodeFormatterConstants.FORMATTER_CONTINUATION_INDENTATION));
						if (options.isInsertSpaces()) {
							newText = SPACE.repeat(options.getTabSize() * numTabs);
						} else {
							newText = TAB.repeat(numTabs);
						}
						return new InsertEdit(document.getLineOffset(document.getLineOfOffset(region.getOffset()) + 1), newText);
					} else {
						return null;
					}
				}
			}
			return null;
		} catch (BadLocationException e) {
			return null;
		}
	}

	private int getFormattingKind(ICompilationUnit cu, boolean includeComments) {
		int kind = includeComments ? CodeFormatter.F_INCLUDE_COMMENTS : 0;
		if (cu.getResource() != null && cu.getResource().getName().equals(IModule.MODULE_INFO_JAVA)) {
			kind |= CodeFormatter.K_MODULE_INFO;
		} else {
			kind |= CodeFormatter.K_COMPILATION_UNIT;
		}
		return kind;
	}

	private IRegion getRegion(Range range, IDocument document) {
		try {
			int offset = document.getLineOffset(range.getStart().getLine()) + range.getStart().getCharacter();
			int endOffset = document.getLineOffset(range.getEnd().getLine()) + range.getEnd().getCharacter();
			int length = endOffset - offset;
			return new Region(offset, length);
		} catch (BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return null;
	}

	public static Map<String, String> getOptions(FormattingOptions options, ICompilationUnit cu) {
		Map<String, String> eclipseOptions = cu.getOptions(true);

		Map<String, String> customOptions = options.entrySet().stream().filter(map -> chekIfValueIsNotNull(map.getValue())).collect(toMap(e -> e.getKey(), e -> getOptionValue(e.getValue())));

		eclipseOptions.putAll(customOptions);

		Integer tabSize = options.getTabSize();
		if (tabSize != null) {
			int tSize = tabSize.intValue();
			if (tSize > 0) {
				eclipseOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, Integer.toString(tSize));
			}
		}
		boolean insertSpaces = options.isInsertSpaces();
		eclipseOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, insertSpaces ? JavaCore.SPACE : JavaCore.TAB);
		return eclipseOptions;
	}

	private static boolean chekIfValueIsNotNull(Either3<String, Number, Boolean> value) {
		return value.getFirst() != null || value.getSecond() != null || value.getThird() != null;
	}

	private static String getOptionValue(Either3<String, Number, Boolean> option) {
		if (option.isFirst()) {
			return option.getFirst();
		} else if (option.isSecond()) {
			return option.getSecond().toString();
		} else {
			return option.getThird().toString();
		}
	}

	private static List<org.eclipse.lsp4j.TextEdit> convertEdits(TextEdit[] edits, IDocument document) {
		return Arrays.stream(edits).map(t -> convertEdit(t, document)).collect(Collectors.toList());
	}

	private static org.eclipse.lsp4j.TextEdit convertEdit(TextEdit edit, IDocument document) {
		org.eclipse.lsp4j.TextEdit textEdit = new org.eclipse.lsp4j.TextEdit();
		if (edit instanceof ReplaceEdit replaceEdit) {
			textEdit.setNewText(replaceEdit.getText());
			int offset = edit.getOffset();
			textEdit.setRange(new Range(createPosition(document, offset), createPosition(document, offset + edit.getLength())));
		}
		if (edit instanceof InsertEdit insertEdit) {
			textEdit.setNewText(insertEdit.getText());
			int offset = edit.getOffset();
			textEdit.setRange(new Range(createPosition(document, offset), createPosition(document, offset)));
		}
		return textEdit;
	}

	private static Position createPosition(IDocument document, int offset) {
		Position start = new Position();
		try {
			int lineOfOffset = document.getLineOfOffset(offset);
			start.setLine(Integer.valueOf(lineOfOffset));
			start.setCharacter(Integer.valueOf(offset - document.getLineOffset(lineOfOffset)));
		} catch (BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return start;
	}

	public List<? extends org.eclipse.lsp4j.TextEdit> onTypeFormatting(DocumentOnTypeFormattingParams params, IProgressMonitor monitor) {
		return format(params.getTextDocument().getUri(), params.getOptions(), params.getPosition(), params.getCh(), monitor);
	}

	private List<? extends org.eclipse.lsp4j.TextEdit> format(String uri, FormattingOptions options, Position position, String triggerChar, IProgressMonitor monitor) {
		if (!preferenceManager.getPreferences().isJavaFormatOnTypeEnabled()) {
			return Collections.emptyList();
		}

		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		if (cu == null) {
			return Collections.emptyList();
		}
		IRegion region = null;
		IDocument document = null;
		try {
			document = JsonRpcHelpers.toDocument(cu.getBuffer());
			if (document != null && position != null) {
				region = getRegion(cu, document, position, triggerChar);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		if (region == null) {
			return Collections.emptyList();
		}
		return format(cu, document, region, options, false, monitor);
	}

	private IRegion getRegion(ICompilationUnit cu, IDocument document, Position position, String trigger) {
		try {
			int line = position.getLine();
			int offset = document.getLineOffset(line);
			int length = position.getCharacter();

			char triggerChar = '\u0000';
			if (trigger != null && !trigger.isEmpty()) {
				triggerChar = trigger.charAt(0);
				if (NEW_LINE == triggerChar && (document.getChar(offset + length) != triggerChar || length == 0) && line > 0) {
					int prevLine = line - 1;
					offset = document.getLineOffset(prevLine);
					length = document.getLineLength(prevLine);
					line = prevLine;
				}
			} else {
				triggerChar = document.getChar(offset + length);
			}

			boolean emptyLine = false;
			if (NEW_LINE == triggerChar) {
				// get previous non-whitespace char (up to previous line)
				int[] lines = (line > 0) ? new int[] { line, line - 1 } : new int[] { line };

				lineLoop: for (int l : lines) {
					int lineOffset = document.getLineOffset(l);
					int maxPosition = document.getLineLength(l) - 1;

					emptyLine = false;
					for (int pos = maxPosition; pos >= 0; pos--) {
						char ch = document.getChar(lineOffset + pos);
						if (Character.isWhitespace(ch)) {
							continue;
						}
						if (ch == CLOSING_BRACE) {
							length = pos + 1;
						} else {
							length = maxPosition;
						}
						offset = lineOffset;
						triggerChar = ch;
						break lineLoop;
					}
					emptyLine = true;
				}
			}

			if (triggerChar == CLOSING_BRACE) {
				//Format whole block, from beginning of line to end of last line
				CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
				if (astRoot == null) {
					return null;
				}
				NodeFinder finder = new NodeFinder(astRoot, offset, length);
				ASTNode block = finder.getCoveredNode();
				if (block == null) {
					block = finder.getCoveringNode();
				}
				if (block != null) {
					int blockStartPosition = block.getStartPosition();
					int lineOfBlock = document.getLineOfOffset(blockStartPosition);
					int lineOffset = document.getLineOffset(lineOfBlock);
					int blockLength = block.getLength();

					int endLine = document.getLineOfOffset(blockStartPosition + blockLength);
					int endLineOffset = document.getLineOffset(endLine);
					int endLineLength = document.getLineLength(endLine);
					if (document.getChar(endLineOffset + endLineLength - 1) == NEW_LINE) {
						endLineLength--;
					}
					int lastPosition = document.getLineOffset(endLine) + endLineLength;

					int totalLength = lastPosition - lineOffset;
					return new Region(lineOffset, totalLength);
				}
			} else if (!emptyLine) {
				//format current non-empty line
				return new Region(offset, length);
			}
		} catch (BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return null;
	}

	/**
	 * A utility function to format a String with given formatter options.
	 *
	 * @see org.eclipse.jdt.core.formatter.CodeFormatterApplication#formatFile(File,
	 *      CodeFormatter)
	 * @param content
	 *                    the content to format
	 * @param options
	 *                    the Map includes formatter options, If set to
	 *                    <code>null</code>, then use the default formatter settings.
	 * @param version
	 *                    the version of the formatter options
	 * @param monitor
	 *                    the progress monitor
	 *
	 * @return the formatted String
	 */
	public String stringFormatting(String content, Map<String, String> options, int version, IProgressMonitor monitor) {
		IDocument document = new Document();
		document.set(content);
		Map<String, String> formatOptions = (options == null) ? getCombinedDefaultFormatterSettings() : ProfileVersionerCore.updateAndComplete(options, version);
		CodeFormatter formatter = ToolFactory.createCodeFormatter(formatOptions);
		IRegion region = new Region(0, document.getLength());
		int kind = CodeFormatter.K_COMPILATION_UNIT;
		if (preferenceManager.getPreferences().isJavaFormatComments()) {
			kind = kind | CodeFormatter.F_INCLUDE_COMMENTS;
		}
		TextEdit edit = formatter.format(kind, content, region.getOffset(), region.getLength(), 0, TextUtilities.getDefaultLineDelimiter(document));
		if (edit != null) {
			try {
				edit.apply(document);
			} catch (Exception e) {
				// Do nothing
			}
		}
		return document.get();
	}

	public static Map<String, String> getCombinedDefaultFormatterSettings() {
		Map<String, String> options = DefaultCodeFormatterOptions.getEclipseDefaultSettings().getMap();
		options.putAll(getJavaLSDefaultFormatterSettings());
		return options;
	}

	public static Map<String, String> getJavaLSDefaultFormatterSettings() {
		Map<String, String> options = new HashMap<>();
		options.put(DefaultCodeFormatterConstants.FORMATTER_JOIN_WRAPPED_LINES, DefaultCodeFormatterConstants.FALSE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_JOIN_LINES_IN_COMMENTS, DefaultCodeFormatterConstants.FALSE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_SWITCH, DefaultCodeFormatterConstants.TRUE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_USE_ON_OFF_TAGS, DefaultCodeFormatterConstants.TRUE);
		return options;
	}
}
