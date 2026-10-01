/*******************************************************************************
 * Copyright (c) 2016-2025 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.Reader;

/**
 * Converts JavaDoc tags into Markdown equivalent.
 *
 * @author Fred Bricon
 */
public class JavaDoc2MarkdownConverter extends AbstractJavaDocConverter {

	private final HtmlToMarkdownConverter htmlToMarkdownConverter = new HtmlToMarkdownConverter();

	public JavaDoc2MarkdownConverter(Reader reader) {
		super(reader);
	}

	public JavaDoc2MarkdownConverter(String javadoc) {
		super(javadoc);
	}

	@Override
	public String convert(String html) {
		return htmlToMarkdownConverter.convert(html);
	}
}
