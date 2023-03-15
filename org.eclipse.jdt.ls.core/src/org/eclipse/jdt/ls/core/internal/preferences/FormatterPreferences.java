/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.preferences;

import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

public class FormatterPreferences {

	// @formatter:off
	// < JDTLS settings, eclipse settings >
	private static Map<String, String> eclipseOptions = Map.ofEntries(
		Map.entry("lineSplit", DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT),
		Map.entry("comment.line.length", DefaultCodeFormatterConstants.FORMATTER_COMMENT_LINE_LENGTH),
		Map.entry("join.wrapped.lines", DefaultCodeFormatterConstants.FORMATTER_JOIN_WRAPPED_LINES),
		Map.entry("use.on.off.tags", DefaultCodeFormatterConstants.FORMATTER_USE_ON_OFF_TAGS),
		Map.entry("disabling.tag", DefaultCodeFormatterConstants.FORMATTER_DISABLING_TAG),
		Map.entry("enabling.tag", DefaultCodeFormatterConstants.FORMATTER_ENABLING_TAG),
		Map.entry("indent.parameter.description", DefaultCodeFormatterConstants.FORMATTER_COMMENT_INDENT_PARAMETER_DESCRIPTION),
		Map.entry("indent.root.tags", DefaultCodeFormatterConstants.FORMATTER_COMMENT_INDENT_ROOT_TAGS),
		Map.entry("align.tags.descriptions.grouped", DefaultCodeFormatterConstants.FORMATTER_COMMENT_ALIGN_TAGS_DESCREIPTIONS_GROUPED),
		Map.entry("align.tags.names.descriptions", DefaultCodeFormatterConstants.FORMATTER_COMMENT_ALIGN_TAGS_NAMES_DESCRIPTIONS),
		Map.entry("clear.blank.lines.in.javadoc.comment", DefaultCodeFormatterConstants.FORMATTER_COMMENT_CLEAR_BLANK_LINES_IN_JAVADOC_COMMENT),
		Map.entry("blank.lines.between.import.groups", DefaultCodeFormatterConstants.FORMATTER_BLANK_LINES_BETWEEN_IMPORT_GROUPS),
		Map.entry("format.line.comments", DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_LINE_COMMENT),
		Map.entry("format.block.comments", DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_BLOCK_COMMENT),
		Map.entry("format.javadoc.comments", DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_JAVADOC_COMMENT),
		Map.entry("keep.loop.body.block.on.one.line", DefaultCodeFormatterConstants.FORMATTER_KEEP_LOOP_BODY_BLOCK_ON_ONE_LINE),
		Map.entry("keep.anonymous.type.declaration.on.one.line", DefaultCodeFormatterConstants.FORMATTER_KEEP_ANONYMOUS_TYPE_DECLARATION_ON_ONE_LINE),
		Map.entry("keep.type.declaration.on.one.line", DefaultCodeFormatterConstants.FORMATTER_KEEP_TYPE_DECLARATION_ON_ONE_LINE),
		Map.entry("keep.method.body.on.one.line", DefaultCodeFormatterConstants.FORMATTER_KEEP_METHOD_BODY_ON_ONE_LINE),
		Map.entry("insert.space.after.closing.angle.bracket.in.type.arguments", DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS),
		Map.entry("insert.space.after.opening.brace.in.array.initializer", DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_BRACE_IN_ARRAY_INITIALIZER),
		Map.entry("insert.space.before.closing.brace.in.array.initializer", DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_BRACE_IN_ARRAY_INITIALIZER),
		Map.entry("brace.position.for.block", DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK),
		Map.entry("alignment.for.enum.constants", DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_ENUM_CONSTANTS),
		Map.entry("alignment.for.parameters.in.method.declaration", DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_PARAMETERS_IN_METHOD_DECLARATION)
	);

