package org.jboss.tools.vscode.java;

import java.io.IOException;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class LanguageServer implements IApplication {

	private volatile boolean shutdown;
	private long parentProcessId;

	@Override
	public Object start(IApplicationContext context) throws Exception {
		JavaLanguageServerPlugin.languageServer = this;
		
		JavaLanguageServerPlugin.getContext().getBundle().start();

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

	public void setParentProcessId(long processId) {
		parentProcessId = processId;
	}

}
