/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.ls.core.internal.handlers.FormatterHandler;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class FormatterManagerTest {

	/**
	 * The generated template must be a complete, uncommented-and-commented superset of the input
	 * settings: every promoted option appears live with its current value, and reading the generated
	 * XML back must round-trip those exact values - a regression here would mean a user's generated
	 * starter file doesn't actually reflect their current effective settings.
	 */
	@Test
	public void testGeneratedTemplateRoundTripsPromotedSettings() throws Exception {
		Map<String, String> settings = FormatterHandler.getCombinedDefaultFormatterSettings();
		// Pick a value that's unambiguously different from the real default, so the round trip can't
		// accidentally pass just because the generated value happens to match some other default.
		settings.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "77");
		settings.put(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK, DefaultCodeFormatterConstants.NEXT_LINE);

		String xml = FormatterManager.generateSettingsTemplate(settings, "MyProfile");
		Map<String, String> parsed = FormatterManager.readSettingsFromStream(toInputSource(xml), "MyProfile");

		assertEquals("77", parsed.get(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT));
		assertEquals(DefaultCodeFormatterConstants.NEXT_LINE, parsed.get(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
	}

	/**
	 * Every option not in the promoted list must appear commented out in the generated text (not
	 * just absent) - a user should have something to find and uncomment, not a blank reference, and
	 * since it's commented out it must not come back when the template is read.
	 */
	@Test
	public void testNonPromotedSettingsAreCommentedOutButStillPresentInText() throws Exception {
		Map<String, String> settings = new HashMap<>();
		settings.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "100");
		// Not in PROMOTED_SETTINGS - an arbitrary long-tail option.
		String nonPromotedId = DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_SWITCH;
		settings.put(nonPromotedId, DefaultCodeFormatterConstants.TRUE);

		String xml = FormatterManager.generateSettingsTemplate(settings, "MyProfile");

		assertTrue(xml.contains(nonPromotedId), "The non-promoted option must still be present in the generated text so a user can find and uncomment it");
		assertTrue(xml.contains("<!-- <setting id=\"" + nonPromotedId + "\""), "The non-promoted option must be commented out, not live");

		Map<String, String> parsed = FormatterManager.readSettingsFromStream(toInputSource(xml), "MyProfile");
		assertFalse(parsed.containsKey(nonPromotedId), "A commented-out setting must not be parsed back as an active one");
	}

	/**
	 * Every promoted option must be preceded by its one-line human-readable description - the whole
	 * point of promoting it instead of leaving it in the commented long tail.
	 */
	@Test
	public void testPromotedSettingsHaveDescriptions() {
		Map<String, String> settings = FormatterHandler.getCombinedDefaultFormatterSettings();
		String xml = FormatterManager.generateSettingsTemplate(settings, "MyProfile");

		assertTrue(xml.contains("Specify the length of the page."), "lineSplit's description must appear in the generated text");
		int descriptionIndex = xml.indexOf("Specify the length of the page.");
		int settingIndex = xml.indexOf("id=\"" + DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT + "\"");
		assertTrue(descriptionIndex < settingIndex, "The description must come before the setting it describes");
	}

	@Test
	public void testDefaultProfileNameUsedWhenNoneGiven() throws Exception {
		Map<String, String> settings = FormatterHandler.getCombinedDefaultFormatterSettings();
		String xml = FormatterManager.generateSettingsTemplate(settings, null);
		assertTrue(xml.contains("name=\"" + FormatterManager.DEFAULT_GENERATED_PROFILE_NAME + "\""));
	}

	private static InputSource toInputSource(String xml) {
		return new InputSource(new StringReader(xml));
	}

}
