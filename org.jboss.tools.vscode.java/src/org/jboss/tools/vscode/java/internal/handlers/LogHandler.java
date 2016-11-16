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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.Calendar;

import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.lsp4j.MessageType;
import org.jboss.tools.vscode.java.internal.JavaClientConnection;

/**
 * The LogHandler hooks in the Eclipse log and forwards all Eclipse log messages to
 * the the client. In VSCode you can see all the messages in the Output view, in the
 * 'Java Language Support' channel.
 */
public class LogHandler {

	// if set, the all log entries will be written to USER_DIR/langserver.log as well.
	public static final boolean LOG_TO_FILE = true;

	private ILogListener logListener;
	private FileWriter logWriter;
	private DateFormat dateFormat;
	private int logLevelMask;
	private JavaClientConnection connection;
	private Calendar calendar;

	public void install(JavaClientConnection rcpConnection) {
		this.dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
		this.logLevelMask = getLogLevelMask(System.getProperty("log.level", ""));//Empty by default
		this.calendar = Calendar.getInstance();
		this.connection = rcpConnection;

		this.logListener = new ILogListener() {
			@Override
			public void logging(IStatus status, String bundleId) {
				processLogMessage(status);
			}
		};
		Platform.addLogListener(this.logListener);
	}

	public void uninstall() {
		Platform.removeLogListener(this.logListener);
		if (this.logWriter != null) {
			try {
				this.logWriter.close();
			} catch (IOException e) {
				// ignore
			}
			this.logWriter = null;
		}
	}

	private int getLogLevelMask(String logLevel) {
		switch (logLevel) {
		case "ALL":
			return -1;
		case "ERROR":
			return IStatus.ERROR;
		case "WARNING":
		default:
			return IStatus.ERROR | IStatus.WARNING;

		}
	}

	private void processLogMessage(IStatus status) {
		String dateString = this.dateFormat.format(this.calendar.getTime());
		String message = status.getMessage();
		if (status.getException() != null) {
			message = message + '\n' + status.getException().getMessage();
			StringWriter sw = new StringWriter();
			status.getException().printStackTrace(new PrintWriter(sw));
			String exceptionAsString = sw.toString();
			message = message + '\n' + exceptionAsString;
		}

		if (status.matches(this.logLevelMask)) {
			connection.logMessage(getMessageTypeFromSeverity(status.getSeverity()), dateString + ' '+ message);
		}
		if (LOG_TO_FILE) {
			// log all messages
			logToFile("[" + getLabelFromSeverity(status.getSeverity()) + "] " + dateString + ' ' + message);
		}
	}

	private MessageType getMessageTypeFromSeverity(int severity) {
		switch (severity) {
		case IStatus.ERROR:
			return MessageType.Error;
		case IStatus.WARNING:
			return MessageType.Warning;
		case IStatus.INFO:
			return MessageType.Info;
		default:
			return MessageType.Log;
		}
	}

	private String getLabelFromSeverity(int severity) {
		switch (severity) {
		case IStatus.ERROR:
			return "error";
		case IStatus.WARNING:
			return "warning";
		case IStatus.INFO:
			return "info";
		case IStatus.OK:
			return "ok";
		default:
			return "cancel";
		}
	}

	private void logToFile(String log) {
		try {
			if (this.logWriter == null) {
				Path cwd = Paths.get(System.getProperty("user.home"));
				Path logPath = cwd.toAbsolutePath().normalize().resolve("langserver.log");
				this.logWriter = new FileWriter(logPath.toFile());
			}
			this.logWriter.write(log + "\n");
			this.logWriter.flush();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
