/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.managers;

import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.IPreferencesChangeListener;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

public class InvisibleProjectPreferenceChangeListener implements IPreferencesChangeListener {

	@Override
	public void preferencesChange(Preferences oldPreferences, Preferences newPreferences) {
		if (!Objects.equals(oldPreferences.getInvisibleProjectOutputPath(), newPreferences.getInvisibleProjectOutputPath()) ||
				!Objects.equals(oldPreferences.getInvisibleProjectSourcePaths(), newPreferences.getInvisibleProjectSourcePaths())) {
			for (IJavaProject javaProject : ProjectUtils.getJavaProjects()) {
				try {
					InvisibleProjectImporter.updateSourcePaths(javaProject);
				} catch (CoreException e) {
					JavaLanguageServerPlugin.getProjectsManager().getConnection().showMessage(new MessageParams(MessageType.Error, e.getMessage()));
				}
			}
		}
	}

}
