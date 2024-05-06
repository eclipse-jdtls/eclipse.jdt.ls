/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.apache.maven.settings.Activation;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Settings;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.m2e.apt.MavenJdtAptPlugin;
import org.eclipse.m2e.apt.preferences.PreferencesConstants;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.embedder.MavenProperties;
import org.eclipse.m2e.core.internal.lifecyclemapping.LifecycleMappingFactory;
import org.eclipse.m2e.core.internal.preferences.MavenConfigurationImpl;
import org.eclipse.m2e.core.internal.preferences.MavenPreferenceConstants;
import org.eclipse.m2e.core.internal.preferences.ProblemSeverity;
import org.eclipse.m2e.core.lifecyclemapping.model.PluginExecutionAction;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Preference manager
 *
 * @author Gorkem Ercan
 * @author Fred Bricon
 *
 */
public class StandardPreferenceManager extends PreferenceManager {
	private static final String JAVALS_PROFILE = "javals.profile";
	public static final String M2E_DISABLE_TEST_CLASSPATH_FLAG = "m2e.disableTestClasspathFlag";
	private static final String M2E_APT_ID = MavenJdtAptPlugin.PLUGIN_ID;
	public static final String MAVEN_MULTI_MODULE_PROJECT_DIRECTORY = "maven.multiModuleProjectDirectory";
	private IMavenConfiguration mavenConfig;

	public StandardPreferenceManager() {
		super();
		initializeMavenPreferences();
	}

	public static void initialize()  {
		PreferenceManager.initialize();
		initializeMavenPreferences();
	}

	public static void initializeMavenPreferences() {

		IEclipsePreferences m2eAptPrefs = DefaultScope.INSTANCE.getNode(M2E_APT_ID);
		if (m2eAptPrefs != null) {
			m2eAptPrefs.put(PreferencesConstants.MODE, "jdt_apt");
		}

		IEclipsePreferences store = InstanceScope.INSTANCE.getNode(IMavenConstants.PLUGIN_ID);
		store.put(MavenPreferenceConstants.P_OUT_OF_DATE_PROJECT_CONFIG_PB, ProblemSeverity.warning.toString());
	}

