/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreJavaDoc2HTMLTextReader;

/**
 * Converts JavaDoc tags into an output format.
 *
 * @author Fred Bricon
 */
abstract class AbstractJavaDocConverter {

	private CoreJavaDoc2HTMLTextReader reader;

	private boolean read;
	private String result;

	public AbstractJavaDocConverter(Reader reader) {
		setJavaDoc2HTMLTextReader(reader);
	}

	public AbstractJavaDocConverter(String javadoc) {
		setJavaDoc2HTMLTextReader(javadoc == null ? null : new StringReader(javadoc));
	}

	private void setJavaDoc2HTMLTextReader(Reader reader) {
		if (reader == null || reader instanceof JdtLsJavaDoc2HTMLTextReader) {
			this.reader = (JdtLsJavaDoc2HTMLTextReader) reader;
		} else {
			this.reader = new JdtLsJavaDoc2HTMLTextReader(reader);
		}
	}

	public String getAsString() throws IOException {
		if (!read && reader != null) {
			String rawHtml = reader.getString();
			result = convert(rawHtml);
		}
		return result;
	}

	abstract String convert(String rawHtml);

	private static class JdtLsJavaDoc2HTMLTextReader extends CoreJavaDoc2HTMLTextReader {
		private int preTagDepth = 0;
		private int codeTagDepth = 0;
		private StringBuilder tagBuffer = new StringBuilder();
		private boolean inTag = false;
		private boolean checkNextChar = false;
		private char quoteChar = 0; // 0 = not in quotes, '"' or '\'' when inside quotes
		private boolean inComment = false;
		private StringBuilder commentBuffer = new StringBuilder();

		public JdtLsJavaDoc2HTMLTextReader(Reader reader) {
			super(reader);
		}

		@Override
		protected String computeSubstitution(int c) throws java.io.IOException {
			// Track HTML comments - everything inside <!-- ... --> should be ignored
			if (inComment) {
				commentBuffer.append((char) c);
				// Check if we've reached the end of comment: -->
				if (commentBuffer.length() >= 3) {
					String last3 = commentBuffer.substring(commentBuffer.length() - 3);
					if ("-->".equals(last3)) {
						inComment = false;
						commentBuffer.setLength(0);
					}
				}
				// Don't process anything inside comments, but pass through to parent
				return super.computeSubstitution(c);
			}

			// Track HTML tags to detect when we're inside <pre> or <code> blocks
			if (checkNextChar) {
				checkNextChar = false;
				// Check for comment start: <!--
				if (c == '!') {
					// Might be start of comment, accumulate to check
					commentBuffer.setLength(0);
					commentBuffer.append("<!");
				} else if (Character.isLetter((char) c) || c == '/') {
					// Only treat < as start of tag if followed by letter or / (for closing tags)
					inTag = true;
					quoteChar = 0;
					tagBuffer.setLength(0);
					tagBuffer.append('<');
					tagBuffer.append((char) c);
				}
			} else if (commentBuffer.length() > 0) {
				// Still checking if this is a comment start
				commentBuffer.append((char) c);
				if ("<!--".equals(commentBuffer.toString())) {
					inComment = true;
				} else if (commentBuffer.length() >= 4 || c == '>') {
					// Not a comment start, clear buffer
					commentBuffer.setLength(0);
				}
			} else if (inTag) {
				tagBuffer.append((char) c);

				// Track quotes to avoid ending tag on > inside attribute values
				if (quoteChar == 0 && (c == '"' || c == '\'')) {
					quoteChar = (char) c;
				} else if (c == quoteChar) {
					quoteChar = 0; // Closing quote
				} else if (c == '>' && quoteChar == 0) {
					// Only end tag if we're not inside quotes
					inTag = false;
					updateTagDepth(tagBuffer.toString().toLowerCase());
				}
				// Don't check for new tags while we're already parsing one
			} else if (c == '<') {
				// Only check for new tag if we're not already inside one
				checkNextChar = true;
			}

			// If we're inside a <pre> or <code> block, don't process @ or { as javadoc tags
			if ((preTagDepth > 0 || codeTagDepth > 0) && (c == '@' || c == '{')) {
				return null; // Return null to use the character as-is
			}

			return super.computeSubstitution(c);
		}

		private void updateTagDepth(String tag) {
			// Handle opening tags (with or without attributes)
			if (tag.startsWith("<pre") && (tag.equals("<pre>") || tag.charAt(4) == ' ')) {
				preTagDepth++;
			} else if (tag.equals("</pre>")) {
				preTagDepth = Math.max(0, preTagDepth - 1);
			} else if (tag.startsWith("<code") && (tag.equals("<code>") || tag.charAt(5) == ' ')) {
				codeTagDepth++;
			} else if (tag.equals("</code>")) {
				codeTagDepth = Math.max(0, codeTagDepth - 1);
			}
		}

		@Override
		protected String getPrintSingleDefinitionStartTags() {
			return "<li>"; //$NON-NLS-1$
		}

		@Override
		protected String getPrintSingleDefinitionEndTags() {
			return "</li>"; //$NON-NLS-1$
		}

		@Override
		protected void print(StringBuilder buffer, String tag, List<String> elements, boolean firstword) {
			if (!elements.isEmpty()) {
				buffer.append("<li><b>"); //$NON-NLS-1$
				buffer.append(tag);
				buffer.append("</b><ul>"); //$NON-NLS-1$
				printDefinitions(buffer, elements, firstword);
				buffer.append("</ul></li>"); //$NON-NLS-1$
			}
		}

		@Override
		protected void print(StringBuilder buffer, String tag, String content) {
			if (content != null) {
				buffer.append("<li><b>"); //$NON-NLS-1$
				buffer.append(tag);
				buffer.append("</b><ul><li>"); //$NON-NLS-1$
				buffer.append(content);
				buffer.append("</li></ul></li>"); //$NON-NLS-1$
			}
		}

		@Override
		protected void printRest(StringBuilder buffer, List<Pair> rest) {
			if (!rest.isEmpty()) {
				Iterator<Pair> e = rest.iterator();
				while (e.hasNext()) {
					Pair p = e.next();
					buffer.append("<li>"); //$NON-NLS-1$
					if (p.fTag() != null) {
						buffer.append(p.fTag());
					}
					if (p.fContent() != null) {
						buffer.append("<ul><li>"); //$NON-NLS-1$
						buffer.append(p.fContent());
						buffer.append("</li></ul>"); //$NON-NLS-1$
					}
					buffer.append("</li>"); //$NON-NLS-1$
				}
			}
		}

		@Override
		protected String printSimpleTag(List<Pair> rest) {
			StringBuilder buffer = new StringBuilder();
			buffer.append("<ul>"); //$NON-NLS-1$
			printTagAttributes(buffer);
			printRest(buffer, rest);
			buffer.append("</ul>"); //$NON-NLS-1$
			return buffer.toString();
		}
	}
}
