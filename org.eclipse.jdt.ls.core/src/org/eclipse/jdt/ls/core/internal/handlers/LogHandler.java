/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.LogReader;
import org.eclipse.jdt.ls.core.internal.LogReader.LogEntry;
import org.eclipse.jdt.ls.core.internal.managers.TelemetryEvent;
import org.eclipse.lsp4j.MessageType;

import com.google.gson.JsonObject;

/**
 * The LogHandler hooks in the Eclipse log and forwards all Eclipse log messages to
 * the the client. In VSCode you can see all the messages in the Output view, in the
 * 'Java Language Support' channel.
 */
public class LogHandler {

	/**
	 * The filter that decide whether an Eclipse log message gets forwarded to the client
	 * via {@link org.eclipse.lsp4j.services.LanguageClient#logMessage(org.eclipse.lsp4j.MessageParams)}
	 * <p>Clients who load the LS in same process can override the default log handler.
	 * This usually needs to be done very early, before the language server starts.</p>
	 */
	public static Predicate<IStatus> defaultLogFilter = new DefaultLogFilter();
	private static final String JAVA_ERROR_LOG = "java.ls.error";

	private ILogListener logListener;
	private DateFormat dateFormat;
	private int logLevelMask;
	private JavaClientConnection connection;
	private Predicate<IStatus> filter;

	private String firstRecordedEntryDateString;

	private List<IStatus> statusCache = new ArrayList<>();
	private Set<Integer> knownErrors = new HashSet<>();

	/**
	 * Equivalent to <code>LogHandler(defaultLogFilter)</code>.
	 */
	public LogHandler() {
		this(defaultLogFilter);
	}

	public LogHandler(Predicate<IStatus> filter) {
		this.filter = filter;
	}

	public void install() {
		this.dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
		this.logLevelMask = getLogLevelMask(System.getProperty("log.level", ""));//Empty by default

		this.logListener = new ILogListener() {
			@Override
			public void logging(IStatus status, String bundleId) {
				String dateString = dateFormat.format(new Date());
				if (firstRecordedEntryDateString == null) {
					firstRecordedEntryDateString = dateString;
				}
				if (connection == null) {
					statusCache.add(status);
				} else {
					processLogMessage(status);
				}
			}
		};
		Platform.addLogListener(this.logListener);
	}

	public void setClientConnection(JavaClientConnection clientConnection) {
		this.connection = clientConnection;

		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		File workspaceFile = root.getRawLocation().makeAbsolute().toFile();
		Path serverLogPath = Paths.get(workspaceFile.getAbsolutePath(), ".metadata", ".log");

		List<LogEntry> entries = LogReader.parseLogFile(serverLogPath.toFile(), this.firstRecordedEntryDateString);

		for (IStatus event : statusCache) {
			processLogMessage(event);
		}
		statusCache.clear();

		for (LogEntry e : entries) {
			processLogMessage(e);
		}
	}

	private int getLogLevelMask(String logLevel) {
		switch (logLevel) {
		case "ALL":
			return -1;
		case "ERROR":
			return IStatus.ERROR;
		case "INFO":
			return IStatus.ERROR | IStatus.WARNING | IStatus.INFO;
		case "WARNING":
		default:
			return IStatus.ERROR | IStatus.WARNING;

		}
	}

	private void processLogMessage(LogEntry entry) {
		String dateString = this.dateFormat.format(entry.getDate());
		String message = entry.getMessage() + '\n' + entry.getStack();

		connection.logMessage(getMessageTypeFromSeverity(entry.getSeverity()), dateString + ' ' + message);
		// Send a trace event to client
		if (entry.getSeverity() == IStatus.ERROR) {
			JsonObject properties = new JsonObject();
			properties.addProperty("message", redact(entry.getMessage()));
			if (entry.getStack() != null) {
				properties.addProperty("exception", message);
			}
			int hashCode = properties.hashCode();
			if (!knownErrors.contains(hashCode)) {
				knownErrors.add(hashCode);
				connection.telemetryEvent(new TelemetryEvent(JAVA_ERROR_LOG, properties));
			}
		}
	}
	private void processLogMessage(IStatus status) {
		if ((filter != null && !filter.test(status)) || !status.matches(this.logLevelMask)) {
			//no op;
			return;
		}
		String dateString = this.dateFormat.format(new Date());
		String message = status.getMessage();
		if (status.getException() != null) {
			message = message + '\n' + status.getException().getMessage();
			StringWriter sw = new StringWriter();
			status.getException().printStackTrace(new PrintWriter(sw));
			String exceptionAsString = sw.toString();
			message = message + '\n' + exceptionAsString;
		}

		connection.logMessage(getMessageTypeFromSeverity(status.getSeverity()), dateString + ' ' + message);
		// Send a trace event to client
		if (status.getSeverity() == IStatus.ERROR) {
			JsonObject properties = new JsonObject();
			properties.addProperty("message", redact(status.getMessage()));
			if (status.getException() != null) {
				properties.addProperty("exception", message);
			}
			int hashCode = properties.hashCode();
			if (!knownErrors.contains(hashCode)) {
				knownErrors.add(hashCode);
				connection.telemetryEvent(new TelemetryEvent(JAVA_ERROR_LOG, properties));
			}
		}
	}

	private String redact(String message) {
		if (message == null) {
			return null;
		}

		if (message.startsWith("Error occured while building workspace.")) {
			return "Error occured while building workspace.";
		}

		return message;
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

}
