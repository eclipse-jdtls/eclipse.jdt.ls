/*******************************************************************************
 * Copyright (c) 2025 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Objects;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.IPreferencesChangeListener;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

public class CodeLensPreferenceChangeListener implements IPreferencesChangeListener {

	@Override
	public void preferencesChange(Preferences oldPreferences, Preferences newPreferences) {
		if (oldPreferences.isReferencesCodeLensEnabled() != newPreferences.isReferencesCodeLensEnabled()
				|| !Objects.equals(oldPreferences.getImplementationsCodeLens(), newPreferences.getImplementationsCodeLens())) {
			refresh();
		}
	}

	private void refresh() {
		if (!JavaLanguageServerPlugin.getPreferencesManager()
				.getClientPreferences().isCodeLensRefreshSupported()) {
			return;
		}
		JavaLanguageServerPlugin.getInstance().getClientConnection().refreshCodeLenses();
	}
}

