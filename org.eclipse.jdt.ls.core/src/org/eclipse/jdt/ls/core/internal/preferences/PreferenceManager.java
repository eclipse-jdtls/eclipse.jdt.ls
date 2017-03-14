/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.preferences;

import java.util.Hashtable;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;

/**
 * Preference manager
 *
 * @author Gorkem Ercan
 * @author Fred Bricon
 *
 */
public class PreferenceManager {

	private Preferences preferences ;
	private ClientPreferences clientPreferences;
	private IMavenConfiguration mavenConfig;

	public PreferenceManager() {
		preferences = new Preferences();
	}

	/**
	 * Initialize default preference values of used bundles to match server
	 * functionality.
	 */
	public void initialize() {
		// Update JavaCore options
		Hashtable<String, String> javaCoreOptions = JavaCore.getOptions();
		javaCoreOptions.put(JavaCore.CODEASSIST_VISIBILITY_CHECK, JavaCore.ENABLED);
		JavaCore.setOptions(javaCoreOptions);
	}

	public void update(Preferences preferences) {
		if(preferences == null){
			throw new IllegalArgumentException("Preferences can not be null");
		}
		this.preferences = preferences;

		String newMavenSettings = preferences.getMavenUserSettings();
		String oldMavenSettings = getMavenConfiguration().getUserSettingsFile();
		if (!Objects.equals(newMavenSettings, oldMavenSettings)) {
			try {
				getMavenConfiguration().setUserSettingsFile(newMavenSettings);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("failed to set Maven settings", e);
				preferences.setMavenUserSettings(oldMavenSettings);
			}
		}
		// TODO serialize preferences
	}

	public Preferences getPreferences() {
		return preferences;
	}

	public ClientPreferences getClientPreferences() {
		return clientPreferences;
	}

	/**
	 * @param clientCapabilities the clientCapabilities to set
	 */
	public void updateClientPrefences(ClientCapabilities clientCapabilities) {
		this.clientPreferences = new ClientPreferences(clientCapabilities);
	}

	public IMavenConfiguration getMavenConfiguration() {
		if (mavenConfig == null) {
			mavenConfig = MavenPlugin.getMavenConfiguration();
		}
		return mavenConfig;
	}

	/**
	 * public for testing purposes
	 */
	public void setMavenConfiguration(IMavenConfiguration mavenConfig) {
		this.mavenConfig = mavenConfig;
	}
}
