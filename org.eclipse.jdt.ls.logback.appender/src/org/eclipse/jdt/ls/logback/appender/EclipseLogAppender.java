/*******************************************************************************
 * Copyright (c) 2010, 2021 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.m2e.logback.appender.EclipseLogAppender
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.logback.appender;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

public class EclipseLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
	private static final String BUNDLE_ID = "org.eclipse.jdt.ls.logback.appender";

	private static final ILog ECLIPSE_LOG = Platform.getLog(EclipseLogAppender.class);

	@Override
	protected void append(ILoggingEvent logEvent) {
		if (logEvent.getFormattedMessage().contains("org.eclipse.m2e.core.internal.markers.IEditorMarkerService")) {
			// ignore m2e editor markers
			return;
		}
		int severity;
		switch (logEvent.getLevel().levelInt) {
			case Level.ERROR_INT:
				severity = IStatus.ERROR;
				break;
			case Level.WARN_INT:
				severity = IStatus.WARNING;
				break;
			case Level.INFO_INT:
			case Level.DEBUG_INT:
				severity = IStatus.INFO;
				break;
			default:
				return;
		}
		IStatus status = new Status(severity, BUNDLE_ID, logEvent.getFormattedMessage(), getThrowable(logEvent));
		ECLIPSE_LOG.log(status);
	}

	private static Throwable getThrowable(ILoggingEvent logEvent) {
		if (logEvent.getThrowableProxy() instanceof ThrowableProxy) {
			return ((ThrowableProxy) logEvent.getThrowableProxy()).getThrowable();
		}
		Object[] args = logEvent.getArgumentArray();
		if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable) {
			return (Throwable) args[args.length - 1];
		}
		return null;
	}
}
