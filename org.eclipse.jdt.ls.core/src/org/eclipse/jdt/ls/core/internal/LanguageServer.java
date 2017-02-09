/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.IOException;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class LanguageServer implements IApplication {

	private volatile boolean shutdown;
	private long parentProcessId;

	@Override
	public Object start(IApplicationContext context) throws Exception {
		JavaLanguageServerPlugin.startLanguageServer(this);

		while (!shutdown && parentProcessStillRunning()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// Not expected. Continue.
				e.printStackTrace();
			}
		}
		return IApplication.EXIT_OK;
	}

	/**
	 * Checks whether the parent process is still running.
	 * If not, then we assume it has crashed, and we have to terminate the Java Language Server.
	 *
	 * @return true iff the parent process is still running
	 */
	private boolean parentProcessStillRunning() {
		// Wait until parent process id is available
		long parentProcessId = getParentProcessId();
		if (parentProcessId == 0) return true;

		String command;
		if (Platform.OS_WIN32.equals(Platform.getOS())) {
			command = "cmd /c \"tasklist /FI \"PID eq " + parentProcessId + "\" | findstr " + parentProcessId + "\"";
		} else {
			command = "ps -p " + parentProcessId;
		}
		try {
			Process process = Runtime.getRuntime().exec(command);
			int processResult = process.waitFor();
			return processResult == 0;
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return true;
		}
	}

	@Override
	public void stop() {
		System.out.println("Stopping language server");
	}

	public void shutdown() {
		System.out.println("Shutting down language server");
		shutdown = true;
	}

	public synchronized long getParentProcessId() {
		return parentProcessId;
	}

	public synchronized void setParentProcessId(long processId) {
		parentProcessId = processId;
	}

}
