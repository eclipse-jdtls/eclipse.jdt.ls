/*******************************************************************************
 * Copyright (c) 2016-2020 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     IBM Corporation (Markus Keller)
 *     Microsoft Corporation - extract to a base class
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JVMConfigurator;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.LanguageServerApplication;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Handler for the VS Code extension initialization
 */
public abstract class BaseInitHandler {

	public static final String JAVA_LS_INITIALIZATION_JOBS = "java-ls-initialization-jobs";
	public static final String SETTINGS_KEY = "settings";

	private PreferenceManager preferenceManager;

	protected ProjectsManager projectsManager;

	public BaseInitHandler(ProjectsManager projectsManager, PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
		this.projectsManager = projectsManager;
	}

	@SuppressWarnings("unchecked")
	public InitializeResult initialize(InitializeParams param) {
		logInfo("Initializing Java Language Server " + JavaLanguageServerPlugin.getVersion());
		InitializeResult result = new InitializeResult();
		handleInitializationOptions(param);
		registerCapabilities(result);

		// At the end of the InitHandler, trigger a job to import the workspace. This is used to ensure ServiceStatus notification
		// is not sent before the initialize response. See the bug https://github.com/redhat-developer/vscode-java/issues/1056
		triggerInitialization(preferenceManager.getPreferences().getRootPaths());
		return result;
	}

	public Map<?, ?> handleInitializationOptions(InitializeParams param) {
		Map<?, ?> initializationOptions = this.getInitializationOptions(param);
		Map<String, Object> extendedClientCapabilities = getInitializationOption(initializationOptions, "extendedClientCapabilities", Map.class);
		if (param.getCapabilities() == null) {
			preferenceManager.updateClientPrefences(new ClientCapabilities(), extendedClientCapabilities);
		} else {
			preferenceManager.updateClientPrefences(param.getCapabilities(), extendedClientCapabilities);
		}

		Collection<IPath> rootPaths = new ArrayList<>();
		Collection<String> workspaceFolders = getInitializationOption(initializationOptions, "workspaceFolders", Collection.class);
		if (workspaceFolders != null && !workspaceFolders.isEmpty()) {
			for (String uri : workspaceFolders) {
				IPath filePath = ResourceUtils.canonicalFilePathFromURI(uri);
				if (filePath != null) {
					rootPaths.add(filePath);
				}
			}
		} else {
			String rootPath = param.getRootUri();
			if (rootPath == null) {
				rootPath = param.getRootPath();
				if (rootPath != null) {
					logInfo("In LSP 3.0, InitializeParams.rootPath is deprecated in favour of InitializeParams.rootUri!");
				}
			}
			if (rootPath != null) {
				IPath filePath = ResourceUtils.canonicalFilePathFromURI(rootPath);
				if (filePath != null) {
					rootPaths.add(filePath);
				}
			}
		}
		if (rootPaths.isEmpty()) {
			IPath workspaceLocation = ResourcesPlugin.getWorkspace().getRoot().getLocation();
			logInfo("No workspace folders or root uri was defined. Falling back on " + workspaceLocation);
			rootPaths.add(workspaceLocation);
		}
		if (initializationOptions.get(SETTINGS_KEY) instanceof Map<?, ?> settings) {
			@SuppressWarnings("unchecked")
			Preferences prefs = Preferences.createFrom((Map<String, Object>) settings);
			prefs.setRootPaths(rootPaths);
			preferenceManager.update(prefs);
			if (!isWorkspaceInitialized()) {
				// We don't care about triggering a full build here, like in onDidChangeConfiguration
				try {
					JVMConfigurator.configureJVMs(prefs);
					registerWorkspaceInitialized();
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Failed to configure Java Runtimes", e);
				}
			}
		} else {
			preferenceManager.getPreferences().setRootPaths(rootPaths);
		}

		Collection<IPath> triggerPaths = new ArrayList<>();
		Collection<String> triggerFiles = getInitializationOption(initializationOptions, "triggerFiles", Collection.class);
		if (triggerFiles != null) {
			for (String uri : triggerFiles) {
				IPath filePath = ResourceUtils.canonicalFilePathFromURI(uri);
				if (filePath != null) {
					triggerPaths.add(filePath);
				}
			}
		}
		preferenceManager.getPreferences().setTriggerFiles(triggerPaths);

		Collection<IPath> configurationPaths = new ArrayList<>();
		Collection<String> projectConfigurations = getInitializationOption(initializationOptions, "projectConfigurations", Collection.class);
		if (projectConfigurations != null) {
			for (String uri : projectConfigurations) {
				IPath filePath = ResourceUtils.canonicalFilePathFromURI(uri);
				if (filePath != null) {
					configurationPaths.add(filePath);
				}
			}
			preferenceManager.getPreferences().setProjectConfigurations(configurationPaths);
		}

		Integer processId = param.getProcessId();
		LanguageServerApplication application = JavaLanguageServerPlugin.getLanguageServer();
		if (processId != null && application != null) {
			application.setParentProcessId(processId.longValue());
		}

		return initializationOptions;
	}

	private void registerWorkspaceInitialized() {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		prefs.putBoolean(IConstants.WORKSPACE_INITIALIZED, true);
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	private boolean isWorkspaceInitialized() {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		return prefs.getBoolean(IConstants.WORKSPACE_INITIALIZED, false);
	}

	public abstract void registerCapabilities(InitializeResult initializeResult);

	public abstract void triggerInitialization(Collection<IPath> roots);

	protected Map<?, ?> getInitializationOptions(InitializeParams params) {
		Map<?, ?> initOptions = JSONUtility.toModel(params.getInitializationOptions(), Map.class);
		return initOptions == null ? Collections.emptyMap() : initOptions;
	}

	protected <T> T getInitializationOption(Map<?, ?> initializationOptions, String key, Class<T> clazz) {
		if (initializationOptions != null) {
			Object bundleObject = initializationOptions.get(key);
			if (clazz.isInstance(bundleObject)) {
				return clazz.cast(bundleObject);
			}
		}
		return null;
	}

}
