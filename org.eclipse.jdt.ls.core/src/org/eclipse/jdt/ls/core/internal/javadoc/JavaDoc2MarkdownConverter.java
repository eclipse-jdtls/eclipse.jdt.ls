/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.Reader;
import java.lang.reflect.Field;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;

import com.overzealous.remark.Options;
import com.overzealous.remark.Options.Tables;
import com.overzealous.remark.Remark;

/**
 * Converts JavaDoc tags into Markdown equivalent.
 *
 * @author Fred Bricon
 */
public class JavaDoc2MarkdownConverter extends AbstractJavaDocConverter {

	private static Remark remark;

	static {
		Options options = new Options();
		options.tables = Tables.CONVERT_TO_CODE_BLOCK;
		options.hardwraps = true;
		options.inlineLinks = true;
		options.autoLinks = true;
		options.reverseHtmlSmartPunctuation = true;
		remark = new Remark(options);

		//Stop remark from stripping file and jdt protocols in an href
		try {
			Field cleanerField = Remark.class.getDeclaredField("cleaner");
			cleanerField.setAccessible(true);

			Cleaner c = (Cleaner) cleanerField.get(remark);

			Field whitelistField = Cleaner.class.getDeclaredField("whitelist");
			whitelistField.setAccessible(true);

			Whitelist w = (Whitelist) whitelistField.get(c);

			w.addProtocols("a", "href", "file", "jdt");
			whitelistField.set(whitelistField.get(c), w);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			JavaLanguageServerPlugin.logException("Unable to modify jsoup to include file and jdt protocols", e);
		}
	}

	public JavaDoc2MarkdownConverter(Reader reader) {
		super(reader);
	}

	public JavaDoc2MarkdownConverter(String javadoc) {
		super(javadoc);
	}

	@Override
	String convert(String rawHtml) {
		return remark.convert(rawHtml);
	}
}
