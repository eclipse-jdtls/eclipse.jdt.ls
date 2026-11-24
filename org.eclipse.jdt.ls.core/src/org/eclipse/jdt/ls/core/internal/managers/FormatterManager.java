/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Based on org.eclipse.jdt.internal.ui.preferences.formatter.ProfileStore
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileVersionerCore;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * load/store profiles from/to profilesKey
 */
public class FormatterManager {

	/** The default encoding to use */
	public static final String ENCODING= "UTF-8"; //$NON-NLS-1$

	protected static final String VERSION_KEY_SUFFIX= ".version"; //$NON-NLS-1$

	public static final String CODE_FORMATTER_PROFILE_KIND = "CodeFormatterProfile"; //$NON-NLS-1$

	/**
	 * A SAX event handler to parse the xml format for profiles.
	 */
	private final static class ProfileDefaultHandler extends DefaultHandler {

		private String profileName;
		protected String fName;
		private Map<String, String> fSettings;
		private String fKind;
		private int fVersion;
		private boolean reading = false;

		/**
		 * @param profileName
		 */
		private ProfileDefaultHandler(String profileName) {
			this.profileName = profileName;
			if (this.profileName != null && this.profileName.isBlank()) {
				this.profileName = null;
			}
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			if (qName.equals(XML_NODE_SETTING)) {
				if (reading) {
					final String key = attributes.getValue(XML_ATTRIBUTE_ID);
					final String value = attributes.getValue(XML_ATTRIBUTE_VALUE);
					fSettings.put(key, value);
				}
			} else if (qName.equals(XML_NODE_PROFILE)) {
				fName= attributes.getValue(XML_ATTRIBUTE_NAME);
				if (profileName == null || profileName.equals(fName)) {
					reading = true;
					fKind = attributes.getValue(XML_ATTRIBUTE_PROFILE_KIND);
					if (fKind == null) {
						fKind = CODE_FORMATTER_PROFILE_KIND;
					}
					fSettings = new HashMap<>(200);
					try {
						fVersion = Integer.parseInt(attributes.getValue(XML_ATTRIBUTE_VERSION));
					} catch (NumberFormatException e) {
						fVersion = ProfileVersionerCore.getCurrentVersion();
					}
				}
			}
			else if (qName.equals(XML_NODE_ROOT)) {
				// ignore
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			if (qName.equals(XML_NODE_PROFILE)) {
				if (reading) {
					reading = false;
				}
			}
		}

		private Map<String, String> getSettings() {
			return fSettings;
		}

		public int getVersion() {
			return fVersion;
		}

	}

	/**
	 * Identifiers for the XML file.
	 */
	private final static String XML_NODE_ROOT= "profiles"; //$NON-NLS-1$
	private final static String XML_NODE_PROFILE= "profile"; //$NON-NLS-1$
	private final static String XML_NODE_SETTING= "setting"; //$NON-NLS-1$

	private final static String XML_ATTRIBUTE_ID= "id"; //$NON-NLS-1$
	private final static String XML_ATTRIBUTE_NAME= "name"; //$NON-NLS-1$
	private final static String XML_ATTRIBUTE_PROFILE_KIND= "kind"; //$NON-NLS-1$
	private final static String XML_ATTRIBUTE_VALUE= "value"; //$NON-NLS-1$
	private final static String XML_ATTRIBUTE_VERSION= "version"; //$NON-NLS-1$

	public static final String DEFAULT_GENERATED_PROFILE_NAME = "GeneratedProfile";

