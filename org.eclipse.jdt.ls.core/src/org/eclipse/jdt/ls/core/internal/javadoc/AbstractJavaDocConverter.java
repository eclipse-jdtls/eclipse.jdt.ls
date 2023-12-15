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
		if (reader == null || reader instanceof CoreJavaDoc2HTMLTextReader) {
			this.reader = (CoreJavaDoc2HTMLTextReader) reader;
		} else {
			this.reader = createHTMLTextReader(reader);
		}
	}

	private CoreJavaDoc2HTMLTextReader createHTMLTextReader(Reader r) {
		return new CoreJavaDoc2HTMLTextReader(r) {

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
						buffer.append("<ul><li>"); //$NON-NLS-1$
						if (p.fContent() != null) {
							buffer.append(p.fContent());
						}
						buffer.append("</li></ul></li>"); //$NON-NLS-1$
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
		};
	}

	public String getAsString() throws IOException {
		if (!read && reader != null) {
			String rawHtml = reader.getString();
			result = convert(rawHtml);
		}
		return result;
	}

	public Reader getAsReader() throws IOException {
		String m = getAsString();
		return m == null ? null : new StringReader(m);
	}

	abstract String convert(String rawHtml);
}
