package org.jboss.tools.vscode.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
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
import org.jboss.tools.vscode.java.handlers.LogHandler;
import org.jboss.tools.vscode.java.handlers.NavigateToDefinitionHandler;
import org.jboss.tools.vscode.java.handlers.ReferencesHandler;
import org.jboss.tools.vscode.java.handlers.WorkspaceEventsHandler;
import org.jboss.tools.vscode.java.managers.DocumentsManager;
import org.jboss.tools.vscode.java.managers.ProjectsManager;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;

public class JavaClientConnection {
	
	private JsonRpcConnection rcpConnection;
	private LogHandler logHandler;
	private ProjectsManager projectsManager;

	public JavaClientConnection() {
		this.rcpConnection = new JsonRpcConnection(new IPC());
		
		projectsManager = new ProjectsManager();
		DocumentsManager dm = new DocumentsManager(this, projectsManager);
		rcpConnection.addHandlers(handlers(projectsManager, dm));
		
		logHandler = new LogHandler();
		logHandler.install(this);		
	}
	
	private List<RequestHandler> handlers(ProjectsManager pm, DocumentsManager dm) {
		List<RequestHandler> handlers = new ArrayList<RequestHandler>();
		handlers.add(new ExtensionLifeCycleHandler(pm, this));
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
	/**
	 * Sends the logMessage message back to the client as a notification
	 * @param msg The message to send back to the client
	 */
	public void logMessage(MessageType type, String msg) {
		JSONRPC2Notification note = new JSONRPC2Notification("window/logMessage");
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("message", msg);
		params.put("type", type.getType());
		note.setNamedParams(params);
		rcpConnection.sendNotification(note);
	}

	/**
	 * Sends a status to the client to be presented to users
	 * @param msg The status to send back to the client
	 */
	public void sendStatus(MessageType type, String status) {
		JSONRPC2Notification note = new JSONRPC2Notification("language/status");
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("message", status);
		params.put("type", type.name());
		note.setNamedParams(params);
		rcpConnection.sendNotification(note);
	}
	
	public void sendNotification(JSONRPC2Notification notification) {
		rcpConnection.sendNotification(notification);
	}
	
	public void connect() {
		rcpConnection.connect();
	}

	public void disconnect() {
		if (logHandler != null) {
			logHandler.uninstall();
			logHandler = null;
		}
	}

}
