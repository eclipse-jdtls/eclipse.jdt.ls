/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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

import org.jsoup.Jsoup;

/**
 * Converts JavaDoc tags into plain text equivalent.
 *
 * @author Fred Bricon
 */
public class JavaDoc2PlainTextConverter extends AbstractJavaDocConverter {

	public JavaDoc2PlainTextConverter(Reader reader) {
		super(reader);
	}

	public JavaDoc2PlainTextConverter(String javadoc) {
		super(javadoc);
	}

	@Override
	String convert(String rawHtml) {
		HtmlToPlainText formatter = new HtmlToPlainText();
		return formatter.getPlainText(Jsoup.parse(rawHtml));
	}
}
