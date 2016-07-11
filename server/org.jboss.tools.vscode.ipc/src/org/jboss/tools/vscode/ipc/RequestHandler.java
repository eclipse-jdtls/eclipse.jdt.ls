package org.jboss.tools.vscode.ipc;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public interface RequestHandler {
	
	public boolean canHandle(String request);
	public JSONRPC2Response process(JSONRPC2Request request);
	public void process(JSONRPC2Notification request);
}
