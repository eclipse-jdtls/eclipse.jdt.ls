package org.jboss.tools.vscode.ipc;

public interface RequestHandler<R,S> {
	
	public boolean canHandle(String request);
	public S handle(R param);
	
}
