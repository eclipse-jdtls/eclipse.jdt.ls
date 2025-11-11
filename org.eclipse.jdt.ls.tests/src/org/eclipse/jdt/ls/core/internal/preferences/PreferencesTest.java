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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionGuessMethodArgumentsMode;
import org.junit.Test;

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
		assertTrue("Initial autobuild should be enabled", initial.isAutobuildEnabled());
		assertTrue("Initial completion should be enabled", initial.isCompletionEnabled());
		assertFalse("Initial completion overwrite should be false", initial.isCompletionOverwrite());
		assertTrue("Initial format should be enabled", initial.isJavaFormatEnabled());
		assertFalse("Initial format on type should be disabled", initial.isJavaFormatOnTypeEnabled());
		assertTrue("Initial Gradle import should be enabled", initial.isImportGradleEnabled());

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
		assertFalse("Updated autobuild should be disabled", updated.isAutobuildEnabled());
		assertTrue("Updated completion overwrite should be true", updated.isCompletionOverwrite());

		// Verify the non-updated values were preserved
		assertTrue("Completion enabled should still be true (preserved)", updated.isCompletionEnabled());
		assertTrue("Format enabled should still be true (preserved)", updated.isJavaFormatEnabled());
		assertFalse("Format on type should still be false (preserved)", updated.isJavaFormatOnTypeEnabled());
		assertTrue("Gradle import should still be enabled (preserved)", updated.isImportGradleEnabled());
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
		assertTrue("Original autobuild should be enabled", original.isAutobuildEnabled());
		assertTrue("Original completion should be enabled", original.isCompletionEnabled());

		// Update with partial config
		Map<String, Object> partialConfig = new HashMap<>();
		Map<String, Object> javaPartial = new HashMap<>();
		partialConfig.put("java", javaPartial);
		javaPartial.put("autobuild", Map.of("enabled", false));

		Preferences updated = Preferences.updateFrom(original, partialConfig);

		// Verify original wasn't modified
		assertTrue("Original autobuild should still be enabled", original.isAutobuildEnabled());
		assertTrue("Original completion should still be enabled", original.isCompletionEnabled());

		// Verify updated has the changes
		assertFalse("Updated autobuild should be disabled", updated.isAutobuildEnabled());
		assertTrue("Updated completion should be enabled (preserved)", updated.isCompletionEnabled());
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
		assertNotSame("Clone should be a different instance", original, cloned);

		// Verify modifying clone doesn't affect original
		cloned.setAutobuildEnabled(false);
		cloned.setTabSize(2);

		assertTrue("Original autobuild should still be true", original.isAutobuildEnabled());
		assertEquals("Original tab size should still be 4", 4, original.getTabSize());
		assertFalse("Cloned autobuild should be false", cloned.isAutobuildEnabled());
		assertEquals("Cloned tab size should be 2", 2, cloned.getTabSize());
	}

	@Test
	public void testMultiplePartialUpdates() {
		// Start with default preferences
		Preferences prefs = new Preferences();
		assertTrue("Default autobuild should be enabled", prefs.isAutobuildEnabled());
		assertTrue("Default completion should be enabled", prefs.isCompletionEnabled());
		assertTrue("Default format should be enabled", prefs.isJavaFormatEnabled());

		// First partial update: disable autobuild
		Map<String, Object> update1 = new HashMap<>();
		Map<String, Object> java1 = new HashMap<>();
		update1.put("java", java1);
		java1.put("autobuild", Map.of("enabled", false));

		prefs = Preferences.updateFrom(prefs, update1);
		assertFalse("Autobuild should be disabled after update 1", prefs.isAutobuildEnabled());
		assertTrue("Completion should still be enabled after update 1", prefs.isCompletionEnabled());
		assertTrue("Format should still be enabled after update 1", prefs.isJavaFormatEnabled());

		// Second partial update: disable completion
		Map<String, Object> update2 = new HashMap<>();
		Map<String, Object> java2 = new HashMap<>();
		update2.put("java", java2);
		java2.put("completion", Map.of("enabled", false));

		prefs = Preferences.updateFrom(prefs, update2);
		assertFalse("Autobuild should still be disabled after update 2", prefs.isAutobuildEnabled());
		assertFalse("Completion should be disabled after update 2", prefs.isCompletionEnabled());
		assertTrue("Format should still be enabled after update 2", prefs.isJavaFormatEnabled());

		// Third partial update: disable format
		Map<String, Object> update3 = new HashMap<>();
		Map<String, Object> java3 = new HashMap<>();
		update3.put("java", java3);
		java3.put("format", Map.of("enabled", false));

		prefs = Preferences.updateFrom(prefs, update3);
		assertFalse("Autobuild should still be disabled after update 3", prefs.isAutobuildEnabled());
		assertFalse("Completion should still be disabled after update 3", prefs.isCompletionEnabled());
		assertFalse("Format should be disabled after update 3", prefs.isJavaFormatEnabled());
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
		assertTrue("Initial format should be enabled", initial.isJavaFormatEnabled());
		assertTrue("Initial format on type should be enabled", initial.isJavaFormatOnTypeEnabled());
		assertTrue("Initial completion should be enabled", initial.isCompletionEnabled());
		assertNotNull("Initial favorite members should not be null", initial.getJavaCompletionFavoriteMembers());

		// Partial update: only change format.enabled, leave format.onType untouched
		Map<String, Object> partialConfig = new HashMap<>();
		Map<String, Object> javaPartial = new HashMap<>();
		partialConfig.put("java", javaPartial);
		javaPartial.put("format", Map.of("enabled", false));
		// Note: NOT sending format.onType or completion

		Preferences updated = Preferences.updateFrom(initial, partialConfig);

		// Verify the specific nested property changed
		assertFalse("Updated format should be disabled", updated.isJavaFormatEnabled());

		// Verify other nested property was preserved
		assertTrue("Format on type should still be enabled (preserved)", updated.isJavaFormatOnTypeEnabled());

		// Verify unrelated properties were preserved
		assertTrue("Completion should still be enabled (preserved)", updated.isCompletionEnabled());
		assertNotNull("Favorite members should still be present (preserved)", updated.getJavaCompletionFavoriteMembers());
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
		assertFalse("Initial autobuild should be disabled", initial.isAutobuildEnabled());
		assertFalse("Initial completion should be disabled", initial.isCompletionEnabled());

		// Send empty partial update
		Map<String, Object> emptyConfig = new HashMap<>();

		Preferences updated = Preferences.updateFrom(initial, emptyConfig);

		// Verify everything was preserved
		assertFalse("Autobuild should still be disabled", updated.isAutobuildEnabled());
		assertFalse("Completion should still be disabled", updated.isCompletionEnabled());
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
