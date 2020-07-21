/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.WrapperGradleDistribution;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.ProjectConfiguration;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.IPreferencesChangeListener;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.internal.gradle.checksums.ValidationResult;
import org.eclipse.jdt.ls.internal.gradle.checksums.WrapperValidator;

/**
 * @author snjeza
 *
 */
public class GradlePreferenceChangeListener implements IPreferencesChangeListener {
	@Override
	public void preferencesChange(Preferences oldPreferences, Preferences newPreferences) {
		ProjectsManager projectsManager = JavaLanguageServerPlugin.getProjectsManager();
		if (projectsManager != null) {
			boolean gradleJavaHomeChanged = !Objects.equals(oldPreferences.getGradleJavaHome(), newPreferences.getGradleJavaHome());
			if (gradleJavaHomeChanged || hasAllowedChecksumsChanged(oldPreferences, newPreferences)) {
				for (IProject project : ProjectUtils.getGradleProjects()) {
					if (newPreferences.isGradleWrapperEnabled() || gradleJavaHomeChanged) {
						updateProject(projectsManager, project, gradleJavaHomeChanged);
					}
				}
			}
		}
	}

	private void updateProject(ProjectsManager projectsManager, IProject project, boolean gradleJavaHomeChanged) {
		String projectDir = project.getLocation().toFile().getAbsolutePath();
		Path projectPath = Paths.get(projectDir);
		if (gradleJavaHomeChanged || Files.exists(projectPath.resolve("gradlew"))) {
			ProjectConfiguration configuration = CorePlugin.configurationManager().loadProjectConfiguration(project);
			GradleDistribution distribution = configuration.getBuildConfiguration().getGradleDistribution();
			if (gradleJavaHomeChanged || !(distribution instanceof WrapperGradleDistribution)) {
				projectsManager.updateProject(project, true);
			} else {
				try {
					ValidationResult result = new WrapperValidator().checkWrapper(projectDir);
					if (!result.isValid()) {
						projectsManager.updateProject(project, true);
					}
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
			}
		}
	}

	private boolean hasAllowedChecksumsChanged(Preferences oldPreferences, Preferences newPreferences) {
		if (!Objects.equals(oldPreferences.getGradleWrapperList(), newPreferences.getGradleWrapperList())) {
			putSha256(newPreferences.getGradleWrapperList());
			return true;
		}
		return false;
	}

	private void putSha256(List<?> gradleWrapperList) {
		List<String> sha256Allowed = new ArrayList<>();
		List<String> sha256Disallowed = new ArrayList<>();
		for (Object object : gradleWrapperList) {
			if (object instanceof Map) {
				Map<?, ?> map = (Map<?, ?>) object;
				final ChecksumWrapper sha256 = this.new ChecksumWrapper();
				sha256.allowed = true;
				map.forEach((k, v) -> {
					if (k instanceof String) {
						switch ((String) k) {
							case "sha256":
								if (v instanceof String) {
									sha256.checksum = (String) v;
								}
								break;
							case "allowed":
								if (v instanceof Boolean) {
									sha256.allowed = (Boolean) v;
								}
								break;
							default:
								break;
						}
					}
				});
				if (sha256.checksum != null) {
					if (sha256.allowed) {
						sha256Allowed.add(sha256.checksum);
					} else {
						sha256Disallowed.add(sha256.checksum);
					}
				}
			}
		}
		WrapperValidator.clear();
		if (sha256Allowed != null) {
			WrapperValidator.allow(sha256Allowed);
		}
		if (sha256Disallowed != null) {
			WrapperValidator.disallow(sha256Disallowed);
		}
	}

	class ChecksumWrapper {
		private String checksum;
		private boolean allowed;
	}
}
