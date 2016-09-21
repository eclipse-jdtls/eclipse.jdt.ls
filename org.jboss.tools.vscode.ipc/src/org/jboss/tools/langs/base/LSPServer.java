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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
import org.jboss.tools.langs.transport.TransportMessage;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.NotificationHandler;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
/**
 * Base server implementation
 *
 * @author Gorkem Ercan
 *
 */
public abstract class LSPServer {

	private class ParameterizedTypeImpl implements ParameterizedType{

		final private Type rawType;
		final private Type paramType;

		public ParameterizedTypeImpl(Type rawType, Type paramType) {
			this.rawType = rawType;
			this.paramType = paramType;
		}

		@Override
		public Type[] getActualTypeArguments() {
			return new Type[]{paramType};
		}

		@Override
		public Type getRawType() {
			return rawType;
		}

		@Override
		public Type getOwnerType() {
			return null;
		}

	}

	private class MessageJSONHandler implements JsonSerializer<Message>, JsonDeserializer<Message>{

		@Override
		public Message deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context)
				throws JsonParseException {
			JsonObject object = jsonElement.getAsJsonObject();
			Type subType = ResponseMessage.class;
			if(!object.has("id")){
				subType = NotificationMessage.class;
				//TODO: Resolve result type from id.
			}
			else if(object.has("method")){
				subType = RequestMessage.class;
			}
			String method = object.get("method").getAsString();
			LSPMethods lspm = LSPMethods.fromMethod(method);
			if(lspm == null){
				throw new LSPException(ReservedCode.METHOD_NOT_FOUND.code(), method + " is not handled ", null, null);
			}
			Type paramType = LSPMethods.fromMethod(method).getRequestType();
			return context.deserialize(jsonElement, new ParameterizedTypeImpl(subType,paramType));
		}

		@Override
		public JsonElement serialize(Message message, Type type, JsonSerializationContext context) {
			Type rawType = null;
			Type paramType = Object.class;
			if(message instanceof NotificationMessage){
				rawType = NotificationMessage.class;
				NotificationMessage<?> nm = (NotificationMessage<?>)message;
				if(nm.getParams() != null){
					paramType = nm.getParams().getClass();
				}
			}
			if(message instanceof ResponseMessage){
				rawType = ResponseMessage.class;
				ResponseMessage<?> rm = (ResponseMessage<?>)message;
				if(rm.getResult() != null ){
					paramType = rm.getResult().getClass();
				}
			}
			if(message instanceof RequestMessage){
				rawType = RequestMessage.class;
				RequestMessage<?>rqm = (RequestMessage<?>)message;
				if(rqm.getParams() != null){
					paramType = rqm.getParams().getClass();
				}
			}
			if(rawType == null)
				throw new RuntimeException("Unrecognized message type");
			return context.serialize(message,new ParameterizedTypeImpl(rawType,paramType));
		}

	}
	private Connection connection;
	private final Gson gson;
	private List<RequestHandler<?, ?>> requestHandlers;
	private List<NotificationHandler<?, ?>> notificationHandlers;

	private Map<Integer, CancelMonitor> cancelMonitors = new HashMap<>();
	private ExecutorService executor;

	protected LSPServer(){
		GsonBuilder builder = new GsonBuilder();
		gson = builder.registerTypeAdapter(Message.class,new MessageJSONHandler())
				.create();
	}

	public void connect() throws IOException{
		this.notificationHandlers = buildNotificationHandlers();
		this.requestHandlers = buildRequestHandlers();

		final String stdInName = System.getenv("STDIN_PIPE_NAME");
		final String stdOutName = System.getenv("STDOUT_PIPE_NAME");
		if (stdInName == null || stdOutName == null) {
			//XXX temporary hack to let unit tests run
			System.err.println("Unable to connect to named pipes");
			return;
		}
		initExecutor();
		connection = new NamedPipeConnection(stdOutName, stdInName);
		connection.start();
		startDispatching();
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
		TransportMessage tm = new TransportMessage(gson.toJson(message));
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
			return gson.fromJson(message.getContent(), Message.class);
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
