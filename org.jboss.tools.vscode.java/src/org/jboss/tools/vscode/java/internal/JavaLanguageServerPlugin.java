/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal;

import java.io.IOException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.jboss.tools.vscode.java.internal.managers.ProjectsManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class JavaLanguageServerPlugin implements BundleActivator {

	/**
	 * Source string send to clients for messages such as diagnostics.
	 **/
	public static final String SERVER_SOURCE_ID = "Java";

	public static final String PLUGIN_ID = "org.jboss.tools.vscode.java";
	private static JavaLanguageServerPlugin pluginInstance;
	private static BundleContext context;

	private LanguageServer languageServer;
	private JavaClientConnection connection;
	private ProjectsManager projectsManager;
	private LanguageServerWorkingCopyOwner workingCopyOwner;

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
		projectsManager = new ProjectsManager();
	}

	private void startConnection() throws IOException {
		connection = new JavaClientConnection(projectsManager);
		connection.connect();
		this.workingCopyOwner = new LanguageServerWorkingCopyOwner(connection);
		WorkingCopyOwner.setPrimaryBufferProvider(this.workingCopyOwner);
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		JavaLanguageServerPlugin.pluginInstance = null;
		JavaLanguageServerPlugin.context = null;
		if (connection != null) {
			connection.disconnect();
		}
		projectsManager = null;
		connection = null;
		languageServer = null;

	}

	public JavaClientConnection getConnection(){
		return connection;
	}

	public WorkingCopyOwner getWorkingCopyOwner(){
		return this.workingCopyOwner;
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
}
