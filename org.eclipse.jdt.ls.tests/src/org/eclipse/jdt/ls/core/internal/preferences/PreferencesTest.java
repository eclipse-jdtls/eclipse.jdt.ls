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
import org.eclipse.jdt.ls.core.internal.handlers.MapFlattener;
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
		MapFlattener.setValue(config, Preferences.JAVA_COMPLETION_GUESS_METHOD_ARGUMENTS_KEY, Boolean.TRUE);

		Preferences preferences = Preferences.createFrom(config);
		assertEquals(CompletionGuessMethodArgumentsMode.INSERT_BEST_GUESSED_ARGUMENTS, preferences.getGuessMethodArgumentsMode());
	}

	@Test
	public void testPartialUpdatePreservesExistingValues() {
		// Create initial preferences with full configuration
		Map<String, Object> initialConfig = new HashMap<>();
		MapFlattener.setValue(initialConfig, Preferences.COMPLETION_ENABLED_KEY, true);
		MapFlattener.setValue(initialConfig, Preferences.JAVA_COMPLETION_OVERWRITE_KEY, false);
		MapFlattener.setValue(initialConfig, Preferences.AUTOBUILD_ENABLED_KEY, true);
		MapFlattener.setValue(initialConfig, Preferences.JAVA_FORMAT_ENABLED_KEY, true);
		MapFlattener.setValue(initialConfig, Preferences.JAVA_FORMAT_ON_TYPE_ENABLED_KEY, false);
		MapFlattener.setValue(initialConfig, Preferences.IMPORT_GRADLE_ENABLED, true);

		Preferences initial = Preferences.createFrom(initialConfig);
		assertTrue(initial.isAutobuildEnabled(), "Initial autobuild should be enabled");
		assertTrue(initial.isCompletionEnabled(), "Initial completion should be enabled");
		assertFalse(initial.isCompletionOverwrite(), "Initial completion overwrite should be false");
		assertTrue(initial.isJavaFormatEnabled(), "Initial format should be enabled");
		assertFalse(initial.isJavaFormatOnTypeEnabled(), "Initial format on type should be disabled");
		assertTrue(initial.isImportGradleEnabled(), "Initial Gradle import should be enabled");

		// Now send a partial update that only changes autobuild and completion overwrite
		Map<String, Object> partialConfig = new HashMap<>();
		MapFlattener.setValue(partialConfig, Preferences.AUTOBUILD_ENABLED_KEY, false); // Change this
		MapFlattener.setValue(partialConfig, Preferences.JAVA_COMPLETION_OVERWRITE_KEY, true); // Change this
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
		MapFlattener.setValue(initialConfig, Preferences.AUTOBUILD_ENABLED_KEY, true);
		MapFlattener.setValue(initialConfig, Preferences.COMPLETION_ENABLED_KEY, true);

		Preferences original = Preferences.createFrom(initialConfig);
		assertTrue(original.isAutobuildEnabled(), "Original autobuild should be enabled");
		assertTrue(original.isCompletionEnabled(), "Original completion should be enabled");

		// Update with partial config
		Map<String, Object> partialConfig = new HashMap<>();
		MapFlattener.setValue(partialConfig, Preferences.AUTOBUILD_ENABLED_KEY, false);

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
		MapFlattener.setValue(update1, Preferences.AUTOBUILD_ENABLED_KEY, false);

		prefs = Preferences.updateFrom(prefs, update1);
		assertFalse(prefs.isAutobuildEnabled(), "Autobuild should be disabled after update 1");
		assertTrue(prefs.isCompletionEnabled(), "Completion should still be enabled after update 1");
		assertTrue(prefs.isJavaFormatEnabled(), "Format should still be enabled after update 1");

		// Second partial update: disable completion
		Map<String, Object> update2 = new HashMap<>();
		MapFlattener.setValue(update2, Preferences.COMPLETION_ENABLED_KEY, false);

		prefs = Preferences.updateFrom(prefs, update2);
		assertFalse(prefs.isAutobuildEnabled(), "Autobuild should still be disabled after update 2");
		assertFalse(prefs.isCompletionEnabled(), "Completion should be disabled after update 2");
		assertTrue(prefs.isJavaFormatEnabled(), "Format should still be enabled after update 2");

		// Third partial update: disable format
		Map<String, Object> update3 = new HashMap<>();
		MapFlattener.setValue(update3, Preferences.JAVA_FORMAT_ENABLED_KEY, false);

		prefs = Preferences.updateFrom(prefs, update3);
		assertFalse(prefs.isAutobuildEnabled(), "Autobuild should still be disabled after update 3");
		assertFalse(prefs.isCompletionEnabled(), "Completion should still be disabled after update 3");
		assertFalse(prefs.isJavaFormatEnabled(), "Format should be disabled after update 3");
	}

	@Test
	public void testPartialUpdateWithNestedProperties() {
		// Create initial preferences
		Map<String, Object> initialConfig = new HashMap<>();
		MapFlattener.setValue(initialConfig, Preferences.JAVA_FORMAT_ENABLED_KEY, true);
		MapFlattener.setValue(initialConfig, Preferences.JAVA_FORMAT_ON_TYPE_ENABLED_KEY, true);
		MapFlattener.setValue(initialConfig, Preferences.COMPLETION_ENABLED_KEY, true);
		MapFlattener.setValue(initialConfig, Preferences.JAVA_COMPLETION_FAVORITE_MEMBERS_KEY, Arrays.asList("org.junit.Assert.*", "org.mockito.Mockito.*"));

		Preferences initial = Preferences.createFrom(initialConfig);
		assertTrue(initial.isJavaFormatEnabled(), "Initial format should be enabled");
		assertTrue(initial.isJavaFormatOnTypeEnabled(), "Initial format on type should be enabled");
		assertTrue(initial.isCompletionEnabled(), "Initial completion should be enabled");
		assertNotNull(initial.getJavaCompletionFavoriteMembers(), "Initial favorite members should not be null");

		// Partial update: only change format.enabled, leave format.onType untouched
		Map<String, Object> partialConfig = new HashMap<>();
		MapFlattener.setValue(partialConfig, Preferences.JAVA_FORMAT_ENABLED_KEY, false);
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
		MapFlattener.setValue(initialConfig, Preferences.AUTOBUILD_ENABLED_KEY, false);
		MapFlattener.setValue(initialConfig, Preferences.COMPLETION_ENABLED_KEY, false);

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
			// java.import.maven.disableTestClasspathFlag
			MapFlattener.setValue(configMap, Preferences.MAVEN_DISABLE_TEST_CLASSPATH_FLAG, true);
			Preferences preferences = Preferences.createFrom(configMap);
			preferenceManager.update(preferences);
			assertTrue(preferences.isMavenDisableTestClasspathFlag());
			mavenDisableTestClasspathFlag = prefs.getBoolean(StandardPreferenceManager.M2E_DISABLE_TEST_CLASSPATH_FLAG, false);
			assertTrue(mavenDisableTestClasspathFlag);
			configMap = new HashMap<>();
			// java.import.maven.disableTestClasspathFlag
			MapFlattener.setValue(configMap, Preferences.MAVEN_DISABLE_TEST_CLASSPATH_FLAG, false);
			preferences = Preferences.createFrom(configMap);
			preferenceManager.update(preferences);
			assertFalse(preferences.isMavenDisableTestClasspathFlag());
			mavenDisableTestClasspathFlag = prefs.getBoolean(StandardPreferenceManager.M2E_DISABLE_TEST_CLASSPATH_FLAG, false);
			assertFalse(mavenDisableTestClasspathFlag);
		} finally {
			prefs.putBoolean(StandardPreferenceManager.M2E_DISABLE_TEST_CLASSPATH_FLAG, mavenDisableTestClasspathFlag);
		}
	}

	@Test
	public void testSettingsUrlPreservation() {
		String settingsUrl = "file:///path/to/settings.properties";

		// Create initial preferences with java.settings.url set
		Map<String, Object> initialConfig = new HashMap<>();
		MapFlattener.setValue(initialConfig, Preferences.JAVA_SETTINGS_URL, settingsUrl);

		Preferences initial = Preferences.createFrom(initialConfig);
		assertEquals(settingsUrl, initial.getSettingsUrl(), "Initial settings URL should be set");

		// Update with partial config that doesn't include java.settings.url
		Map<String, Object> partialConfig = new HashMap<>();
		MapFlattener.setValue(partialConfig, Preferences.AUTOBUILD_ENABLED_KEY, false);
		// Note: NOT sending java.settings.url

		Preferences updated = Preferences.updateFrom(initial, partialConfig);

		// Verify settings URL was preserved
		assertEquals(settingsUrl, updated.getSettingsUrl(), "Settings URL should be preserved when not in update");
		assertFalse(updated.isAutobuildEnabled(), "Autobuild should be updated");
	}

	@Test
	public void testSettingsUrlExplicitNull() {
		String settingsUrl = "file:///path/to/settings.properties";

		// Create initial preferences with java.settings.url set
		Map<String, Object> initialConfig = new HashMap<>();
		MapFlattener.setValue(initialConfig, Preferences.JAVA_SETTINGS_URL, settingsUrl);

		Preferences initial = Preferences.createFrom(initialConfig);
		assertEquals(settingsUrl, initial.getSettingsUrl(), "Initial settings URL should be set");

		// Update with java.settings.url explicitly set to null
		Map<String, Object> partialConfig = new HashMap<>();
		MapFlattener.setValue(partialConfig, Preferences.JAVA_SETTINGS_URL, null);

		Preferences updated = Preferences.updateFrom(initial, partialConfig);

		// Verify settings URL was set to null
		assertEquals(null, updated.getSettingsUrl(), "Settings URL should be null when explicitly set to null");
	}

	@Test
	public void testSettingsUrlUpdate() {
		String oldSettingsUrl = "file:///path/to/old.properties";
		String newSettingsUrl = "file:///path/to/new.properties";

		// Create initial preferences with java.settings.url set
		Map<String, Object> initialConfig = new HashMap<>();
		MapFlattener.setValue(initialConfig, Preferences.JAVA_SETTINGS_URL, oldSettingsUrl);

		Preferences initial = Preferences.createFrom(initialConfig);
		assertEquals(oldSettingsUrl, initial.getSettingsUrl(), "Initial settings URL should be set");

		// Update with java.settings.url set to a new value
		Map<String, Object> partialConfig = new HashMap<>();
		MapFlattener.setValue(partialConfig, Preferences.JAVA_SETTINGS_URL, newSettingsUrl);

		Preferences updated = Preferences.updateFrom(initial, partialConfig);

		// Verify settings URL was updated
		assertEquals(newSettingsUrl, updated.getSettingsUrl(), "Settings URL should be updated to new value");
	}

	@Test
	public void testSettingsUrlInitialNullThenSet() {
		String settingsUrl = "file:///path/to/settings.properties";

		// Create initial preferences without java.settings.url
		Preferences initial = new Preferences();
		assertEquals(null, initial.getSettingsUrl(), "Initial settings URL should be null");

		// Update with java.settings.url set
		Map<String, Object> partialConfig = new HashMap<>();
		MapFlattener.setValue(partialConfig, Preferences.JAVA_SETTINGS_URL, settingsUrl);

		Preferences updated = Preferences.updateFrom(initial, partialConfig);

		// Verify settings URL was set
		assertEquals(settingsUrl, updated.getSettingsUrl(), "Settings URL should be set");
	}

	@Test
	public void testSettingsUrlSetThenUnset() {
		String settingsUrl = "file:///path/to/settings.properties";

		// Create initial preferences with java.settings.url set
		Map<String, Object> initialConfig = new HashMap<>();
		MapFlattener.setValue(initialConfig, Preferences.JAVA_SETTINGS_URL, settingsUrl);

		Preferences initial = Preferences.createFrom(initialConfig);
		assertEquals(settingsUrl, initial.getSettingsUrl(), "Initial settings URL should be set");

		// First update: set to null
		Map<String, Object> partialConfig1 = new HashMap<>();
		MapFlattener.setValue(partialConfig1, Preferences.JAVA_SETTINGS_URL, null);

		Preferences updated1 = Preferences.updateFrom(initial, partialConfig1);
		assertEquals(null, updated1.getSettingsUrl(), "Settings URL should be null after first update");

		// Second update: don't include java.settings.url (should preserve null)
		Map<String, Object> partialConfig2 = new HashMap<>();
		MapFlattener.setValue(partialConfig2, Preferences.AUTOBUILD_ENABLED_KEY, false);

		Preferences updated2 = Preferences.updateFrom(updated1, partialConfig2);
		assertEquals(null, updated2.getSettingsUrl(), "Settings URL should remain null when not in update");
	}

}
