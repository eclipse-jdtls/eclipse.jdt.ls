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
package org.eclipse.jdt.ls.core.internal.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.maven.settings.Settings;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.preferences.MavenPreferenceConstants;
import org.eclipse.m2e.core.lifecyclemapping.model.PluginExecutionAction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;

@RunWith(MockitoJUnitRunner.class)
public class PreferenceManagerTest {

	@Mock
	private IMavenConfiguration mavenConfig;

	private StandardPreferenceManager preferenceManager;

	@Before
	public void setUp() {
		preferenceManager = new StandardPreferenceManager();
		preferenceManager.setMavenConfiguration(mavenConfig);
		when(mavenConfig.getNotCoveredMojoExecutionSeverity()).thenReturn("ignore");
	}

	@Test
	public void testUpdateMavenSettings() throws Exception {
		String path = "/foo/bar.xml";
		Preferences preferences = Preferences.createFrom(Collections.singletonMap(Preferences.MAVEN_USER_SETTINGS_KEY, path));
		preferenceManager.update(preferences);
		verify(mavenConfig).setUserSettingsFile(path);


		//check setting the same path doesn't call Maven's config update
		reset(mavenConfig);
		when(mavenConfig.getUserSettingsFile()).thenReturn(path);
		when(mavenConfig.getNotCoveredMojoExecutionSeverity()).thenReturn("ignore");
		when(mavenConfig.getDefaultMojoExecutionAction()).thenReturn(PluginExecutionAction.ignore);
		preferenceManager.update(preferences);
		verify(mavenConfig, never()).setUserSettingsFile(anyString());

		//check setting null is allowed
		reset(mavenConfig);
		when(mavenConfig.getUserSettingsFile()).thenReturn(path);
		when(mavenConfig.getNotCoveredMojoExecutionSeverity()).thenReturn("ignore");
		preferences.setMavenUserSettings(null);
		preferenceManager.update(preferences);
		verify(mavenConfig).setUserSettingsFile(null);
	}

	@Test
	public void testUpdateMavenGlobalSettings() throws Exception {
		String path = "/foo/bar.xml";
		Preferences preferences = Preferences.createFrom(Collections.singletonMap(Preferences.MAVEN_GLOBAL_SETTINGS_KEY, path));
		preferenceManager.update(preferences);
		verify(mavenConfig).setGlobalSettingsFile(path);

		//check setting the same path doesn't call Maven's config update
		reset(mavenConfig);
		when(mavenConfig.getGlobalSettingsFile()).thenReturn(path);
		when(mavenConfig.getNotCoveredMojoExecutionSeverity()).thenReturn("ignore");
		preferenceManager.update(preferences);
		verify(mavenConfig, never()).setGlobalSettingsFile(anyString());

		//check setting null is allowed
		reset(mavenConfig);
		when(mavenConfig.getGlobalSettingsFile()).thenReturn(path);
		when(mavenConfig.getNotCoveredMojoExecutionSeverity()).thenReturn("ignore");
		preferences.setMavenGlobalSettings(null);
		preferenceManager.update(preferences);
		verify(mavenConfig).setGlobalSettingsFile(null);
	}

	@Test
	public void testUpdateMavenLifecycleMappings() throws Exception {
		String path = "/foo/bar.xml";
		Preferences preferences = Preferences.createFrom(Collections.singletonMap(Preferences.MAVEN_LIFECYCLE_MAPPINGS_KEY, path));
		preferenceManager.update(preferences);
		verify(mavenConfig).setWorkspaceLifecycleMappingMetadataFile(path);

		//check setting the same path doesn't call Maven's config update
		reset(mavenConfig);
		when(mavenConfig.getWorkspaceLifecycleMappingMetadataFile()).thenReturn(path);
		when(mavenConfig.getNotCoveredMojoExecutionSeverity()).thenReturn("ignore");
		preferenceManager.update(preferences);
		verify(mavenConfig, never()).setGlobalSettingsFile(anyString());

		//check setting null is allowed
		reset(mavenConfig);
		Mockito.lenient().when(mavenConfig.getWorkspaceLifecycleMappingMetadataFile()).thenReturn(path);
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(IMavenConstants.PLUGIN_ID);
		String oldMappings = prefs.get(MavenPreferenceConstants.P_WORKSPACE_MAPPINGS_LOCATION, null);
		try {
			prefs.put(MavenPreferenceConstants.P_WORKSPACE_MAPPINGS_LOCATION, path);
			when(mavenConfig.getNotCoveredMojoExecutionSeverity()).thenReturn("ignore");
			String defaultPath = null;
			Bundle bundle = Platform.getBundle("org.eclipse.m2e.core");
			if (bundle != null) {
				IPath stateLocation = Platform.getStateLocation(bundle);
				defaultPath = stateLocation.append(Preferences.LIFECYCLE_MAPPING_METADATA_SOURCE_NAME).toString();
			}
			preferences.setMavenLifecycleMappings(null);
			preferenceManager.update(preferences);
			verify(mavenConfig).setWorkspaceLifecycleMappingMetadataFile(defaultPath);
		} finally {
			if (oldMappings == null) {
				prefs.remove(MavenPreferenceConstants.P_WORKSPACE_MAPPINGS_LOCATION);
			} else {
				prefs.put(MavenPreferenceConstants.P_WORKSPACE_MAPPINGS_LOCATION, oldMappings);
			}
		}
	}

