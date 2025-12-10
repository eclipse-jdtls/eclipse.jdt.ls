/*******************************************************************************
 * Copyright (c) 2021, 2023 Red Hat Inc. and others.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionGuessMethodArgumentsMode;
import org.junit.jupiter.api.Test;

public class PreferencesTest {

	@Test
	public void testSetImportOnDemandThreshold() throws Exception {
		Preferences preferences = new Preferences();
		preferences.setImportOnDemandThreshold(10);
		assertEquals(10, preferences.getImportOnDemandThreshold());

		// Zero will fallback to default
		preferences.setImportOnDemandThreshold(0);
		assertEquals(Preferences.IMPORTS_ONDEMANDTHRESHOLD_DEFAULT, preferences.getImportOnDemandThreshold());

		// Negative will fallback to default
		preferences.setImportOnDemandThreshold(-1);
		assertEquals(Preferences.IMPORTS_ONDEMANDTHRESHOLD_DEFAULT, preferences.getImportOnDemandThreshold());
	}

	@Test
	public void testSetStaticImportOnDemandThreshold() throws Exception {
		Preferences preferences = new Preferences();
		preferences.setStaticImportOnDemandThreshold(10);
		assertEquals(10, preferences.getStaticImportOnDemandThreshold());

		// Zero will fallback to default
		preferences.setStaticImportOnDemandThreshold(0);
		assertEquals(Preferences.IMPORTS_STATIC_ONDEMANDTHRESHOLD_DEFAULT, preferences.getStaticImportOnDemandThreshold());

		// Negative will fallback to default
		preferences.setStaticImportOnDemandThreshold(-1);
		assertEquals(Preferences.IMPORTS_STATIC_ONDEMANDTHRESHOLD_DEFAULT, preferences.getStaticImportOnDemandThreshold());
	}

	@Test
	public void testLegacyCompletionGuessMethodArguments() {
		Map<String, Object> config = new HashMap<>();
		Map<String, Object> inJava = new HashMap<>();
		config.put("java", inJava);
		Map<String, Object> inCompletion = new HashMap<>();
		inJava.put("completion", inCompletion);
		inCompletion.put("guessMethodArguments", Boolean.TRUE);

		Preferences preferences = Preferences.createFrom(config);
		assertEquals(CompletionGuessMethodArgumentsMode.INSERT_BEST_GUESSED_ARGUMENTS, preferences.getGuessMethodArgumentsMode());
	}

	@Test
	public void testPartialUpdatePreservesExistingValues() {
		// Create initial preferences with full configuration
		Map<String, Object> initialConfig = new HashMap<>();
		Map<String, Object> java = new HashMap<>();
		initialConfig.put("java", java);

		Map<String, Object> completion = new HashMap<>();
		java.put("completion", completion);
		completion.put("enabled", true);
		completion.put("overwrite", false);

		java.put("autobuild", Map.of("enabled", true));
		java.put("format", Map.of("enabled", true, "onType", Map.of("enabled", false)));
		java.put("import", Map.of("gradle", Map.of("enabled", true)));

		Preferences initial = Preferences.createFrom(initialConfig);
		assertTrue(initial.isAutobuildEnabled(), "Initial autobuild should be enabled");
		assertTrue(initial.isCompletionEnabled(), "Initial completion should be enabled");
		assertFalse(initial.isCompletionOverwrite(), "Initial completion overwrite should be false");
		assertTrue(initial.isJavaFormatEnabled(), "Initial format should be enabled");
		assertFalse(initial.isJavaFormatOnTypeEnabled(), "Initial format on type should be disabled");
		assertTrue(initial.isImportGradleEnabled(), "Initial Gradle import should be enabled");

		// Now send a partial update that only changes autobuild and completion overwrite
		Map<String, Object> partialConfig = new HashMap<>();
		Map<String, Object> javaPartial = new HashMap<>();
		partialConfig.put("java", javaPartial);

		javaPartial.put("autobuild", Map.of("enabled", false)); // Change this
		Map<String, Object> completionPartial = new HashMap<>();
		javaPartial.put("completion", completionPartial);
		completionPartial.put("overwrite", true); // Change this
		// Note: NOT sending format, import, or completion.enabled

		Preferences updated = Preferences.updateFrom(initial, partialConfig);

		// Verify the updated values changed
		assertFalse(updated.isAutobuildEnabled(), "Updated autobuild should be disabled");
		assertTrue(updated.isCompletionOverwrite(), "Updated completion overwrite should be true");

		// Verify the non-updated values were preserved
		assertTrue(updated.isCompletionEnabled(), "Completion enabled should still be true (preserved)");
		assertTrue(updated.isJavaFormatEnabled(), "Format enabled should still be true (preserved)");
		assertFalse(updated.isJavaFormatOnTypeEnabled(), "Format on type should still be false (preserved)");
		assertTrue(updated.isImportGradleEnabled(), "Gradle import should still be enabled (preserved)");
	}

	@Test
	public void testUpdateFromDoesNotModifyOriginal() {
		// Create initial preferences
		Map<String, Object> initialConfig = new HashMap<>();
		Map<String, Object> java = new HashMap<>();
		initialConfig.put("java", java);
		java.put("autobuild", Map.of("enabled", true));
		java.put("completion", Map.of("enabled", true));

		Preferences original = Preferences.createFrom(initialConfig);
		assertTrue(original.isAutobuildEnabled(), "Original autobuild should be enabled");
		assertTrue(original.isCompletionEnabled(), "Original completion should be enabled");

		// Update with partial config
		Map<String, Object> partialConfig = new HashMap<>();
		Map<String, Object> javaPartial = new HashMap<>();
		partialConfig.put("java", javaPartial);
		javaPartial.put("autobuild", Map.of("enabled", false));

		Preferences updated = Preferences.updateFrom(original, partialConfig);

		// Verify original wasn't modified
		assertTrue(original.isAutobuildEnabled(), "Original autobuild should still be enabled");
		assertTrue(original.isCompletionEnabled(), "Original completion should still be enabled");

		// Verify updated has the changes
		assertFalse(updated.isAutobuildEnabled(), "Updated autobuild should be disabled");
		assertTrue(updated.isCompletionEnabled(), "Updated completion should be enabled (preserved)");
	}

	@Test
	public void testCloneCreatesIndependentCopy() {
		// Create preferences with various settings
		Preferences original = new Preferences();
		original.setAutobuildEnabled(true);
		original.setCompletionEnabled(true);
		original.setJavaFormatEnabled(true);
		original.setImportGradleEnabled(false);
		original.setTabSize(4);
		original.setInsertSpaces(true);

		// Clone it
		Preferences cloned = original.clone();

		// Verify values are the same
		assertEquals(original.isAutobuildEnabled(), cloned.isAutobuildEnabled());
		assertEquals(original.isCompletionEnabled(), cloned.isCompletionEnabled());
		assertEquals(original.isJavaFormatEnabled(), cloned.isJavaFormatEnabled());
		assertEquals(original.isImportGradleEnabled(), cloned.isImportGradleEnabled());
		assertEquals(original.getTabSize(), cloned.getTabSize());
		assertEquals(original.isInsertSpaces(), cloned.isInsertSpaces());

		// Verify it's a different instance
		assertNotSame(original, cloned, "Clone should be a different instance");

		// Verify modifying clone doesn't affect original
		cloned.setAutobuildEnabled(false);
		cloned.setTabSize(2);

		assertTrue(original.isAutobuildEnabled(), "Original autobuild should still be true");
		assertEquals(4, original.getTabSize(), "Original tab size should still be 4");
		assertFalse(cloned.isAutobuildEnabled(), "Cloned autobuild should be false");
		assertEquals(2, cloned.getTabSize(), "Cloned tab size should be 2");
	}

	@Test
	public void testMultiplePartialUpdates() {
		// Start with default preferences
		Preferences prefs = new Preferences();
		assertTrue(prefs.isAutobuildEnabled(), "Default autobuild should be enabled");
		assertTrue(prefs.isCompletionEnabled(), "Default completion should be enabled");
		assertTrue(prefs.isJavaFormatEnabled(), "Default format should be enabled");

		// First partial update: disable autobuild
		Map<String, Object> update1 = new HashMap<>();
		Map<String, Object> java1 = new HashMap<>();
		update1.put("java", java1);
		java1.put("autobuild", Map.of("enabled", false));

		prefs = Preferences.updateFrom(prefs, update1);
		assertFalse(prefs.isAutobuildEnabled(), "Autobuild should be disabled after update 1");
		assertTrue(prefs.isCompletionEnabled(), "Completion should still be enabled after update 1");
		assertTrue(prefs.isJavaFormatEnabled(), "Format should still be enabled after update 1");

		// Second partial update: disable completion
		Map<String, Object> update2 = new HashMap<>();
		Map<String, Object> java2 = new HashMap<>();
		update2.put("java", java2);
		java2.put("completion", Map.of("enabled", false));

		prefs = Preferences.updateFrom(prefs, update2);
		assertFalse(prefs.isAutobuildEnabled(), "Autobuild should still be disabled after update 2");
		assertFalse(prefs.isCompletionEnabled(), "Completion should be disabled after update 2");
		assertTrue(prefs.isJavaFormatEnabled(), "Format should still be enabled after update 2");

		// Third partial update: disable format
		Map<String, Object> update3 = new HashMap<>();
		Map<String, Object> java3 = new HashMap<>();
		update3.put("java", java3);
		java3.put("format", Map.of("enabled", false));

		prefs = Preferences.updateFrom(prefs, update3);
		assertFalse(prefs.isAutobuildEnabled(), "Autobuild should still be disabled after update 3");
		assertFalse(prefs.isCompletionEnabled(), "Completion should still be disabled after update 3");
		assertFalse(prefs.isJavaFormatEnabled(), "Format should be disabled after update 3");
	}

	@Test
	public void testPartialUpdateWithNestedProperties() {
		// Create initial preferences
		Map<String, Object> initialConfig = new HashMap<>();
		Map<String, Object> java = new HashMap<>();
		initialConfig.put("java", java);
		java.put("format", Map.of(
			"enabled", true,
			"onType", Map.of("enabled", true)
		));
		java.put("completion", Map.of(
			"enabled", true,
			"favoriteMembers", Arrays.asList("org.junit.Assert.*", "org.mockito.Mockito.*")
		));

		Preferences initial = Preferences.createFrom(initialConfig);
		assertTrue(initial.isJavaFormatEnabled(), "Initial format should be enabled");
		assertTrue(initial.isJavaFormatOnTypeEnabled(), "Initial format on type should be enabled");
		assertTrue(initial.isCompletionEnabled(), "Initial completion should be enabled");
		assertNotNull(initial.getJavaCompletionFavoriteMembers(), "Initial favorite members should not be null");

		// Partial update: only change format.enabled, leave format.onType untouched
		Map<String, Object> partialConfig = new HashMap<>();
		Map<String, Object> javaPartial = new HashMap<>();
		partialConfig.put("java", javaPartial);
		javaPartial.put("format", Map.of("enabled", false));
		// Note: NOT sending format.onType or completion

		Preferences updated = Preferences.updateFrom(initial, partialConfig);

		// Verify the specific nested property changed
		assertFalse(updated.isJavaFormatEnabled(), "Updated format should be disabled");

		// Verify other nested property was preserved
		assertTrue(updated.isJavaFormatOnTypeEnabled(), "Format on type should still be enabled (preserved)");

		// Verify unrelated properties were preserved
		assertTrue(updated.isCompletionEnabled(), "Completion should still be enabled (preserved)");
		assertNotNull(updated.getJavaCompletionFavoriteMembers(), "Favorite members should still be present (preserved)");
	}

	@Test
	public void testEmptyPartialUpdatePreservesAll() {
		// Create initial preferences
		Map<String, Object> initialConfig = new HashMap<>();
		Map<String, Object> java = new HashMap<>();
		initialConfig.put("java", java);
		java.put("autobuild", Map.of("enabled", false));
		java.put("completion", Map.of("enabled", false));

		Preferences initial = Preferences.createFrom(initialConfig);
		assertFalse(initial.isAutobuildEnabled(), "Initial autobuild should be disabled");
		assertFalse(initial.isCompletionEnabled(), "Initial completion should be disabled");

		// Send empty partial update
		Map<String, Object> emptyConfig = new HashMap<>();

		Preferences updated = Preferences.updateFrom(initial, emptyConfig);

		// Verify everything was preserved
		assertFalse(updated.isAutobuildEnabled(), "Autobuild should still be disabled");
		assertFalse(updated.isCompletionEnabled(), "Completion should still be disabled");
	}

	@Test
	public void testMavenLifecycleMappings() {
		// Create initial preferences
		Map<String, Object> initialConfig = new HashMap<>();
		Map<String, Object> java = new HashMap<>();
		initialConfig.put("java", java);
		Preferences initial = Preferences.createFrom(initialConfig);
		assertNotNull(initial.getMavenLifecycleMappings());
		// Send empty partial update
		Map<String, Object> emptyConfig = new HashMap<>();
		Preferences updated = Preferences.updateFrom(initial, emptyConfig);
		assertNotNull(updated.getMavenLifecycleMappings());
	}

	@Test
	public void testMavenDisableTestClasspathFlag() {
		PreferenceManager preferenceManager = new StandardPreferenceManager();
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		boolean mavenDisableTestClasspathFlag = prefs.getBoolean(StandardPreferenceManager.M2E_DISABLE_TEST_CLASSPATH_FLAG, false);
		try {
			assertFalse(mavenDisableTestClasspathFlag);
			Map<String, Object> configMap = new HashMap<>();
			Map<String, Object> java = new HashMap<>();
			configMap.put("java", java);
			// java.import.maven.disableTestClasspathFlag
			java.put("import", Map.of("maven", Map.of("disableTestClasspathFlag", true)));
			Preferences preferences = Preferences.createFrom(configMap);
			preferenceManager.update(preferences);
			assertTrue(preferences.isMavenDisableTestClasspathFlag());
			mavenDisableTestClasspathFlag = prefs.getBoolean(StandardPreferenceManager.M2E_DISABLE_TEST_CLASSPATH_FLAG, false);
			assertTrue(mavenDisableTestClasspathFlag);
			java = new HashMap<>();
			configMap.put("java", java);
			// java.import.maven.disableTestClasspathFlag
			java.put("import", Map.of("maven", Map.of("disableTestClasspathFlag", false)));
			preferences = Preferences.createFrom(configMap);
			preferenceManager.update(preferences);
			assertFalse(preferences.isMavenDisableTestClasspathFlag());
			mavenDisableTestClasspathFlag = prefs.getBoolean(StandardPreferenceManager.M2E_DISABLE_TEST_CLASSPATH_FLAG, false);
			assertFalse(mavenDisableTestClasspathFlag);
		} finally {
			prefs.putBoolean(StandardPreferenceManager.M2E_DISABLE_TEST_CLASSPATH_FLAG, mavenDisableTestClasspathFlag);
		}
	}

}
