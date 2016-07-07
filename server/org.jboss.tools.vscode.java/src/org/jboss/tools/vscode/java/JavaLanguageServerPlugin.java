package org.jboss.tools.vscode.java;

import java.util.ArrayList;
import java.util.List;

import javax.swing.text.DocumentFilter;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.jboss.tools.vscode.ipc.IPC;
import org.jboss.tools.vscode.ipc.JsonRpcConnection;
import org.jboss.tools.vscode.ipc.MessageType;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.handlers.CompletionHandler;
import org.jboss.tools.vscode.java.handlers.DocumentLifeCycleHandler;
import org.jboss.tools.vscode.java.handlers.DocumentSymbolHandler;
import org.jboss.tools.vscode.java.handlers.ExtensionLifeCycleHandler;
import org.jboss.tools.vscode.java.handlers.FindSymbolsHandler;
import org.jboss.tools.vscode.java.handlers.HoverHandler;
import org.jboss.tools.vscode.java.handlers.NavigateToDefinitionHandler;
import org.jboss.tools.vscode.java.handlers.ReferencesHandler;
import org.jboss.tools.vscode.java.handlers.WorkspaceEventsHandler;
import org.jboss.tools.vscode.java.managers.DocumentsManager;
import org.jboss.tools.vscode.java.managers.ProjectsManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class JavaLanguageServerPlugin implements BundleActivator {

	public static JavaLanguageServerPlugin instance;
	private static BundleContext context;
	private ProjectsManager pm;
	private DocumentsManager dm;
	private JsonRpcConnection connection;

	static BundleContext getContext() {
		return context;
	}

	public JavaLanguageServerPlugin() {
		instance = this;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		JavaLanguageServerPlugin.context = bundleContext;
		connection = new JsonRpcConnection(new IPC());
		pm = new ProjectsManager();
		dm = new DocumentsManager(connection,pm);
		connection.addHandlers(handlers());
		connection.connect();
	}

	/**
	 * @return
	 */
	private List<RequestHandler> handlers() {
		List<RequestHandler> handlers = new ArrayList<RequestHandler>();
		handlers.add(new ExtensionLifeCycleHandler(pm));
		handlers.add(new DocumentLifeCycleHandler(dm));
		handlers.add(new CompletionHandler(dm));
		handlers.add(new HoverHandler(dm));
		handlers.add(new NavigateToDefinitionHandler(dm));
		handlers.add(new WorkspaceEventsHandler(pm,dm));
		handlers.add(new DocumentSymbolHandler(dm));
		handlers.add(new FindSymbolsHandler());
		handlers.add(new ReferencesHandler(dm));
		return handlers;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		JavaLanguageServerPlugin.context = null;
		connection = null;	
	}
	
	public JsonRpcConnection getConnection(){
		return connection;
	}
	
	public static void log(MessageType type, String msg) {
		instance.connection.logMessage(type, msg);
	}
}
