/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;

import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

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
