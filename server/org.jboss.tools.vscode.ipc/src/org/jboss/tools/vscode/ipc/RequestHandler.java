package org.jboss.tools.vscode.ipc;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public interface RequestHandler<R,S> {
	
	public boolean canHandle(String request);
	@Deprecated
	public JSONRPC2Response process(JSONRPC2Request request);
	@Deprecated
	public void process(JSONRPC2Notification request);
	public S handle(R param);
	
}