	@Override
	public void update(Preferences preferences) {
		super.update(preferences);

		boolean updateMavenProjects = false;
		String newMavenSettings = preferences.getMavenUserSettings();
		String oldMavenSettings = getMavenConfiguration().getUserSettingsFile();
		if (!Objects.equals(newMavenSettings, oldMavenSettings)) {
			try {
				getMavenConfiguration().setUserSettingsFile(newMavenSettings);
				updateMavenProjects = true;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("failed to set Maven settings", e);
				preferences.setMavenUserSettings(oldMavenSettings);
			}
		}
		String newMavenGlobalSettings = preferences.getMavenGlobalSettings();
		String oldMavenGlobalSettings = getMavenConfiguration().getGlobalSettingsFile();
		if (!Objects.equals(newMavenGlobalSettings, oldMavenGlobalSettings)) {
			try {
				getMavenConfiguration().setGlobalSettingsFile(newMavenGlobalSettings);
				updateMavenProjects = true;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("failed to set Maven global settings", e);
				preferences.setMavenGlobalSettings(oldMavenGlobalSettings);
			}
		}
		String newMavenLifecycleMappings = preferences.getMavenLifecycleMappings();
		String oldMavenLifecycleMappings = getMavenConfiguration().getWorkspaceLifecycleMappingMetadataFile();
		if (!Objects.equals(newMavenLifecycleMappings, oldMavenLifecycleMappings)) {
			try {
				getMavenConfiguration().setWorkspaceLifecycleMappingMetadataFile(newMavenLifecycleMappings);
				// reloads lifcycle mapping. See org.eclipse.m2e.core.ui.internal.preferences.LifecycleMappingPreferencePage.performOK()
				LifecycleMappingFactory.getWorkspaceMetadata(true);
				updateMavenProjects = true;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("failed to set Maven lifecycle mappings", e);
				preferences.setMavenLifecycleMappings(oldMavenLifecycleMappings);
			}
		}
		try {
			Settings mavenSettings = MavenPlugin.getMaven().getSettings();
			String systemMmpd = System.getProperty(MAVEN_MULTI_MODULE_PROJECT_DIRECTORY);
			IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
			boolean oldDisableTest = prefs.getBoolean(M2E_DISABLE_TEST_CLASSPATH_FLAG, false);
			String oldMultiModuleProjectDirectory = prefs.get(MAVEN_MULTI_MODULE_PROJECT_DIRECTORY, null);
			String multiModuleProjectDirectory = systemMmpd;
			if (multiModuleProjectDirectory == null) {
				if (preferences.getRootPaths() != null) {
					for (IPath path : preferences.getRootPaths()) {
						File f = MavenProperties.computeMultiModuleProjectDirectory(path.toFile());
						if (f != null) {
							try {
								multiModuleProjectDirectory = f.getCanonicalPath();
							} catch (IOException e) {
								multiModuleProjectDirectory = f.getAbsolutePath();
							}
							break;
						}
					}
				}
			}
			updateMavenProjects = updateMavenProjects || !Objects.equals(multiModuleProjectDirectory, oldMultiModuleProjectDirectory) || (oldDisableTest != preferences.isMavenDisableTestClasspathFlag());
			mavenSettings.getProfiles().removeIf(p -> JAVALS_PROFILE.equals(p.getId()));
			if (preferences.isMavenDisableTestClasspathFlag() || multiModuleProjectDirectory != null) {
				Profile profile = new Profile();
				profile.setId(JAVALS_PROFILE);
				Activation activation = new Activation();
				activation.setActiveByDefault(true);
				profile.setActivation(activation);
				profile.getProperties().put(M2E_DISABLE_TEST_CLASSPATH_FLAG, String.valueOf(preferences.isMavenDisableTestClasspathFlag()));
				if (multiModuleProjectDirectory != null) {
					profile.getProperties().put(MAVEN_MULTI_MODULE_PROJECT_DIRECTORY, multiModuleProjectDirectory);
				} else {
					profile.getProperties().remove(MAVEN_MULTI_MODULE_PROJECT_DIRECTORY);
				}
				mavenSettings.addProfile(profile);
				mavenSettings.addActiveProfile(profile.getId());
				prefs.putBoolean(M2E_DISABLE_TEST_CLASSPATH_FLAG, preferences.isMavenDisableTestClasspathFlag());
				if (multiModuleProjectDirectory != null) {
					prefs.put(MAVEN_MULTI_MODULE_PROJECT_DIRECTORY, multiModuleProjectDirectory);
				} else {
					prefs.remove(MAVEN_MULTI_MODULE_PROJECT_DIRECTORY);
				}
				try {
					prefs.flush();
				} catch (BackingStoreException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e);
		}
		String newMavenNotCoveredExecutionSeverity = preferences.getMavenNotCoveredPluginExecutionSeverity();
		String oldMavenNotCoveredExecutionSeverity = getMavenConfiguration().getNotCoveredMojoExecutionSeverity();
		if (!Objects.equals(newMavenNotCoveredExecutionSeverity, oldMavenNotCoveredExecutionSeverity)) {
			try {
				((MavenConfigurationImpl) getMavenConfiguration()).setNotCoveredMojoExecutionSeverity(newMavenNotCoveredExecutionSeverity);
				updateMavenProjects = true;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("failed to set not covered Maven plugin execution severity", e);
			}
		}
		String newMavenDefaultMavenExecutionAction = preferences.getMavenDefaultMojoExecutionAction();
		String oldMavenDefaultMavenExecutionAction = getMavenConfiguration().getDefaultMojoExecutionAction() == null ? null : getMavenConfiguration().getDefaultMojoExecutionAction().name();
		if (!Objects.equals(newMavenDefaultMavenExecutionAction, oldMavenDefaultMavenExecutionAction)) {
			PluginExecutionAction action = PluginExecutionAction.valueOf(newMavenDefaultMavenExecutionAction == null ? "ignore" : newMavenDefaultMavenExecutionAction);
			getMavenConfiguration().setDefaultMojoExecutionAction(action);
			updateMavenProjects = true;
		}
		if (updateMavenProjects) {
			ProjectsManager projectManager = JavaLanguageServerPlugin.getProjectsManager();
			if (projectManager != null) {
				if (projectManager.isBuildFinished()) {
					for (IProject project : ProjectUtils.getAllProjects()) {
						if (ProjectUtils.isMavenProject(project)) {
							projectManager.updateProject(project, true);
						}
					}
				} else {
					boolean hasMavenProjects = false;
					for (IProject project : ProjectUtils.getAllProjects()) {
						if (ProjectUtils.isMavenProject(project)) {
							hasMavenProjects = true;
							break;
						}
					}
					projectManager.setShouldUpdateProjects(hasMavenProjects);
				}
			}
		}
		updateParallelBuild(preferences.getMaxConcurrentBuilds());
		boolean mavenOffline = preferences.isMavenOffline();
		IEclipsePreferences store = DefaultScope.INSTANCE.getNode(IMavenConstants.PLUGIN_ID);
		store.putBoolean(MavenPreferenceConstants.P_OFFLINE, mavenOffline);
	}

	private void updateParallelBuild(int maxConcurrentBuilds) {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();
		if (description.getMaxConcurrentBuilds() == maxConcurrentBuilds) {
			return;
		}

		description.setMaxConcurrentBuilds(maxConcurrentBuilds);
		try {
			workspace.setDescription(description);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problems setting maxConcurrentBuilds from workspace.", e);
		}

		String stringValue = maxConcurrentBuilds != 1 ? Boolean.TRUE.toString() : Boolean.FALSE.toString();
		IEclipsePreferences pref = InstanceScope.INSTANCE.getNode(IMavenConstants.PLUGIN_ID);
		pref.put(MavenPreferenceConstants.P_BUILDER_USE_NULL_SCHEDULING_RULE, stringValue);
		pref = InstanceScope.INSTANCE.getNode(JavaCore.PLUGIN_ID);
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
