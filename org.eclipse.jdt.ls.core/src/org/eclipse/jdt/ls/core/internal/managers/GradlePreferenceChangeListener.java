/*******************************************************************************
 * Copyright (c) 2020-2022 Red Hat Inc. and others.
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
import java.util.Objects;

import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.WrapperGradleDistribution;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.ProjectConfiguration;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.apt.core.util.AptConfig;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
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

			boolean protobufSupportChanged = !Objects.equals(oldPreferences.isProtobufSupportEnabled(), newPreferences.isProtobufSupportEnabled());
			if (protobufSupportChanged) {
				for (IProject project : ProjectUtils.getGradleProjects()) {
					projectsManager.updateProject(project, true);
				}
			}

			boolean androidSupportChanged = !Objects.equals(oldPreferences.isAndroidSupportEnabled(), newPreferences.isAndroidSupportEnabled());
			if (androidSupportChanged) {
				for (IProject project : ProjectUtils.getGradleProjects()) {
					projectsManager.updateProject(project, true);
				}
			}

			boolean annotationProcessingChanged = !Objects.equals(oldPreferences.isGradleAnnotationProcessingEnabled(), newPreferences.isGradleAnnotationProcessingEnabled());
			if (annotationProcessingChanged) {
				if (newPreferences.isGradleAnnotationProcessingEnabled()) {
					GradleUtils.synchronizeAnnotationProcessingConfiguration(new NullProgressMonitor());
				} else {
					for (IProject project : ProjectUtils.getGradleProjects()) {
						IJavaProject javaProject = JavaCore.create(project);
						if (javaProject != null) {
							AptConfig.setEnabled(javaProject, false);
						}
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
			WrapperValidator.putSha256(newPreferences.getGradleWrapperList());
			return true;
		}
		return false;
	}

}
