package org.jboss.tools.vscode.ipc;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Message;

public interface Transport {

	public interface DataListener{
		public void dataReceived(String data);
	}
	
	public void addListener(DataListener listener);
	public void removeListener(DataListener listener);
	public void send(JSONRPC2Message response);
	
}