	@Test
	public void testInitialize() throws Exception {
		PreferenceManager.initialize();
		assertEquals(JavaCore.ENABLED, JavaCore.getOptions().get(JavaCore.CODEASSIST_VISIBILITY_CHECK));
		assertEquals(JavaCore.IGNORE, JavaCore.getOptions().get(JavaCore.COMPILER_PB_UNHANDLED_WARNING_TOKEN));
	}

	@Test
	public void testPreferencesChangeListener() throws Exception {
		PreferenceManager.initialize();
		boolean called[] = new boolean[1];
		called[0] = false;
		IPreferencesChangeListener listener = new IPreferencesChangeListener() {

			@Override
			public void preferencesChange(Preferences oldPreferences, Preferences newPreferences) {
				called[0] = true;
			}
		};
		preferenceManager.addPreferencesChangeListener(listener);
		Preferences preferences = new Preferences();
		preferenceManager.update(preferences);
		assertTrue("No one listener has been called", called[0]);
		preferenceManager.removePreferencesChangeListener(listener);
		called[0] = false;
		preferences = new Preferences();
		preferenceManager.update(preferences);
		assertFalse("A listener has been called", called[0]);
	}

	@Test
	public void testUpdateFileHeaderTemplate() {
		PreferenceManager.initialize();

		Template template = JavaManipulation.getCodeTemplateStore().findTemplateById(CodeTemplateContextType.FILECOMMENT_ID);
		assertNotNull(template);
		assertEquals("", template.getPattern());

		Preferences preferences = new Preferences();
		preferences.setFileHeaderTemplate(Arrays.asList("/** */"));
		preferenceManager.update(preferences);

		template = JavaManipulation.getCodeTemplateStore().findTemplateById(CodeTemplateContextType.FILECOMMENT_ID);
		assertNotNull(template);
		assertEquals("/** */", template.getPattern());
	}

	@Test
	public void testUpdateTypeCommentTemplate() {
		PreferenceManager.initialize();

		Template template = JavaManipulation.getCodeTemplateStore().findTemplateById(CodeTemplateContextType.TYPECOMMENT_ID);
		assertNotNull(template);
		assertEquals(CodeTemplatePreferences.CODETEMPLATE_TYPECOMMENT_DEFAULT, template.getPattern());

		Preferences preferences = new Preferences();
		preferences.setTypeCommentTemplate(Arrays.asList("/** */"));
		preferenceManager.update(preferences);

		template = JavaManipulation.getCodeTemplateStore().findTemplateById(CodeTemplateContextType.TYPECOMMENT_ID);
		assertNotNull(template);
		assertEquals("/** */", template.getPattern());
	}

	@Test
	public void testUpdateNewTypeTemplate() {
		PreferenceManager.initialize();

		Template template = JavaManipulation.getCodeTemplateStore().findTemplateById(CodeTemplateContextType.NEWTYPE_ID);
		assertNotNull(template);
		assertEquals(CodeTemplatePreferences.CODETEMPLATE_NEWTYPE_DEFAULT, template.getPattern());
	}

	@Test
	public void testInsertSpacesTabSize() {
		PreferenceManager.initialize();
		Preferences preferences = new Preferences();
		preferenceManager.update(preferences);
		assertTrue(preferenceManager.getPreferences().isInsertSpaces());
		assertEquals(4, preferenceManager.getPreferences().getTabSize());
		Map<String, String> eclipseOptions = JavaCore.getOptions();
		String tabSize = eclipseOptions.get(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE);
		assertEquals(4, Integer.valueOf(tabSize).intValue());
		String insertSpaces = eclipseOptions.get(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR);
		assertEquals(JavaCore.SPACE, insertSpaces);
	}

	@Test
	public void testMavenOffline() {
		IEclipsePreferences store = DefaultScope.INSTANCE.getNode(IMavenConstants.PLUGIN_ID);

		try {
			PreferenceManager.initialize();
			Preferences preferences = new Preferences();
			preferenceManager.update(preferences);
			assertFalse(preferenceManager.getPreferences().isMavenOffline());
			assertFalse(store.getBoolean(MavenPreferenceConstants.P_OFFLINE, false));
			preferences.setMavenOffline(true);
			preferenceManager.update(preferences);
			assertTrue(preferenceManager.getPreferences().isMavenOffline());
			assertTrue(store.getBoolean(MavenPreferenceConstants.P_OFFLINE, false));
		} finally {
			Preferences preferences = new Preferences();
			preferenceManager.update(preferences);
			assertFalse(store.getBoolean(MavenPreferenceConstants.P_OFFLINE, false));
		}
	}