	// < JDTLS camelCase value, eclipse underline value>
	private static Map<String, String> valueMap = Map.ofEntries(
		Map.entry("commonLines", "common_lines"),
		Map.entry("separateLinesIfNotEmpty", "separate_lines_if_not_empty"),
		Map.entry("separateLinesIfWrapped", "separate_lines_if_wrapped"),
		Map.entry("separateLines", "separate_lines"),
		Map.entry("preservePositions", "preserve_positions"),
		Map.entry("never", "one_line_never"),
		Map.entry("ifEmpty", "one_line_if_empty"),
		Map.entry("ifSingleItem", "one_line_if_single_item"),
		Map.entry("always", "one_line_always"),
		Map.entry("preserve", "one_line_preserve"),
		Map.entry("doNotInsert", "do not insert"),
		Map.entry("endOfLine", "end_of_line"),
		Map.entry("nextLine", "next_line"),
		Map.entry("nextLineIndented", "next_line_indented"),
		Map.entry("nextLineOnWrap", "next_line_on_wrap")
	);
	// @formatter:on

	/**
	 * Convert known language server formatter options to eclipse formatter
	 * settings.
	 * 
	 * @param lsOptions
	 *                      the given language server formatter options
	 * @return the converted eclipse formatter options
	 */
	public static Map<String, String> toEclipseOptions(Map<String, String> lsOptions) {
		return lsOptions.entrySet().stream().filter(option -> eclipseOptions.containsKey(option.getKey())).collect(Collectors.toMap(option -> eclipseOptions.get(option.getKey()), option -> {
			String value = option.getValue();
			if (valueMap.containsKey(value)) {
				return valueMap.get(value);
			}
			return value;
		}));
	}

	/**
	 * Convert language server formatter alignment value to eclipse formatter
	 * alignment value.
	 * 
	 * @param alignmentValue
	 *                           the given language server formatter alignment value
	 * @return the converted eclipse formatter alignment value
	 */
	public static String getEclipseAlignmentValue(Map<String, Object> alignmentValue) {
		Object forceSplit = alignmentValue.getOrDefault("force.split", Boolean.FALSE);
		Object indentationStyle = alignmentValue.getOrDefault("indentation.style", "indentDefault");
		Object wrappingStyle = alignmentValue.getOrDefault("wrapping.style", "compact");
		if (forceSplit instanceof Boolean forceSplitBoolean && indentationStyle instanceof String indentationStyleString && wrappingStyle instanceof String wrappingStyleString) {
			int indentationStyleInt = 0;
			switch (indentationStyleString) {
				case "indentDefault":
					indentationStyleInt = DefaultCodeFormatterConstants.INDENT_DEFAULT;
					break;
				case "indentOnColumn":
					indentationStyleInt = DefaultCodeFormatterConstants.INDENT_ON_COLUMN;
					break;
				case "indentByOne":
					indentationStyleInt = DefaultCodeFormatterConstants.INDENT_BY_ONE;
					break;
				default:
					return null;
			}
			int wrappingStyleInt = 0;
			switch (wrappingStyleString) {
				case "noSplit":
					wrappingStyleInt = DefaultCodeFormatterConstants.WRAP_NO_SPLIT;
					break;
				case "compact":
					wrappingStyleInt = DefaultCodeFormatterConstants.WRAP_COMPACT;
					break;
				case "compactFirstBreak":
					wrappingStyleInt = DefaultCodeFormatterConstants.WRAP_COMPACT_FIRST_BREAK;
					break;
				case "onePerLine":
					wrappingStyleInt = DefaultCodeFormatterConstants.WRAP_ONE_PER_LINE;
					break;
				case "nextShifted":
					wrappingStyleInt = DefaultCodeFormatterConstants.WRAP_NEXT_SHIFTED;
					break;
				case "nextPerLine":
					wrappingStyleInt = DefaultCodeFormatterConstants.WRAP_NEXT_PER_LINE;
					break;
				default:
					return null;
			}
			return DefaultCodeFormatterConstants.createAlignmentValue(forceSplitBoolean, wrappingStyleInt, indentationStyleInt);
		}
		return null;
	}
}
