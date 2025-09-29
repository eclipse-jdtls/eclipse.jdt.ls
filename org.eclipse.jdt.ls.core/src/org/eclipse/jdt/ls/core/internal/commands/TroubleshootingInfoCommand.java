/*******************************************************************************
 * Copyright (c) 2025 Castle Ridge Software and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Castle Ridge Software - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.commands;

import java.io.File;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;

/**
 * This command collects some diagnostic info to display to the user in case of issues during project import.
 * The purpose of this info is to display the effective values being used: many times, the configuration passed to
 * the language server is only used as a start, with additional detection and configuration going on inside the
 * tools like maven, etc.
 */
public class TroubleshootingInfoCommand {

	private TroubleshootingInfoCommand() {
	}

	public static final String GET_TROUBLESHOOTING_INFO_COMMAND = "java.getTroubleshootingInfo";

	/**
	 * Return Diagnostic Info
	 */
	public static final TroubleshootingInfo getTroubleshootingInfo() {
		return new TroubleshootingInfo();
	}

	public static final class TroubleshootingInfo {
		public String mavenUserSettings;
		public String mavenGlobalSettings;
		public String gradleJavaHome;
		public String gradleUserHome;
		public String[] activeImporters;

		public TroubleshootingInfo() {
			IMavenConfiguration config = IMavenConfiguration.getWorkspaceConfiguration();
			File userSettings = config.getSettingsLocations().userSettings();
			if (userSettings != null) {
				this.mavenUserSettings = userSettings.toString();
			}
			File globalSettings = config.getSettingsLocations().globalSettings();
			if (globalSettings != null) {
				this.mavenGlobalSettings = config.getSettingsLocations().globalSettings().toString();
			}
			this.activeImporters = ProjectsManager.importers().stream().map(importer -> importer.getClass().getName()).toArray(size -> new String[size]);

			Preferences prefs = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
			this.gradleJavaHome= prefs.getGradleJavaHome();
			this.gradleUserHome= prefs.getGradleUserHome();
		}
	}
}
