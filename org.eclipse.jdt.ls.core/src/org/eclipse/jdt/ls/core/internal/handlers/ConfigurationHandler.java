/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;

public class ConfigurationHandler {

	private ConfigurationHandler() {}

	/**
	 * Query the format setting (insertSpace and tabSize) for the given uri.
	 * Will return {@code null} if the 'workspace/configuration' request is not supported,
	 * @param uri The target uri to query
	 * @return 	a map stores the setting keys and their values, for any setting key which is not
	 * 			available at the client side, a {@code null} value will be provided.
	 */
	public static Map<String, Object> getFormattingOptions(String uri) {
		if (!JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isWorkspaceConfigurationSupported()) {
			return null;
		};

		List<ConfigurationItem> configurationItems = new ArrayList<>();
		String[] settingKeys = {
			Preferences.JAVA_CONFIGURATION_TABSIZE,
			Preferences.JAVA_CONFIGURATION_INSERTSPACES
		};
		
		ConfigurationItem tabSizeItem = new ConfigurationItem();
		tabSizeItem.setScopeUri(uri);
		tabSizeItem.setSection(settingKeys[0]);
		configurationItems.add(tabSizeItem);
		
		ConfigurationItem insertSpacesItem = new ConfigurationItem();
		insertSpacesItem.setScopeUri(uri);
		insertSpacesItem.setSection(settingKeys[1]);
		configurationItems.add(insertSpacesItem);
	
		ConfigurationParams configurationParams = new ConfigurationParams(configurationItems);
		List<Object> response = JavaLanguageServerPlugin.getInstance().getClientConnection().configuration(configurationParams);

		Map<String, Object> results = new HashMap<>();
		int minLength = Math.min(settingKeys.length, response.size());
		for (int i = 0; i < minLength; i++) {
			results.put(settingKeys[i], response.get(i));
		}
		return results;
	}
}