	/**
	 * The formatter options most commonly asked about/customized (gathered from formatter-related
	 * issues in vscode-java and eclipse.jdt.ls), in the order they should appear in a generated
	 * settings template - see {@link #generateSettingsTemplate(Map, String)}. Every other available
	 * option still appears in the generated template, just further down and commented out, since
	 * there's no settings UI a user could otherwise discover them through.
	 */
	private static final LinkedHashMap<String, String> PROMOTED_SETTINGS = new LinkedHashMap<>();
	static {
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "Specify the length of the page. Beyond this length, the formatter will try to split the code. Defaults to 120.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_LINE_LENGTH, "Specify the line length for comments. Defaults to 80.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_JOIN_WRAPPED_LINES, "Specify whether the formatter can join wrapped lines or not.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_USE_ON_OFF_TAGS, "Use the disabling and enabling tags defined respectively by the disabling_tag and the enabling_tag options.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_DISABLING_TAG, "Define the tag to put in a comment to disable the formatting. Defaults to @formatter:off.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_ENABLING_TAG, "Define the tag to put in a comment to enable the formatting. Defaults to @formatter:on.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_INDENT_PARAMETER_DESCRIPTION, "Control whether description of Javadoc parameters are indented.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_INDENT_ROOT_TAGS, "Control whether Javadoc root tags are indented.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_ALIGN_TAGS_DESCREIPTIONS_GROUPED, "Control whether descriptions and names in Javadoc root tags should be aligned and grouped by tag type.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_ALIGN_TAGS_NAMES_DESCRIPTIONS, "Control whether names and descriptions in Javadoc root tags should be aligned.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_CLEAR_BLANK_LINES_IN_JAVADOC_COMMENT, "Control whether blank lines are cleared inside Javadoc comments.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_BLANK_LINES_BETWEEN_IMPORT_GROUPS, "Control blank lines between import groups.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_LINE_COMMENT, "Control whether single line comments are formatted.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_BLOCK_COMMENT, "Control whether multiple line comments are formatted.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_JAVADOC_COMMENT, "Control whether Javadoc comments are formatted.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_PARENTHESES_POSITIONS_IN_METHOD_INVOCATION, "Position parentheses in method invocations. Defaults to common_lines.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_KEEP_LOOP_BODY_BLOCK_ON_ONE_LINE, "Control whether a loop body block should be kept on one line.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_KEEP_ANONYMOUS_TYPE_DECLARATION_ON_ONE_LINE, "Control whether an anonymous type declaration should be kept on one line.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_KEEP_TYPE_DECLARATION_ON_ONE_LINE, "Control whether a type declaration should be kept on one line.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_KEEP_METHOD_BODY_ON_ONE_LINE, "Control whether a method body should be kept on one line.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS, "Insert a space after the closing angle bracket in type arguments.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_BRACE_IN_ARRAY_INITIALIZER, "Insert a space after the opening brace in an array initializer.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_BRACE_IN_ARRAY_INITIALIZER, "Insert a space before the closing brace in an array initializer.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK, "Specify the position of a block's opening brace. Defaults to end_of_line.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_ENUM_CONSTANTS, "Specify the wrapping/alignment style for enum constants.");
		PROMOTED_SETTINGS.put(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_PARAMETERS_IN_METHOD_DECLARATION, "Specify the wrapping/alignment style for parameters in a method declaration.");
	}

	public FormatterManager() {
	}

