package org.jboss.tools.vscode.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.tools.vscode.ipc.IPC;
import org.jboss.tools.vscode.ipc.JsonRpcConnection;
import org.jboss.tools.vscode.ipc.MessageType;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.ipc.ServiceStatus;
import org.jboss.tools.vscode.java.handlers.CompletionHandler;
import org.jboss.tools.vscode.java.handlers.DocumentHighlightHandler;
import org.jboss.tools.vscode.java.handlers.DocumentLifeCycleHandler;
import org.jboss.tools.vscode.java.handlers.DocumentSymbolHandler;
import org.jboss.tools.vscode.java.handlers.ExtensionLifeCycleHandler;
import org.jboss.tools.vscode.java.handlers.FindSymbolsHandler;
import org.jboss.tools.vscode.java.handlers.HoverHandler;
import org.jboss.tools.vscode.java.handlers.LogHandler;
import org.jboss.tools.vscode.java.handlers.NavigateToDefinitionHandler;
import org.jboss.tools.vscode.java.handlers.ReferencesHandler;
import org.jboss.tools.vscode.java.handlers.WorkspaceEventsHandler;
import org.jboss.tools.vscode.java.managers.ProjectsManager;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;

public class JavaClientConnection {
	
	private JsonRpcConnection rcpConnection;
	private LogHandler logHandler;
	private ProjectsManager projectsManager;

	public JavaClientConnection() {
		this.rcpConnection = new JsonRpcConnection(new IPC());
		
		projectsManager = new ProjectsManager();
		rcpConnection.addHandlers(handlers(projectsManager));
		
		logHandler = new LogHandler();
		logHandler.install(this);		
	}
	
	private List<RequestHandler> handlers(ProjectsManager pm) {
		List<RequestHandler> handlers = new ArrayList<RequestHandler>();
		handlers.add(new ExtensionLifeCycleHandler(pm, this));
		handlers.add(new DocumentLifeCycleHandler(this));
		handlers.add(new CompletionHandler());
		handlers.add(new HoverHandler());
		handlers.add(new NavigateToDefinitionHandler());
		handlers.add(new WorkspaceEventsHandler(pm));
		handlers.add(new DocumentSymbolHandler());
		handlers.add(new FindSymbolsHandler());
		handlers.add(new ReferencesHandler());
		handlers.add(new DocumentHighlightHandler());
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
	public void sendStatus(ServiceStatus serverStatus, String status) {
		JSONRPC2Notification note = new JSONRPC2Notification("language/status");
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("message", status);
		params.put("type", serverStatus.name());
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
