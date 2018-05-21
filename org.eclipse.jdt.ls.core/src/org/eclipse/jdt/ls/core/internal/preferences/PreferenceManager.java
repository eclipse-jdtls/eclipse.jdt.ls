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
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.StatusFactory;
import org.eclipse.jdt.ls.core.internal.corext.codemanipulation.CodeGenerationSettings;
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
	private static final String M2E_APT_ID = "org.jboss.tools.maven.apt";
	private ClientPreferences clientPreferences;
	private IMavenConfiguration mavenConfig;
	private ListenerList<IPreferencesChangeListener> preferencesChangeListeners;
	private IEclipsePreferences eclipsePrefs;

	public PreferenceManager() {
		preferences = new Preferences();
		preferencesChangeListeners = new ListenerList<>();
		eclipsePrefs = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		initialize();
	}

	/**
	 * Initialize default preference values of used bundles to match server
	 * functionality.
	 */
	public static void initialize() {
		// Update JavaCore options
		Hashtable<String, String> javaCoreOptions = JavaCore.getOptions();
		javaCoreOptions.put(JavaCore.CODEASSIST_VISIBILITY_CHECK, JavaCore.ENABLED);
		javaCoreOptions.put(DefaultCodeFormatterConstants.FORMATTER_USE_ON_OFF_TAGS, DefaultCodeFormatterConstants.TRUE);
		JavaCore.setOptions(javaCoreOptions);

		// Initialize default preferences
		IEclipsePreferences defEclipsePrefs = DefaultScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		defEclipsePrefs.put("org.eclipse.jdt.ui.typefilter.enabled", "");
		defEclipsePrefs.put(CodeStyleConfiguration.ORGIMPORTS_IMPORTORDER, String.join(";", Preferences.JAVA_IMPORT_ORDER_DEFAULT));
		defEclipsePrefs.put(CodeStyleConfiguration.ORGIMPORTS_ONDEMANDTHRESHOLD, "99");
		defEclipsePrefs.put(CodeStyleConfiguration.ORGIMPORTS_STATIC_ONDEMANDTHRESHOLD, "99");

		IEclipsePreferences m2eAptPrefs = DefaultScope.INSTANCE.getNode(M2E_APT_ID);
		if (m2eAptPrefs != null) {
			m2eAptPrefs.put(M2E_APT_ID + ".mode", "jdt_apt");
		}
	}

	public void update(Preferences preferences) {
		if(preferences == null){
			throw new IllegalArgumentException("Preferences can not be null");
		}
		preferencesChanged(this.preferences, preferences);
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

	private void preferencesChanged(Preferences oldPreferences, Preferences newPreferences) {
		for (final IPreferencesChangeListener listener : preferencesChangeListeners) {
			ISafeRunnable job = new ISafeRunnable() {
				@Override
				public void handleException(Throwable e) {
					JavaLanguageServerPlugin.log(new CoreException(StatusFactory.newErrorStatus(e.getMessage(), e)));
				}

				@Override
				public void run() throws Exception {
					listener.preferencesChange(oldPreferences, newPreferences);
				}
			};
			SafeRunner.run(job);
		}
	}

	/**
	 * Workspace wide preferences
	 */
	public Preferences getPreferences() {
		return preferences;
	}

	/**
	 * Resource specific preferences
	 */
	public Preferences getPreferences(IResource resource) {
		return preferences;
	}

	public ClientPreferences getClientPreferences() {
		return clientPreferences;
	}

	/**
	 * @param clientCapabilities
	 *                                       the clientCapabilities to set
	 * @param extendedClientCapabilities
	 */
	public void updateClientPrefences(ClientCapabilities clientCapabilities, Map<String, Object> extendedClientCapabilities) {
		this.clientPreferences = new ClientPreferences(clientCapabilities, extendedClientCapabilities);
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

	public static Preferences getPrefs(IResource resource) {
		return JavaLanguageServerPlugin.getPreferencesManager().getPreferences(resource);
	}

	public static CodeGenerationSettings getCodeGenerationSettings(IResource resource) {
		IJavaProject project = JavaCore.create(resource.getProject());

		CodeGenerationSettings res = new CodeGenerationSettings();
		// TODO indentation settings should be retrieved from client/external
		// settings?
		res.tabWidth = CodeFormatterUtil.getTabWidth(project);
		res.indentWidth = CodeFormatterUtil.getIndentWidth(project);
		return res;
	}

	/**
	 * Register the given listener for notification of preferences changes. Calling
	 * this method multiple times with the same listener has no effect. The given
	 * listener argument must not be <code>null</code>.
	 *
	 * @param listener
	 *            the preferences change listener to register
	 */
	public void addPreferencesChangeListener(IPreferencesChangeListener listener) {
		preferencesChangeListeners.add(listener);

	}

	/**
	 * De-register the given listener from receiving notification of preferences
	 * changes. Calling this method multiple times with the same listener has no
	 * effect. The given listener argument must not be <code>null</code>.
	 *
	 * @param listener
	 *            the preference change listener to remove
	 */
	public void removePreferencesChangeListener(IPreferencesChangeListener listener) {
		preferencesChangeListeners.remove(listener);
	}

	/**
	 * @return Get the workspace runtime preferences for Eclipse
	 */
	public IEclipsePreferences getEclipsePreferences() {
		return eclipsePrefs;
	}

	/**
	 * Checks whether the client supports class file contents
	 */
	public boolean isClientSupportsClassFileContent() {
		return getClientPreferences() != null && getClientPreferences().isClassFileContentSupported();
	}

	/**
	 * Checks whether the client supports markdown in completion
	 */
	public boolean isClientSupportsCompletionDocumentationMarkDown() {
		return getClientPreferences() != null && getClientPreferences().isSupportsCompletionDocumentationMarkdown();
	}

}
