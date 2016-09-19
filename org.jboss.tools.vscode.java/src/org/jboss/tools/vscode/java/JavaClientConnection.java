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
package org.jboss.tools.vscode.java;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.tools.langs.LogMessageParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.langs.base.LSPServer;
import org.jboss.tools.langs.base.NotificationMessage;
import org.jboss.tools.langs.ext.StatusReport;
import org.jboss.tools.vscode.ipc.MessageType;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.ipc.ServiceStatus;
import org.jboss.tools.vscode.java.handlers.ClassfileContentHandler;
import org.jboss.tools.vscode.java.handlers.CodeLensHandler;
import org.jboss.tools.vscode.java.handlers.CompletionHandler;
import org.jboss.tools.vscode.java.handlers.CompletionResolveHandler;
import org.jboss.tools.vscode.java.handlers.DocumentHighlightHandler;
import org.jboss.tools.vscode.java.handlers.DocumentLifeCycleHandler;
import org.jboss.tools.vscode.java.handlers.DocumentSymbolHandler;
import org.jboss.tools.vscode.java.handlers.ExitHandler;
import org.jboss.tools.vscode.java.handlers.FormatterHandler;
import org.jboss.tools.vscode.java.handlers.HoverHandler;
import org.jboss.tools.vscode.java.handlers.InitHandler;
import org.jboss.tools.vscode.java.handlers.LogHandler;
import org.jboss.tools.vscode.java.handlers.NavigateToDefinitionHandler;
import org.jboss.tools.vscode.java.handlers.ReferencesHandler;
import org.jboss.tools.vscode.java.handlers.ShutdownHandler;
import org.jboss.tools.vscode.java.handlers.WorkspaceEventsHandler;
import org.jboss.tools.vscode.java.handlers.WorkspaceSymbolHandler;
import org.jboss.tools.vscode.java.managers.ProjectsManager;

public class JavaClientConnection extends LSPServer{
	
	private LogHandler logHandler;
	private ProjectsManager projectsManager;

	public JavaClientConnection() {
		projectsManager = new ProjectsManager();
		logHandler = new LogHandler();
		logHandler.install(this);		
	}
	
	private List<RequestHandler<?,?>> handlers(ProjectsManager pm) {
		List<RequestHandler<?,?>> handlers = new ArrayList<RequestHandler<?,?>>();
		//server lifeCycle
		handlers.add(new InitHandler(pm, this));
		handlers.add(new ShutdownHandler());
		handlers.add(new ExitHandler());
		handlers.add(new HoverHandler());
		DocumentLifeCycleHandler dh = new DocumentLifeCycleHandler(this);
		handlers.add(dh.new ChangeHandler());
		handlers.add(dh.new ClosedHandler());
		handlers.add(dh.new OpenHandler());
		handlers.add(dh.new SaveHandler());
		handlers.add(new CompletionHandler());
		handlers.add(new CompletionResolveHandler());
		handlers.add(new NavigateToDefinitionHandler());
		handlers.add(new WorkspaceEventsHandler(pm,this));
		handlers.add(new DocumentSymbolHandler());
		handlers.add(new WorkspaceSymbolHandler());
		handlers.add(new ReferencesHandler());
		handlers.add(new DocumentHighlightHandler());
		FormatterHandler formatterHandler = new FormatterHandler();
		handlers.add(formatterHandler.new DocFormatter());
		handlers.add(formatterHandler.new RangeFormatter());
		final CodeLensHandler codeLensHandler = new CodeLensHandler();
		handlers.add(codeLensHandler.new CodeLensProvider());
		handlers.add(codeLensHandler.new CodeLensResolver());
		handlers.add(new ClassfileContentHandler());
		return handlers;
	}	
	/**
	 * Sends the logMessage message back to the client as a notification
	 * @param msg The message to send back to the client
	 */
	public void logMessage(MessageType type, String msg) {
		NotificationMessage<LogMessageParams> message= new NotificationMessage<LogMessageParams>();
		message.setMethod(LSPMethods.WINDOW_LOGMESSAGE.getMethod());
		message.setParams(new LogMessageParams().withMessage(msg)
				.withType(Double.valueOf(type.getType())));
		send(message);
	}

	/**
	 * Sends a status to the client to be presented to users
	 * @param msg The status to send back to the client
	 */
	public void sendStatus(ServiceStatus serverStatus, String status) {
		NotificationMessage<StatusReport> message = new NotificationMessage<StatusReport>();
		message.setMethod(LSPMethods.LANGUAGE_STATUS.getMethod());
		message.setParams(new StatusReport().withMessage(status).withType(serverStatus.name()));
		send(message);
	}
	
	
	public void connect() throws IOException {
		connect(handlers(projectsManager));
	}

	public void disconnect() {
		super.shutdown();
		if (logHandler != null) {
			logHandler.uninstall();
			logHandler = null;
		}
	}

}
