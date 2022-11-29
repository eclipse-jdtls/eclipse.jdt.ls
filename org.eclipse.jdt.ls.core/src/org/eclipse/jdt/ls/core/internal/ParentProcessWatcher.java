/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.core.runtime.Platform;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;

import com.google.common.io.Closeables;

/**
 * Watches the parent process PID and invokes exit if it is no longer available.
 * This implementation waits for periods of inactivity to start querying the PIDs.
 */
public final class ParentProcessWatcher implements Runnable, Function<MessageConsumer, MessageConsumer>{

	private static final long INACTIVITY_DELAY_SECS = 30 *1000;
	private static final boolean isJava1x = System.getProperty("java.version").startsWith("1.");
	private static final int POLL_DELAY_SECS = 10;
	private volatile long lastActivityTime;
	private final LanguageServerApplication server;
	private ScheduledFuture<?> task;
	private ScheduledExecutorService service;

	public ParentProcessWatcher(LanguageServerApplication server) {
		this.server = server;
		if (ProcessHandle.current().parent().isPresent()) {
			this.server.setParentProcessId(ProcessHandle.current().parent().get().pid());
		}
		service = Executors.newScheduledThreadPool(1);
		task =  service.scheduleWithFixedDelay(this, POLL_DELAY_SECS, POLL_DELAY_SECS, TimeUnit.SECONDS);
	}

	@Override
	public void run() {
		if (!parentProcessStillRunning()) {
			JavaLanguageServerPlugin.logInfo("Parent process stopped running, forcing server exit");
			task.cancel(true);
			if (JavaLanguageServerPlugin.getInstance() != null && JavaLanguageServerPlugin.getInstance().getProtocol() instanceof org.eclipse.lsp4j.services.LanguageServer languageServer) {
				languageServer.exit();
			} else {
				server.exit();
				Executors.newSingleThreadScheduledExecutor().schedule(() -> {
					logInfo("Forcing exit after 1 min.");
					System.exit(BaseJDTLanguageServer.FORCED_EXIT_CODE);
				}, 1, TimeUnit.MINUTES);
			}
		}
	}

	/**
	 * Checks whether the parent process is still running.
	 * If not, then we assume it has crashed, and we have to terminate the Java Language Server.
	 *
	 * @return true if the parent process is still running
	 */
	private boolean parentProcessStillRunning() {
		// Wait until parent process id is available
		final long pid = server.getParentProcessId();
		if (pid == 0 || lastActivityTime > (System.currentTimeMillis() - INACTIVITY_DELAY_SECS)) {
			return true;
		}

		try {
			return ProcessHandle.of(pid).isPresent();
		} catch (UnsupportedOperationException | SecurityException e) {
			JavaLanguageServerPlugin.logException("Unable to determine process state, fallback behaviour", e);
			// Unable to determine process state, fallback behaviour
		}

		String command;
		if (Platform.OS_WIN32.equals(Platform.getOS())) {
			command = "cmd /c \"tasklist /FI \"PID eq " + pid + "\" | findstr " + pid + "\"";
		} else {
			command = "kill -0 " + pid;
		}
		Process process = null;
		boolean finished = false;
		try {
			process = Runtime.getRuntime().exec(command);
			finished = process.waitFor(POLL_DELAY_SECS, TimeUnit.SECONDS);
			if (!finished) {
				process.destroy();
				finished = process.waitFor(POLL_DELAY_SECS, TimeUnit.SECONDS); // wait for the process to stop
			}
			if (Platform.OS_WIN32.equals(Platform.getOS()) && finished && process.exitValue() > 1) {
				// the tasklist command should return 0 (parent process exists) or 1 (parent process doesn't exist)
				JavaLanguageServerPlugin.logInfo("The tasklist command: '" + command + "' returns " + process.exitValue());
				return true;
			}
			return !finished || process.exitValue() == 0;
		} catch (IOException | InterruptedException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return true;
		} finally {
			if (process != null) {
				if (!finished) {
					process.destroyForcibly();
				}
				// Terminating or destroying the Process doesn't close the process handle on Windows.
				// It is only closed when the Process object is garbage collected (in its finalize() method).
				// On Windows, when the Java LS is idle, we need to explicitly request a GC,
				// to prevent an accumulation of zombie processes, as finalize() will be called.
				if (Platform.OS_WIN32.equals(Platform.getOS())) {
					// Java >= 9 doesn't close the handle when the process is garbage collected
					// We need to close the opened streams
					if (!isJava1x) {
						Closeables.closeQuietly(process.getInputStream());
						Closeables.closeQuietly(process.getErrorStream());
						try {
							Closeables.close(process.getOutputStream(), false);
						} catch (IOException e) {
						}
					}
					System.gc();
				}
			}
		}
	}

	@Override
	public MessageConsumer apply(final MessageConsumer consumer) {
		//inject our own consumer to refresh the timestamp
		return message -> {
			lastActivityTime=System.currentTimeMillis();
			consumer.consume(message);
		};
	}
}
