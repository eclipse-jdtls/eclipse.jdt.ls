/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PreferencesTest {

	@Test
	public void testParseNullMap() {
		Map<String, Object> configuration = new HashMap<>();
		Preferences preferences = Preferences.createFrom(configuration);
		assertNull(preferences.getDecompilerConfiguration());
	}

	@Test
	public void testParseMapInstance() {
		Map<String, Object> configuration = new HashMap<>();
		Map<String, Object> decompilerConfiguration = new HashMap<>();
		decompilerConfiguration.put("this", "that");
		configuration.put(Preferences.DECOMPILER_CONFIGURATION_KEY, decompilerConfiguration);

		Preferences preferences = Preferences.createFrom(configuration);
		assertEquals(decompilerConfiguration, preferences.getDecompilerConfiguration());
	}

	@Test
	public void testParseJsonMap() {
		Map<String, Object> configuration = new HashMap<>();
		String json = "{\"this\": \"that\"}";
		configuration.put(Preferences.DECOMPILER_CONFIGURATION_KEY, json);

		Map<String, Object> expectedConfiguration = new HashMap<>();
		expectedConfiguration.put("this", "that");

		Preferences preferences = Preferences.createFrom(configuration);
		assertEquals(expectedConfiguration, preferences.getDecompilerConfiguration());
	}

	@Test
	public void testParseInvalidJsonMap() {
		Map<String, Object> configuration = new HashMap<>();
		String invalidJson = "this is not json";
		configuration.put(Preferences.DECOMPILER_CONFIGURATION_KEY, invalidJson);

		Preferences preferences = Preferences.createFrom(configuration);
		assertNull(preferences.getDecompilerConfiguration());
	}

}
