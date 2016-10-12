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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.jboss.tools.langs.base.ResponseError.ReservedCode;
import org.jboss.tools.langs.transport.TransportMessage;

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
 * Helper for marshaling or unmarshaling JSON RPC messages.
 *
 * @author Gorkem Ercan
 *
 */
public class JSONHelper {

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
			if(!object.has("method")){
				throw new LSPException(ReservedCode.METHOD_NOT_FOUND.code(),  "no method is not handled ", null, null);
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

	private final Gson gson;

	public JSONHelper(){
		gson = new GsonBuilder().
				registerTypeAdapter(Message.class,new MessageJSONHandler())
				.create();
	}

	/**
	 * Converts a message to a JSON string.
	 *
	 * @param message
	 * @return json
	 * @throws IllegalArgumentException  if message is null
	 */
	public String toJson(Message message){
		if(message == null )
			throw new IllegalArgumentException("Message can not be null");
		return gson.toJson(message);
	}

	/**
	 * Converts to contents of a transport message to a
	 * message
	 * @param transportMessage
	 * @return message
	 * @throws IllegalArgumentException if message is null
	 */
	public Message fromJson(TransportMessage message){
		if(message == null )
			throw new IllegalArgumentException("Message can not be null");
		return gson.fromJson(message.getContent(),Message.class);
	}

}
