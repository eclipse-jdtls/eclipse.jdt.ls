package org.jboss.tools.vscode.java;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class JavaLanguageServerPlugin implements BundleActivator {
	
	private static BundleContext context;

	private JavaClientConnection connection;

	static BundleContext getContext() {
		return context;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		JavaLanguageServerPlugin.context = bundleContext;
		connection = new JavaClientConnection();	
		connection.connect();
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		JavaLanguageServerPlugin.context = null;
		if (connection != null) {
			connection.disconnect();
			connection = null;	
		}
		connection = null;	
	}
	
	public JavaClientConnection getConnection() {
		return connection;
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
	
}