	/**
	 * Read the available profiles from the internal XML file and return them as
	 * collection or <code>null</code> if the file is not a profile file.
	 *
	 * @param file
	 *            The file to read from
	 * @return returns a map of formatter options or <code>null</code>
	 * @throws CoreException
	 */
	public Map<String, String> readSettingsFromFile(File file, String profileName) throws CoreException {
		try (FileInputStream reader = new FileInputStream(file)) {
			return readSettingsFromStream(new InputSource(reader), profileName);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.WARNING, IConstants.PLUGIN_ID, e.getMessage(), e));
		}
	}

	/**
	 * Load profiles from a XML stream and add them to a map or <code>null</code> if the source is not a profile store.
	 * @param inputSource The input stream
	 * @return returns a list of <code>CustomProfile</code> or <code>null</code>
	 * @throws CoreException
	 */
	public static Map<String, String> readSettingsFromStream(InputSource inputSource, String profileName) throws CoreException {
		final ProfileDefaultHandler handler = new ProfileDefaultHandler(profileName);
		try {
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			final SAXParser parser = factory.newSAXParser();
			parser.parse(inputSource, handler);
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.WARNING, IConstants.PLUGIN_ID, e.getMessage(), e));
		}
		int version = handler.getVersion();
		if (handler.getSettings() == null) {
			if (!Objects.equals(profileName, handler.fName)) {
				JavaLanguageServerPlugin.logError("Invalid settings: java.format.settings.profile=" + profileName + ". The '" + profileName + "' profile doesn't exist.");
			} else {
				JavaLanguageServerPlugin.logError("Invalid Formatter settings. Check 'java.format.settings.url' and 'java.format.settings.profile'");
			}
			return Collections.emptyMap();
		}
		if (version == ProfileVersionerCore.getCurrentVersion()) {
			return handler.getSettings();
		}
		return ProfileVersionerCore.updateAndComplete(handler.getSettings(), version);
	}

	/**
	 * Generates a starter {@code java.format.settings.url} XML file from the given settings
	 * (typically {@link org.eclipse.jdt.ls.core.internal.handlers.FormatterHandler#getCombinedDefaultFormatterSettings()}),
	 * readable as-is by {@link #readSettingsFromStream}. There's no settings UI a user could
	 * otherwise discover the ~300 available formatter options through - short of already knowing to
	 * export a profile from desktop Eclipse's own Formatter preferences page - so every option
	 * appears in the generated file: the options most commonly asked about/customized
	 * ({@link #PROMOTED_SETTINGS}) are live and documented with a one-line comment at the top: every
	 * other option appears further down, commented out, with its current value, so a user has
	 * something to search/uncomment instead of a blank reference.
	 *
	 * @param currentSettings the current effective formatter settings to seed the template with
	 * @param profileName the profile name to give the generated profile
	 * @return the generated XML content, in the same format {@link #readSettingsFromStream} parses
	 */
	public static String generateSettingsTemplate(Map<String, String> currentSettings, String profileName) {
		String name = (profileName == null || profileName.isBlank()) ? DEFAULT_GENERATED_PROFILE_NAME : profileName;
		int version = ProfileVersionerCore.getCurrentVersion();
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"").append(ENCODING).append("\"?>\n\n");
		sb.append('<').append(XML_NODE_ROOT).append(' ').append(XML_ATTRIBUTE_VERSION).append("=\"").append(version).append("\">\n");
		sb.append('<').append(XML_NODE_PROFILE)
				.append(' ').append(XML_ATTRIBUTE_PROFILE_KIND).append("=\"").append(CODE_FORMATTER_PROFILE_KIND).append('"')
				.append(' ').append(XML_ATTRIBUTE_NAME).append("=\"").append(escapeXmlAttribute(name)).append('"')
				.append(' ').append(XML_ATTRIBUTE_VERSION).append("=\"").append(version).append("\">\n\n");

		sb.append("\t<!-- The settings below are the ones most commonly customized. Every other available setting appears\n");
		sb.append("\t     further down, commented out with its current value - uncomment and edit any of them to override. -->\n\n");
		for (Map.Entry<String, String> promoted : PROMOTED_SETTINGS.entrySet()) {
			String id = promoted.getKey();
			String value = currentSettings.getOrDefault(id, "");
			sb.append("\t<!-- ").append(escapeXmlComment(promoted.getValue())).append(" -->\n");
			sb.append('\t').append(settingElement(id, value)).append("\n\n");
		}

		// Sorted for scanability - there's no other grouping a user without Eclipse-internals
		// knowledge could use to find a specific option in a list this long.
		Map<String, String> remaining = new TreeMap<>(currentSettings);
		PROMOTED_SETTINGS.keySet().forEach(remaining::remove);
		for (Map.Entry<String, String> entry : remaining.entrySet()) {
			sb.append("\t<!-- ").append(settingElement(entry.getKey(), entry.getValue())).append(" -->\n");
		}

		sb.append('\n').append("</").append(XML_NODE_PROFILE).append(">\n");
		sb.append("</").append(XML_NODE_ROOT).append(">\n");
		return sb.toString();
	}

	private static String settingElement(String id, String value) {
		return '<' + XML_NODE_SETTING + ' ' + XML_ATTRIBUTE_ID + "=\"" + escapeXmlAttribute(id) + '"'
				+ ' ' + XML_ATTRIBUTE_VALUE + "=\"" + escapeXmlAttribute(value) + "\"/>";
	}

	private static String escapeXmlAttribute(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	/**
	 * XML comments can't contain "--" anywhere in their text - defensive, since the descriptions
	 * above are hand-written and currently don't contain it, but this keeps a future edit from
	 * silently producing invalid XML.
	 */
	private static String escapeXmlComment(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("--", "——");
	}

}
