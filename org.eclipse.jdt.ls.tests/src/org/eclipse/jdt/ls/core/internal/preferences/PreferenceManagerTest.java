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
package org.eclipse.jdt.ls.core.internal.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PreferenceManagerTest {

	@Mock
	private IMavenConfiguration mavenConfig;

	private PreferenceManager preferenceManager;

	@Before
	public void setUp() {
		preferenceManager = new PreferenceManager();
		preferenceManager.setMavenConfiguration(mavenConfig);
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
		preferenceManager.update(preferences);
		verify(mavenConfig, never()).setUserSettingsFile(anyString());

		//check setting null is allowed
		reset(mavenConfig);
		when(mavenConfig.getUserSettingsFile()).thenReturn(path);
		preferences.setMavenUserSettings(null);
		preferenceManager.update(preferences);
		verify(mavenConfig).setUserSettingsFile(null);
	}

	@Test
	public void testInitialize() throws Exception {
		preferenceManager.initialize();
		assertEquals(JavaCore.ENABLED, JavaCore.getOptions().get(JavaCore.CODEASSIST_VISIBILITY_CHECK));
	}

	@Test
	public void testPreferencesChangeListener() throws Exception {
		preferenceManager.initialize();
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


}
