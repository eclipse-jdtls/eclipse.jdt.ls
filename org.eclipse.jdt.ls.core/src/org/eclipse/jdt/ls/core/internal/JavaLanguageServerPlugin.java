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
package org.eclipse.jdt.ls.core.internal;

import java.io.IOException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class JavaLanguageServerPlugin implements BundleActivator {

	/**
	 * Source string send to clients for messages such as diagnostics.
	 **/
	public static final String SERVER_SOURCE_ID = "Java";

	public static final String PLUGIN_ID = "org.eclipse.jdt.ls.core";
	private static JavaLanguageServerPlugin pluginInstance;
	private static BundleContext context;

	private LanguageServer languageServer;
	private ProjectsManager projectsManager;

	private JDTLanguageServer protocol;

	private PreferenceManager preferenceManager;

	public static LanguageServer getLanguageServer() {
		return pluginInstance == null? null: pluginInstance.languageServer;
	}
	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext bundleContext) {
		JavaLanguageServerPlugin.context = bundleContext;
		JavaLanguageServerPlugin.pluginInstance = this;
		preferenceManager = new PreferenceManager();
		projectsManager = new ProjectsManager(preferenceManager);

	}

	private void startConnection() throws IOException {
		protocol = new JDTLanguageServer(projectsManager, preferenceManager);
		Launcher<JavaLanguageClient> launcher = Launcher.createLauncher(protocol, JavaLanguageClient.class,
				ConnectionStreamFactory.getInputStream(),
				ConnectionStreamFactory.getOutputStream());
		protocol.connectClient(launcher.getRemoteProxy());
		launcher.startListening();

	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		JavaLanguageServerPlugin.pluginInstance = null;
		JavaLanguageServerPlugin.context = null;
		projectsManager = null;
		languageServer = null;

	}

	public WorkingCopyOwner getWorkingCopyOwner(){
		return this.protocol.getWorkingCopyOwner();
	}

	public static JavaLanguageServerPlugin getInstance(){
		return pluginInstance;
	}

	public static void log(IStatus status) {
		Platform.getLog(JavaLanguageServerPlugin.context.getBundle()).log(status);
	}

	public static void logError(String message) {
		log(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), message));
	}

	public static void logInfo(String message) {
		log(new Status(IStatus.INFO, context.getBundle().getSymbolicName(), message));
	}

	public static void logException(String message, Throwable ex) {
		log(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), message, ex));
	}

	public static void sendStatus(ServiceStatus serverStatus, String status) {
		if (pluginInstance != null && pluginInstance.protocol != null) {
			pluginInstance.protocol.sendStatus(serverStatus, status);
		}
	}

	static void startLanguageServer(LanguageServer newLanguageServer) throws IOException {
		if (pluginInstance != null) {
			pluginInstance.languageServer = newLanguageServer;
			pluginInstance.startConnection();
		}
	}


	/**
	 * @return
	 */
	public static ProjectsManager getProjectsManager() {
		return pluginInstance.projectsManager;
	}

	/**
	 * @return the Java Language Server version
	 */
	public static String getVersion() {
		return context == null? "Unknown":context.getBundle().getVersion().toString();
	}
}
