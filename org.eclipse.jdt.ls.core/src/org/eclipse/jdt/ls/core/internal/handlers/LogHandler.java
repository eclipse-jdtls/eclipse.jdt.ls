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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.function.Predicate;

import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.lsp4j.MessageType;

/**
 * The LogHandler hooks in the Eclipse log and forwards all Eclipse log messages to
 * the the client. In VSCode you can see all the messages in the Output view, in the
 * 'Java Language Support' channel.
 */
public class LogHandler {

	private ILogListener logListener;
	private DateFormat dateFormat;
	private int logLevelMask;
	private JavaClientConnection connection;
	private Predicate<IStatus> filter;

	public LogHandler() {
		this(new DefaultLogFilter());
	}

	public LogHandler(Predicate<IStatus> filter) {
		this.filter = filter;
	}

	public void install(JavaClientConnection rcpConnection) {
		this.dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
		this.logLevelMask = getLogLevelMask(System.getProperty("log.level", ""));//Empty by default
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
