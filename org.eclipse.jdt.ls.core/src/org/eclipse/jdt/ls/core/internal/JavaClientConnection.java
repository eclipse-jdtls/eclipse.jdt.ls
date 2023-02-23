/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     Pivotal Inc. - added executeClientCommand API.
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.ls.core.internal.handlers.LogHandler;
import org.eclipse.jdt.ls.core.internal.lsp.ExecuteCommandProposedClient;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

import com.google.common.collect.ImmutableList;

public class JavaClientConnection {

	public interface JavaLanguageClient extends LanguageClient, ExecuteCommandProposedClient {

		/**
		 * The show message notification is sent from a server to a client to ask
		 * the client to display a particular message in the user interface.
		 */
		@JsonNotification("language/status")
		void sendStatusReport(StatusReport report);

		/**
		 * The actionable notification is sent from a server to a client to ask the
		 * client to display a particular message in the user interface, and possible
		 * commands to execute. The commands must be implemented on the client side.
		 */
		@JsonNotification("language/actionableNotification")
		void sendActionableNotification(ActionableNotification notification);

		/**
		 * The event notification is sent from a server to a client to notify the
		 * client certain events happened on the server side, eg. classpath was
		 * updated, projects were imported.
		 */
		@JsonNotification("language/eventNotification")
		void sendEventNotification(EventNotification notification);

		/**
		 * The progress report notification is sent from a server to be handled by the
		 * client.
		 */
		@JsonNotification("language/progressReport")
		void sendProgressReport(ProgressReport report);

	}

	private final LogHandler logHandler;
	private final JavaLanguageClient client;

	public JavaClientConnection(JavaLanguageClient client) {
		this.client = client;
		logHandler = new LogHandler();
		logHandler.install(this);
	}

	public Object executeClientCommand(Duration timeout, String id, Object... params) throws InterruptedException, ExecutionException, TimeoutException {
		return this.client.executeClientCommand(new ExecuteCommandParams(id, ImmutableList.copyOf(params))).get(timeout.toNanos(), TimeUnit.NANOSECONDS);
	}

	public Object executeClientCommand(String id, Object... params) {
		return this.client.executeClientCommand(new ExecuteCommandParams(id, ImmutableList.copyOf(params))).join();
	}

	public void sendNotification(String id, Object... params) {
		client.sendNotification(new ExecuteCommandParams(id, ImmutableList.copyOf(params)));
	}

	/**
	 * Sends the logMessage message back to the client as a notification
	 * @param msg The message to send back to the client
	 */
	public void logMessage(MessageType type, String msg) {
		MessageParams $= new MessageParams();
		$.setMessage(msg);
		$.setType(type);
		client.logMessage($);
	}


	/**
	 * Sends the message to the client, to be displayed on a UI element.
	 *
	 * @param type
	 * @param msg
	 */
	public void showNotificationMessage(MessageType type, String msg){
		MessageParams $ = new MessageParams();
		$.setMessage(msg);
		$.setType(type);
		client.showMessage($);
	}

	/**
	 * Sends the message to the client, to be displayed on a UI element.
	 * Waits for an answer from the user and returns the selected
	 * action.
	 *
	 * @param type
	 * @param msg
	 * @return
	 */
	public MessageActionItem showNotificationMessageRequest(MessageType type, String msg, List<MessageActionItem> actions){
		ShowMessageRequestParams $ = new ShowMessageRequestParams();
		$.setMessage(msg);
		$.setType(type);
		$.setActions(actions);
		return client.showMessageRequest($).join();
	}

	/**
	 * Sends a status to the client to be presented to users
	 * @param msg The status to send back to the client
	 */
	public void sendStatus(ServiceStatus serverStatus, String status) {
		StatusReport $ = new StatusReport();
		client.sendStatusReport( $.withMessage(status).withType(serverStatus.name()));
	}

	/**
	 * Sends a message to the client to be presented to users, with possible
	 * commands to execute
	 */
	public void sendActionableNotification(MessageType severity, String message, Object data, List<Command> commands) {
		ActionableNotification notification = new ActionableNotification().withSeverity(severity).withMessage(message).withData(data).withCommands(commands);
		sendActionableNotification(notification);
	}

	/**
	 * Sends a message to the client to be presented to users, with possible commands to execute
	 */
	public void sendActionableNotification(ActionableNotification notification) {
		client.sendActionableNotification(notification);
	}

	/**
	 * Send a notification to the client when some certain events happen.
	 * @See {@link org.eclipse.jdt.ls.core.internal.EventType} for all
	 * possible event types.
	 */
	public void sendEventNotification(EventNotification notification) {
		client.sendEventNotification(notification);
	}

	public void publishDiagnostics(PublishDiagnosticsParams diagnostics){
		client.publishDiagnostics(diagnostics);
	}


	/**
	 * Sends a message to client to apply the given workspace edit.
	 * This is available since LSP v3.0 should be used
	 * only by checking the ClientCapabilities.
	 *
	 * @param edit
	 */
	public boolean applyWorkspaceEdit(WorkspaceEdit edit){
		ApplyWorkspaceEditParams $ = new ApplyWorkspaceEditParams();
		$.setEdit(edit);
		ApplyWorkspaceEditResponse response = client.applyEdit($).join();
		return response.isApplied();
	}

	/**
	 * @see {@link org.eclipse.lsp4j.services.LanguageClient#unregisterCapability(RegistrationParams)}
	 */
	public void unregisterCapability(UnregistrationParams params) {
		client.unregisterCapability(params);
	}

	/**
	 * @see {@link org.eclipse.lsp4j.services.LanguageClient#registerCapability(RegistrationParams)}
	 */
	public void registerCapability(RegistrationParams params) {
		client.registerCapability(params);
	}

	/**
	 * @see {@link LanguageClient#configuration(ConfigurationParams)}
	 */
	public List<Object> configuration(ConfigurationParams configurationParams) {
		return this.client.configuration(configurationParams).join();
	}

	public CompletableFuture<Void> refreshInlayHints() {
		return this.client.refreshInlayHints();
	}

	public void disconnect() {
		if (logHandler != null) {
			logHandler.uninstall();
		}
	}

}
