package org.jboss.tools.langs.base;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.jboss.tools.langs.CancelParams;
import org.jboss.tools.langs.transport.Connection;
import org.jboss.tools.langs.transport.Connection.MessageListener;
import org.jboss.tools.langs.transport.NamedPipeConnection;
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

public class LSPClient implements MessageListener{
	
	public interface MessageCallback{
		void messageReceived(Message message);
	}
	
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
			}
			if(object.has("method")){
				subType = RequestMessage.class;
			}
			return context.deserialize(jsonElement, new ParameterizedTypeImpl(subType,CancelParams.class));
		}

		@Override
		public JsonElement serialize(Message message, Type type, JsonSerializationContext context) {
			ParameterizedType parameterizedType = null ;
			if(message instanceof NotificationMessage)
				parameterizedType = new ParameterizedTypeImpl(NotificationMessage.class,((NotificationMessage<?>)message).getParams().getClass());
			if(message instanceof ResponseMessage)
				parameterizedType = new ParameterizedTypeImpl(ResponseMessage.class,((ResponseMessage<?>)message).getResult().getClass());
			if(message instanceof RequestMessage)
				parameterizedType = new ParameterizedTypeImpl(RequestMessage.class,((RequestMessage<?>)message).getParams().getClass());
			if(type == null)
				throw new RuntimeException("Unrecognized message type");
			return context.serialize(message,parameterizedType);
		}
		
	}
	private Connection connection;
	private MessageCallback callback;
	private final Gson gson;
	private static LSPClient instance;
	
	private LSPClient(){
		GsonBuilder builder = new GsonBuilder();
		gson = builder.registerTypeAdapter(Message.class,new MessageJSONHandler())
				.create();
		
		//use builder
	}
	
	public static LSPClient getInstance(){
		if(instance == null ){
			instance = new LSPClient();
		}
		return instance;
	}
	
	public void connect(MessageCallback listener) throws LSPClientException{
		final String stdInName = System.getenv("STDIN_PIPE_NAME");
		final String stdOutName = System.getenv("STDOUT_PIPE_NAME");

		this.callback = listener;
		connection = new NamedPipeConnection(stdOutName, stdInName);
		connection.setMessageListener(this);
		try {
			connection.start();
		} catch (IOException e) {
			throw new LSPClientException(e);
		}
	}
	
	public void send (Message message){
		TransportMessage tm = new TransportMessage(gson.toJson(message));
		connection.send(tm);
	}

	@Override
	public void messageReceived(TransportMessage message) {
		Message msg = gson.fromJson(message.getContent(),Message.class);
		callback.messageReceived(msg);
	}
	
	public void shutdown(){
		connection.close();
	}

}
