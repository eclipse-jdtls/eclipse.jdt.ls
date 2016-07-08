package org.jboss.tools.vscode.ipc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.tools.vscode.ipc.Transport.DataListener;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Message;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

/**
 * 
 * @author Gorkem Ercan
 *
 */
public class JsonRpcConnection implements DataListener{
	
	public static final boolean PROTOCOL_LOG = Boolean.valueOf(System.getProperty("log.protocol", "false")).booleanValue();
	
	private Transport transport;
	private List<RequestHandler> handlers = new ArrayList<RequestHandler>();

	public JsonRpcConnection(Transport transport){
		this.transport = transport;
	}
	
	public void connect(){
		this.transport.addListener(this);
	}
	
	public void addHandlers(List<RequestHandler> h){
		this.handlers.addAll(h);
	}

	@Override
	public void dataReceived(final String data) {
		try {
			JSONRPC2Message request = JSONRPC2Message.parse(data);
			if(request instanceof JSONRPC2Notification){
				notificationDataReceived((JSONRPC2Notification)request);
			}
			else{
				requestDataReceived((JSONRPC2Request)request);
			}
		} catch (JSONRPC2ParseException e) {
			IPCPlugin.logException("Error parsing: " + data, e);
			// TODO Auto-generated catch block
		}
	}
	
	public void sendNotification(JSONRPC2Notification notification){
		transport.send(notification);
	}
	
	private void requestDataReceived(JSONRPC2Request request) {
		for (RequestHandler requestHandler : handlers) {
			if(requestHandler.canHandle(request.getMethod())){
				JSONRPC2Response response = requestHandler.process(request);
				transport.send(response);
				return;
			}
		}
		IPCPlugin.logError("No handlers for request: " + request);	
	}

	private void notificationDataReceived(JSONRPC2Notification request) {
		for (RequestHandler requestHandler : handlers) {
			if(requestHandler.canHandle(request.getMethod())){
				requestHandler.process(request);
				return;
			}
		}
		IPCPlugin.logError("No handlers for request: " + request);		
	}

}
