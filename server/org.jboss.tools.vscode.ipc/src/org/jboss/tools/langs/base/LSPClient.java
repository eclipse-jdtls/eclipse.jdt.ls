package org.jboss.tools.langs.base;

import java.io.IOException;

import org.jboss.tools.langs.transport.Connection;
import org.jboss.tools.langs.transport.Connection.MessageListener;
import org.jboss.tools.langs.transport.NamedPipeConnection;
import org.jboss.tools.langs.transport.TransportMessage;

import com.google.gson.Gson;

public class LSPClient implements MessageListener{
	
	public interface MessageCallback{
		void messageReceived(Message message);
	}
	
	private Connection connection;
	private MessageCallback callback;
	private final Gson gson = new Gson();
	private static LSPClient instance;
	
	private LSPClient(){
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
