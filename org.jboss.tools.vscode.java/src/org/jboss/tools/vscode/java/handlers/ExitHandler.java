package org.jboss.tools.vscode.java.handlers;

import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;

public class ExitHandler implements RequestHandler<Object, Object> {

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.EXIT.getMethod().equals(request);
	}

	@Override
	public Object handle(Object param) {
		JavaLanguageServerPlugin.logInfo("Exiting Java Language Server");
		System.exit(0);
		return null;
	}

}
