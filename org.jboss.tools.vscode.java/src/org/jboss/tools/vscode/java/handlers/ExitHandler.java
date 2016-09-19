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
