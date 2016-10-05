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

import java.util.ArrayList;
import java.util.List;

import org.jboss.tools.langs.LogMessageParams;
import org.jboss.tools.langs.ShowMessageParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.langs.base.LSPServer;
import org.jboss.tools.langs.base.NotificationMessage;
import org.jboss.tools.langs.ext.StatusReport;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.MessageType;
import org.jboss.tools.vscode.internal.ipc.NotificationHandler;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.internal.ipc.ServiceStatus;
import org.jboss.tools.vscode.java.internal.handlers.ClassfileContentHandler;
import org.jboss.tools.vscode.java.internal.handlers.CodeLensHandler;
import org.jboss.tools.vscode.java.internal.handlers.CompletionHandler;
import org.jboss.tools.vscode.java.internal.handlers.CompletionResolveHandler;
import org.jboss.tools.vscode.java.internal.handlers.DocumentHighlightHandler;
import org.jboss.tools.vscode.java.internal.handlers.DocumentLifeCycleHandler;
import org.jboss.tools.vscode.java.internal.handlers.DocumentSymbolHandler;
import org.jboss.tools.vscode.java.internal.handlers.ExitHandler;
import org.jboss.tools.vscode.java.internal.handlers.FormatterHandler;
import org.jboss.tools.vscode.java.internal.handlers.HoverHandler;
import org.jboss.tools.vscode.java.internal.handlers.InitHandler;
import org.jboss.tools.vscode.java.internal.handlers.LogHandler;
import org.jboss.tools.vscode.java.internal.handlers.NavigateToDefinitionHandler;
import org.jboss.tools.vscode.java.internal.handlers.ReferencesHandler;
import org.jboss.tools.vscode.java.internal.handlers.ShutdownHandler;
import org.jboss.tools.vscode.java.internal.handlers.WorkspaceEventsHandler;
import org.jboss.tools.vscode.java.internal.handlers.WorkspaceSymbolHandler;
import org.jboss.tools.vscode.java.internal.managers.ProjectsManager;

public class JavaClientConnection extends LSPServer{

	private LogHandler logHandler;
	private ProjectsManager projectsManager;

	public class JavaCancelMonitor implements CancelMonitor{

		private volatile boolean isCancelled;

		/* (non-Javadoc)
		 * @see org.jboss.tools.vscode.internal.ipc.CancelMonitor#cancelled()
		 */
		@Override
		public boolean cancelled() {
			return isCancelled;
		}

		/* (non-Javadoc)
		 * @see org.jboss.tools.vscode.internal.ipc.CancelMonitor#onCancel()
		 */
		@Override
		public void onCancel() {
			isCancelled = true;
		}
	}

	public JavaClientConnection(ProjectsManager projectsManager) {
		this.projectsManager = projectsManager;
		logHandler = new LogHandler();
		logHandler.install(this);
	}

	/**
	 * Sends the logMessage message back to the client as a notification
	 * @param msg The message to send back to the client
	 */
	public void logMessage(MessageType type, String msg) {
		NotificationMessage<LogMessageParams> message= new NotificationMessage<>();
		message.setMethod(LSPMethods.WINDOW_LOGMESSAGE.getMethod());
		message.setParams(new LogMessageParams().withMessage(msg)
				.withType(type.getType()));
		send(message);
	}

	/**
	 * Sends the message to the client to be displayed on a UI element.
	 *
	 * @param type
	 * @param msg
	 */
	public void showNotificationMessage(MessageType type, String msg){
		if(msg == null ) return;
		NotificationMessage<ShowMessageParams> message = new NotificationMessage<>();
		message.setMethod(LSPMethods.WINDOW_SHOW_MESSAGE.getMethod());
		message.setParams(new ShowMessageParams().withMessage(msg)
				.withType(type.getType()));
		send(message);
	}

	/**
	 * Sends a status to the client to be presented to users
	 * @param msg The status to send back to the client
	 */
	public void sendStatus(ServiceStatus serverStatus, String status) {
		NotificationMessage<StatusReport> message = new NotificationMessage<>();
		message.setMethod(LSPMethods.LANGUAGE_STATUS.getMethod());
		message.setParams(new StatusReport().withMessage(status).withType(serverStatus.name()));
		send(message);
	}


	public void disconnect() {
		super.shutdown();
		if (logHandler != null) {
			logHandler.uninstall();
			logHandler = null;
		}
	}

	/* (non-Javadoc)
	 * @see org.jboss.tools.langs.base.LSPServer#newCancelMonitor()
	 */
	@Override
	protected CancelMonitor newCancelMonitor() {
		return new JavaCancelMonitor();
	}

	/* (non-Javadoc)
	 * @see org.jboss.tools.langs.base.LSPServer#buildRequestHandlers()
	 */
	@Override
	protected List<RequestHandler<?, ?>> buildRequestHandlers() {
		List<RequestHandler<?,?>> $= new ArrayList<>(16);
		//server lifeCycle
		$.add(new InitHandler(this.projectsManager, this));
		$.add(new ShutdownHandler());
		$.add(new HoverHandler());
		$.add(new CompletionHandler());
		$.add(new CompletionResolveHandler());
		$.add(new NavigateToDefinitionHandler());

		$.add(new DocumentSymbolHandler());
		$.add(new WorkspaceSymbolHandler());
		$.add(new ReferencesHandler());
		$.add(new DocumentHighlightHandler());
		FormatterHandler formatterHandler = new FormatterHandler();
		$.add(formatterHandler.new DocFormatter());
		$.add(formatterHandler.new RangeFormatter());
		final CodeLensHandler codeLensHandler = new CodeLensHandler();
		$.add(codeLensHandler.new CodeLensProvider());
		$.add(codeLensHandler.new CodeLensResolver());
		$.add(new ClassfileContentHandler());
		return $;
	}

	/* (non-Javadoc)
	 * @see org.jboss.tools.langs.base.LSPServer#buildNotificationHandlers()
	 */
	@Override
	protected List<NotificationHandler<?, ?>> buildNotificationHandlers() {
		List<NotificationHandler<?, ?>> $ = new ArrayList<>(5);
		DocumentLifeCycleHandler dh = new DocumentLifeCycleHandler(this);
		$.add(dh.new ChangeHandler());
		$.add(dh.new ClosedHandler());
		$.add(dh.new OpenHandler());
		$.add(dh.new SaveHandler());
		$.add(new ExitHandler());
		$.add(new WorkspaceEventsHandler(this.projectsManager,this));
		return $;
	}

}