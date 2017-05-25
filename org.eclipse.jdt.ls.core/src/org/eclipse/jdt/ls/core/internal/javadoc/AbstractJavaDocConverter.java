/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Converts JavaDoc tags into an output format.
 *
 * @author Fred Bricon
 */
abstract class AbstractJavaDocConverter {

	private JavaDoc2HTMLTextReader reader;

	private boolean read;

	private String result;

	public AbstractJavaDocConverter(Reader reader) {
		setJavaDoc2HTMLTextReader(reader);
	}

	public AbstractJavaDocConverter(String javadoc) {
		setJavaDoc2HTMLTextReader(javadoc == null ? null : new StringReader(javadoc));
	}

	private void setJavaDoc2HTMLTextReader(Reader reader) {
		if (reader == null || reader instanceof JavaDoc2HTMLTextReader) {
			this.reader = (JavaDoc2HTMLTextReader) reader;
		} else {
			this.reader = new JavaDoc2HTMLTextReader(reader);
		}
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
