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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.sql.Timestamp;
import java.util.Hashtable;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.internal.net.ProxySelector;
import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.util.tracker.ServiceTracker;

public class JavaLanguageServerPlugin implements BundleActivator {

	public static final String MANUAL = "Manual";
	public static final String HTTP_NON_PROXY_HOSTS = "http.nonProxyHosts";
	public static final String HTTPS_NON_PROXY_HOSTS = "https.nonProxyHosts";
	public static final String HTTPS_PROXY_PASSWORD = "https.proxyPassword";
	public static final String HTTPS_PROXY_PORT = "https.proxyPort";
	public static final String HTTPS_PROXY_HOST = "https.proxyHost";
	public static final String HTTP_PROXY_PASSWORD = "http.proxyPassword";
	public static final String HTTP_PROXY_PORT = "http.proxyPort";
	public static final String HTTP_PROXY_HOST = "http.proxyHost";
	public static final String HTTPS_PROXY_USER = "https.proxyUser";
	public static final String HTTP_PROXY_USER = "http.proxyUser";

	/**
	 * Source string send to clients for messages such as diagnostics.
	 **/
	public static final String SERVER_SOURCE_ID = "Java";

	public static final String PLUGIN_ID = "org.eclipse.jdt.ls.core";
	private static JavaLanguageServerPlugin pluginInstance;
	private static BundleContext context;
	private ServiceTracker<IProxyService, IProxyService> proxyServiceTracker = null;
	private static InputStream in;
	private static PrintStream out;
	private static PrintStream err;

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
		try {
			redirectStandardStreams();
		} catch (FileNotFoundException e) {
			logException(e.getMessage(), e);
		}
		JavaLanguageServerPlugin.context = bundleContext;
		JavaLanguageServerPlugin.pluginInstance = this;
		preferenceManager = new PreferenceManager();
		initializeJDTOptions();
		projectsManager = new ProjectsManager(preferenceManager);
		logInfo(getClass()+" is started");
		configureProxy();
	}

	private void configureProxy() {
		// It seems there is no way to set a proxy provider type (manual, native or
		// direct) without the Eclipse UI.
		// The org.eclipse.core.net plugin removes the http., https. system properties
		// when setting its preferences and a proxy provider isn't manual.
		// We save these parameters and set them after starting the
		// org.eclipse.core.net plugin.
		String httpHost = System.getProperty(HTTP_PROXY_HOST);
		String httpPort = System.getProperty(HTTP_PROXY_PORT);
		String httpUser = System.getProperty(HTTP_PROXY_USER);
		String httpPassword = System.getProperty(HTTP_PROXY_PASSWORD);
		String httpsHost = System.getProperty(HTTPS_PROXY_HOST);
		String httpsPort = System.getProperty(HTTPS_PROXY_PORT);
		String httpsUser = System.getProperty(HTTPS_PROXY_USER);
		String httpsPassword = System.getProperty(HTTPS_PROXY_PASSWORD);
		String httpsNonProxyHosts = System.getProperty(HTTPS_NON_PROXY_HOSTS);
		String httpNonProxyHosts = System.getProperty(HTTP_NON_PROXY_HOSTS);
		if (StringUtils.isNotBlank(httpUser) || StringUtils.isNotBlank(httpsUser)) {
			try {
				Platform.getBundle("org.eclipse.core.net").start(Bundle.START_TRANSIENT);
			} catch (BundleException e) {
				logException(e.getMessage(), e);
			}
			if (StringUtils.isNotBlank(httpUser) && StringUtils.isNotBlank(httpPassword)) {
				Authenticator.setDefault(new Authenticator() {
					@Override
					public PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(httpUser, httpPassword.toCharArray());
					}
				});
			}
			IProxyService proxyService = getProxyService();
			if (proxyService != null) {
				ProxySelector.setActiveProvider(MANUAL);
				IProxyData[] proxies = proxyService.getProxyData();
				for (IProxyData proxy:proxies) {
					if ("HTTP".equals(proxy.getType())) {
						proxy.setHost(httpHost);
						proxy.setPort(httpPort == null ? -1 : Integer.valueOf(httpPort));
						proxy.setPassword(httpPassword);
						proxy.setUserid(httpUser);
					}
					if ("HTTPS".equals(proxy.getType())) {
						proxy.setHost(httpsHost);
						proxy.setPort(httpsPort == null ? -1 : Integer.valueOf(httpsPort));
						proxy.setPassword(httpsPassword);
						proxy.setUserid(httpsUser);
					}
				}
				try {
					proxyService.setProxyData(proxies);
					if (httpHost != null) {
						System.setProperty(HTTP_PROXY_HOST, httpHost);
					}
					if (httpPort != null) {
						System.setProperty(HTTP_PROXY_PORT, httpPort);
					}
					if (httpUser != null) {
						System.setProperty(HTTP_PROXY_USER, httpUser);
					}
					if (httpPassword != null) {
						System.setProperty(HTTP_PROXY_PASSWORD, httpPassword);
					}
					if (httpsHost != null) {
						System.setProperty(HTTPS_PROXY_HOST, httpsHost);
					}
					if (httpsPort != null) {
						System.setProperty(HTTPS_PROXY_PORT, httpsPort);
					}
					if (httpsUser != null) {
						System.setProperty(HTTPS_PROXY_USER, httpsUser);
					}
					if (httpsPassword != null) {
						System.setProperty(HTTPS_PROXY_PASSWORD, httpsPassword);
					}
					if (httpsNonProxyHosts != null) {
						System.setProperty(HTTPS_NON_PROXY_HOSTS, httpsNonProxyHosts);
					}
					if (httpNonProxyHosts != null) {
						System.setProperty(HTTP_NON_PROXY_HOSTS, httpNonProxyHosts);
					}
				} catch (CoreException e) {
					logException(e.getMessage(), e);
				}
			}
		}
	}

	public IProxyService getProxyService() {
		try {
			if (proxyServiceTracker == null) {
				proxyServiceTracker = new ServiceTracker<>(context, IProxyService.class.getName(), null);
				proxyServiceTracker.open();
			}
			return proxyServiceTracker.getService();
		} catch (Exception e) {
			logException(e.getMessage(), e);
		}
		return null;
	}

	private void startConnection() throws IOException {
		protocol = new JDTLanguageServer(projectsManager, preferenceManager);
		ConnectionStreamFactory connectionFactory = new ConnectionStreamFactory();
		Launcher<JavaLanguageClient> launcher = Launcher.createLauncher(protocol, JavaLanguageClient.class,
				connectionFactory.getInputStream(),
				connectionFactory.getOutputStream());
		protocol.connectClient(launcher.getRemoteProxy());
		launcher.startListening();
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		logInfo(getClass()+" is stopping:");
		logInfo(getThreadDump());
		JavaLanguageServerPlugin.pluginInstance = null;
		JavaLanguageServerPlugin.context = null;
		projectsManager = null;
		languageServer = null;
	}
	private String getThreadDump() {
		String lineSep = System.getProperty("line.separator");
		StringBuilder sb = new StringBuilder();
		Thread.getAllStackTraces().entrySet().forEach(e -> {
			sb.append(e.getKey());
			sb.append(lineSep);
			Stream.of(e.getValue()).forEach(ste -> {
				sb.append("\tat ").append(ste).append(lineSep);
			});
		});
		return sb.toString();
	}

	public WorkingCopyOwner getWorkingCopyOwner(){
		return this.protocol.getWorkingCopyOwner();
	}

	public static JavaLanguageServerPlugin getInstance(){
		return pluginInstance;
	}

	public static void log(IStatus status) {
		if (context != null) {
			Platform.getLog(JavaLanguageServerPlugin.context.getBundle()).log(status);
		}
	}

	public static void logError(String message) {
		if (context != null) {
			log(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), message));
		}
	}

	public static void logInfo(String message) {
		if (context != null) {
			log(new Status(IStatus.INFO, context.getBundle().getSymbolicName(), message));
		}
	}

	public static void logException(String message, Throwable ex) {
		if (context != null) {
			log(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), message, ex));
		}
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
	 * Initialize default preference values of used bundles to match
	 * server functionality.
	 */
	private void initializeJDTOptions() {
		// Update JavaCore options
		Hashtable<String, String> javaCoreOptions = JavaCore.getOptions();
		javaCoreOptions.put(JavaCore.CODEASSIST_VISIBILITY_CHECK, JavaCore.ENABLED);
		JavaCore.setOptions(javaCoreOptions);
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

	private static void redirectStandardStreams() throws FileNotFoundException {
		in = System.in;
		out = System.out;
		err = System.err;
		System.setIn(new ByteArrayInputStream(new byte[0]));
		String debug = System.getProperty("ls.debug");
		boolean isDebug = debug != null && "true".equals(debug);
		if (isDebug) {
			String id = ("ls-" + new Timestamp(System.currentTimeMillis())).replace(" ", "_");
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			File workspaceFile = root.getRawLocation().makeAbsolute().toFile();
			File rootFile = new File(workspaceFile, ".metadata");
			rootFile.mkdirs();
			File outFile = new File(rootFile, ".out-" + id + ".log");
			FileOutputStream stdFileOut = new FileOutputStream(outFile);
			System.setOut(new PrintStream(stdFileOut));
			File errFile = new File(rootFile, ".error-" + id + ".log");
			FileOutputStream stdFileErr = new FileOutputStream(errFile);
			System.setErr(new PrintStream(stdFileErr));
		} else {
			System.setOut(new PrintStream(new ByteArrayOutputStream()));
			System.setErr(new PrintStream(new ByteArrayOutputStream()));
		}
	}

	public static InputStream getIn() {
		return in;
	}

	public static PrintStream getOut() {
		return out;
	}

	public static PrintStream getErr() {
		return err;
	}
}