	@Test
	public void testMavenDisableTestFlag() throws Exception {
		try {
			PreferenceManager.initialize();
			Preferences preferences = new Preferences();
			preferenceManager.update(preferences);
			assertFalse(preferenceManager.getPreferences().isMavenDisableTestClasspathFlag());
			assertFalse(getDisableTestFlag());
			preferences.setMavenDisableTestClasspathFlag(true);
			preferenceManager.update(preferences);
			assertTrue(preferenceManager.getPreferences().isMavenDisableTestClasspathFlag());
			assertTrue(getDisableTestFlag());
		} finally {
			Preferences preferences = new Preferences();
			preferenceManager.update(preferences);
			assertFalse(preferenceManager.getPreferences().isMavenDisableTestClasspathFlag());
			assertFalse(getDisableTestFlag());
		}
	}

	@Test
	public void testMavenMultipleModuleProjectDirectory() throws Exception {
		try {
			PreferenceManager.initialize();
			Preferences preferences = new Preferences();
			preferenceManager.update(preferences);
			assertNull(getMultipleModuleProjectDirectory());
			preferences = new Preferences();
			Collection<IPath> rootPaths = new ArrayList<>();
			File dir = new File("target", "workingProjects");
			dir = new File(dir, "test");
			File dotMvn = new File(dir, ".mvn");
			FileUtils.forceMkdir(dotMvn);
			IPath rootPath = new org.eclipse.core.runtime.Path(dir.getAbsolutePath());
			rootPaths.add(rootPath);
			preferences.setRootPaths(rootPaths);
			preferenceManager.update(preferences);
			assertEquals(dir.getAbsolutePath(), getMultipleModuleProjectDirectory());
		} finally {
			Preferences preferences = new Preferences();
			preferenceManager.update(preferences);
			assertNull(getMultipleModuleProjectDirectory());
		}
	}

	private String getMultipleModuleProjectDirectory() throws CoreException {
		Settings mavenSettings = MavenPlugin.getMaven().getSettings();
		String multipleModuleProjectDirectory = null;
		List<String> activeProfilesIds = mavenSettings.getActiveProfiles();
		for (org.apache.maven.settings.Profile settingsProfile : mavenSettings.getProfiles()) {
			if ((settingsProfile.getActivation() != null && settingsProfile.getActivation().isActiveByDefault()) || activeProfilesIds.contains(settingsProfile.getId())) {
				if (settingsProfile.getProperties().get(StandardPreferenceManager.MAVEN_MULTI_MODULE_PROJECT_DIRECTORY) instanceof String s) {
					multipleModuleProjectDirectory = s;
					break;
				}
			}
		}
		return multipleModuleProjectDirectory;
	}

	private boolean getDisableTestFlag() throws CoreException {
		Settings mavenSettings = MavenPlugin.getMaven().getSettings();
		boolean disableTest = false;
		List<String> activeProfilesIds = mavenSettings.getActiveProfiles();
		for (org.apache.maven.settings.Profile settingsProfile : mavenSettings.getProfiles()) {
			if ((settingsProfile.getActivation() != null && settingsProfile.getActivation().isActiveByDefault()) || activeProfilesIds.contains(settingsProfile.getId())) {
				if ("true".equals(settingsProfile.getProperties().get(StandardPreferenceManager.M2E_DISABLE_TEST_CLASSPATH_FLAG))) {
					disableTest = true;
					break;
				}
			}
		}
		return disableTest;
	}

	@Test
	public void testMavenDefaultMojoExecution() throws Exception {
		try {
			PreferenceManager.initialize();
			Preferences preferences = new Preferences();
			preferenceManager.update(preferences);
			assertEquals("ignore", preferenceManager.getPreferences().getMavenDefaultMojoExecutionAction());
			preferences.setMavenDefaultMojoExecutionAction("warn");
			preferenceManager.update(preferences);
			assertEquals("warn", preferenceManager.getPreferences().getMavenDefaultMojoExecutionAction());
			preferences.setMavenDefaultMojoExecutionAction("unknown");
			preferenceManager.update(preferences);
			assertEquals("ignore", preferenceManager.getPreferences().getMavenDefaultMojoExecutionAction());
		} finally {
			Preferences preferences = new Preferences();
			preferenceManager.update(preferences);
			assertEquals("ignore", preferenceManager.getPreferences().getMavenDefaultMojoExecutionAction());
		}
	}

	@Test
	public void testTelemetrySettings() throws Exception {
		try {
			PreferenceManager.initialize();
			Preferences preferences = new Preferences(); // default is disabled
			preferenceManager.update(preferences);
			assertEquals(false, preferenceManager.getPreferences().isTelemetryEnabled());
			preferences.setTelemetryEnabled(true);
			preferenceManager.update(preferences);
			assertEquals(true, preferenceManager.getPreferences().isTelemetryEnabled());
		} finally {
			Preferences preferences = new Preferences();
			preferenceManager.update(preferences);
			assertEquals(false, preferenceManager.getPreferences().isTelemetryEnabled());
		}
	}

}
