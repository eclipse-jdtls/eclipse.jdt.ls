/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.debug.IDebugServer;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

public class StartDebugSessionHandler {
	private static final String EXTENSIONPOINT_ID = "org.eclipse.jdt.ls.core.debugserver";

	public CompletableFuture<String> startDebugServer(String type) {
		return CompletableFutures.computeAsync(cm -> {
			if (type != null && type.equals("vscode.java.debugsession")) {
				// Find the Java DebugServer implementor and start it.
				IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSIONPOINT_ID);
				for (IConfigurationElement e : elements) {
					if ("java".equals(e.getAttribute("type"))) {
						final String[] serverPort = new String[] { "" };
						SafeRunner.run(new ISafeRunnable() {
							@Override
							public void run() throws Exception {
								final IDebugServer debugServer = (IDebugServer) e.createExecutableExtension("class");
								debugServer.start();
								serverPort[0] = String.valueOf(debugServer.getPort());
							}
							@Override
							public void handleException(Throwable ex) {
								IStatus status = new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, IStatus.OK, "Error in JDT Core during launching debug server", ex); //$NON-NLS-1$
								JavaLanguageServerPlugin.log(status);
							}
						});
						return serverPort[0];
					}
				}
				return "";
			} else {
				return "";
			}
		});
	}

}