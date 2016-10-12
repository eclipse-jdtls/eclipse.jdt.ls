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
package org.jboss.tools.langs.base;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.jboss.tools.langs.base.ResponseError.ReservedCode;
import org.jboss.tools.langs.transport.Connection;
import org.jboss.tools.langs.transport.NamedPipeConnection;
import org.jboss.tools.langs.transport.SocketConnection;
import org.jboss.tools.langs.transport.TransportMessage;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.NotificationHandler;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
/**
 * Base server implementation
 *
 * @author Gorkem Ercan
 *
 */
public abstract class LSPServer {

	protected Connection connection;
	private final JSONHelper jsonHelper;
	private List<RequestHandler<?, ?>> requestHandlers;
	private List<NotificationHandler<?, ?>> notificationHandlers;

	private Map<Integer, CancelMonitor> cancelMonitors = new HashMap<>();
	private ExecutorService executor;

	protected LSPServer(){
		jsonHelper = new JSONHelper();
	}

	public void connect() throws IOException{
		this.notificationHandlers = buildNotificationHandlers();
		this.requestHandlers = buildRequestHandlers();
		initExecutor();
		connection = initConnection();
		if(connection == null ){
			//Temporary for tests to run
			System.err.println("Failied to initialize connection");
			return;
		}
		connection.start();
		startDispatching();
	}


	private Connection initConnection(){
		final String stdInName = System.getenv("STDIN_PIPE_NAME");
		final String stdOutName = System.getenv("STDOUT_PIPE_NAME");
		if (stdInName != null && stdOutName != null) {
			return new NamedPipeConnection(stdOutName, stdInName);
		}
		final String wHost = System.getenv().getOrDefault("STDIN_HOST","localhost");
		final String rHost = System.getenv().getOrDefault("STDOUT_HOST","localhost");
		final String wPort = System.getenv().get("STDIN_PORT");
		final String rPort = System.getenv().get("STDOUT_PORT");
		if(rPort != null && wPort != null ){
			return new SocketConnection(rHost, Integer.parseInt(rPort),
					wHost, Integer.parseInt(wPort));
		}
		return null;
	}

	/**
	 *
	 */
	private void initExecutor() {
		final ThreadFactory threadFactory = new ThreadFactoryBuilder()
				.setNameFormat("LSP Executor-%d")
				.setDaemon(true)
				.build();
		executor = Executors.newCachedThreadPool(threadFactory);
	}

	public void send (Message message){
		TransportMessage tm = new TransportMessage(jsonHelper.toJson(message));
		connection.send(tm);
	}

	private void startDispatching() {
		Thread t = new Thread(() -> {
			try {
				while (true) {
					TransportMessage message = connection.take();
					runMessage(message);
				}
			} catch (InterruptedException e) {
				//Exit dispatch
				e.printStackTrace();
			}
		});
		t.start();
	}

	private void runMessage(final TransportMessage message ){
		executor.execute(() -> {

			Message msg = maybeParseMessage(message);
			if (msg == null)
				return;

			if (msg instanceof NotificationMessage) {
				NotificationMessage<?> nm = (NotificationMessage<?>) msg;
				try {
					dispatchNotification(nm);
				} catch (LSPException e) {
					e.printStackTrace();
				}
			}

			if (msg instanceof RequestMessage) {
				RequestMessage<?> rm = (RequestMessage<?>) msg;
				try {
					dispatchRequest(rm);
				} catch (LSPException e) {
					send(rm.respondWithError(e.getCode(), e.getMessage(), e.getData()));
				} catch (Exception e) {
					send(rm.respondWithError(ReservedCode.INTERNAL_ERROR.code(), e.getMessage(), null));
				}
			}
		});
	}



	/**
	 * Parses the message notifies client if parse fails and returns null
	 *
	 * @param message
	 * @param msg
	 * @return
	 */
	private Message maybeParseMessage(TransportMessage message) {
		ResponseError error = null;
		try {
			return jsonHelper.fromJson(message);
		} catch (LSPException e) {
			error = new ResponseError();
			error.setCode(e.getCode());
			error.setMessage(e.getMessage());
			error.setData(e.getData());
		} catch (Exception e) {
			error = new ResponseError();
			error.setCode(ReservedCode.PARSE_ERROR.code());
			error.setMessage(e.getMessage());
			error.setData(message.getContent());
		}
		@SuppressWarnings("rawtypes")
		ResponseMessage rm = new ResponseMessage();
		rm.setError(error);
		send(rm);
		return null;
	}

	private void dispatchRequest(RequestMessage<?> request) {
		for (Iterator<RequestHandler<?, ?>> iterator = requestHandlers.iterator(); iterator.hasNext();) {
			@SuppressWarnings("unchecked")
			RequestHandler<Object, Object> requestHandler = (RequestHandler<Object, Object>) iterator.next();
			if (requestHandler.canHandle(request.getMethod())) {
				try{
					CancelMonitor cm = newCancelMonitor();
					cancelMonitors.put(request.getId(),cm);
					send(request.responseWith(requestHandler.handle(request.getParams(), cm)));
					return;
				}finally{
					cancelMonitors.remove(request.getId());
				}
			}
		}
		throw new LSPException(ReservedCode.METHOD_NOT_FOUND.code(), request.getMethod() + " is not handled", null,null);
	}

	private void dispatchNotification(NotificationMessage<?> nm) {
		if(LSPMethods.CANCEL.getMethod().equals(nm.getMethod())){
			CancelParams params = (CancelParams)nm.getParams();
			CancelMonitor monitor = cancelMonitors.remove(params.getId());
			if(monitor != null){
				monitor.onCancel();
			}
		}
		for (Iterator<NotificationHandler<?, ?>> iterator = notificationHandlers.iterator(); iterator.hasNext();) {
			@SuppressWarnings("unchecked")
			NotificationHandler<Object, Object> requestHandler =  (NotificationHandler<Object, Object>) iterator.next();
			if(requestHandler.canHandle(nm.getMethod())){
				requestHandler.handle(nm.getParams());
				break;
			}
		}
	}

	public void shutdown(){
		executor.shutdown();
		connection.close();
	}

	/**
	 * Factory method for creating new CancelMonitors that are passed to request handlers.
	 * Cancel monitors can not be shared implementors must return a new instance for every call.
	 *
	 * @return cancelMonitor
	 */
	protected abstract CancelMonitor newCancelMonitor();

	/**
	 * Returns the list of request handlers.
	 * Called once
	 * @return
	 */
	protected abstract List<RequestHandler<?, ?>> buildRequestHandlers();

	protected abstract List<NotificationHandler<?, ?>> buildNotificationHandlers();

}
