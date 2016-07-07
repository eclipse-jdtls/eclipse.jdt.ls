package org.jboss.tools.vscode.ipc;

import java.io.BufferedOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JList;

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
	
	public static final boolean DEBUG = true;
	public static final boolean PROTOCOL_LOG = Boolean.valueOf(System.getProperty("log.protocol", "false")).booleanValue();
	
	private Transport transport;
	private List<RequestHandler> handlers = new ArrayList<RequestHandler>();
	static FileWriter logWriter;
	static FileWriter protocolLog;

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
			log("Error parsing: " + data);
			// TODO Auto-generated catch block
		}
	}
	
	public void sendNotification(JSONRPC2Notification notification){
		log("Sending notification: "+notification);
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
		log("No handlers for request: " + request);	
	}

	private void notificationDataReceived(JSONRPC2Notification request) {
		for (RequestHandler requestHandler : handlers) {
			if(requestHandler.canHandle(request.getMethod())){
				requestHandler.process(request);
				return;
			}
		}
		log("No handlers for request: " + request);		
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
		sendNotification(note);
	}
	
	public static void log(String log) {
		if(!DEBUG) return;
		try {
			if (logWriter == null) {
				Path cwd = Paths. get(System.getProperty("user.home"));
				Path logPath = cwd.toAbsolutePath().normalize().resolve("langserver.log");
				logWriter = new FileWriter(logPath.toFile());
			}
			logWriter.write(log + "\n");
			logWriter.flush();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		

	public static void plog (String buf){
		if(PROTOCOL_LOG)
			plog(buf.toCharArray());
	}
	
	public static void plog (char buf){
		if(PROTOCOL_LOG)
			plog(new char[] {buf});
	}
	
	public static void plog(char[] buf ){
		if(!PROTOCOL_LOG) return;
		try {
			if (protocolLog == null) {
				Path cwd = Paths. get(System.getProperty("user.home"));
				Path logPath = cwd.toAbsolutePath().normalize().resolve("protocol.log");
				protocolLog = new FileWriter(logPath.toFile());
			}
			protocolLog.write(buf);
			protocolLog.flush();
		}
		catch (IOException e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}

}
