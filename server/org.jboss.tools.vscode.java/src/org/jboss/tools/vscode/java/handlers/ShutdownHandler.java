package org.jboss.tools.vscode.java.handlers;

import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;

public class ShutdownHandler implements RequestHandler<Object, Object> {


	@Override
	public boolean canHandle(String request) {
		return LSPMethods.SHUTDOWN.getMethod().equals(request);
	}

	@Override
	public Object handle(Object param) {
		JavaLanguageServerPlugin.logInfo("Shutting down Java Language Server");
		JavaLanguageServerPlugin.getLanguageServer().shutdown();
		return new Object();
	}

}
