/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

public class InlayHintsPreferenceChangeListener implements IPreferencesChangeListener {

    @Override
    public void preferencesChange(Preferences oldPreferences, Preferences newPreferences) {
        if (!Objects.equals(oldPreferences.getInlayHintsParameterMode(), newPreferences.getInlayHintsParameterMode())) {
            refresh();
        }

        if (!Objects.equals(oldPreferences.getInlayHintsExclusionList(), newPreferences.getInlayHintsExclusionList())) {
            InlayHintFilterManager.instance().reset();
            refresh();
        }
    }

    private void refresh() {
        if (!JavaLanguageServerPlugin.getPreferencesManager()
                .getClientPreferences().isInlayHintRefreshSupported()) {
            return;
        }
        JavaLanguageServerPlugin.getInstance().getClientConnection().refreshInlayHints();
    }
}
