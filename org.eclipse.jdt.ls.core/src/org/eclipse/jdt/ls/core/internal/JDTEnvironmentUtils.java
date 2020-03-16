/*******************************************************************************
 * Copyright (c) 2018 TypeFox and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     TypeFox - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.core.runtime.Platform;

import com.google.common.base.Preconditions;

public class JDTEnvironmentUtils {

	public static final String CLIENT_PORT = "CLIENT_PORT";
	public static final String CLIENT_HOST = "CLIENT_HOST";
	public static final String DEFAULT_CLIENT_HOST = "localhost";
	public static final String SYNTAX_SERVER_ID = "syntaxserver";

	/**
	 * Environment variable indicating that the JDT LS has to be started with a
	 * socket stream. In this case, it is the JDT LS that starts and waits until the
	 * client connects to it.
	 */
	public static final String SOCKET_STREAM_DEBUG = "socket.stream.debug";

	/**
	 * Returns with the client port if set. Otherwise, returns with {@code null}.
	 * Throw an {@link IllegalStateException} if the port is set but it has an
	 * invalid port number.
	 *
	 * When the client port environment variable is set to a valid port number, then
	 * plain socket communication will be used between the language client and the
	 * server instead of the standard IO stream one.
	 */
	public static Integer getClientPort() {
		final String port = Environment.get(CLIENT_PORT);
		if (port != null) {
			int clientPort = Integer.parseInt(port);
			Preconditions.checkState(clientPort >= 1 && clientPort <= 65535, "The port must be an integer between 1 and 65535. It was: '" + port + "'.");
			return clientPort;
		}
		return null;
	}

	/**
	 * Returns with the client host. Defaults to {@code localhost} if not set. Has
	 * absolutely no effect, if this is set but the {@code CLIENT_PORT} is not.
	 */
	public static String getClientHost() {
		return Environment.get(CLIENT_HOST);
	}

	/**
	 * Has no effect, and always returns with {@code false} if the JDT LS has been
	 * started from the source either in debug or development mode. If the
	 * {@code CLIENT_HOST} and {@code CLIENT_PORT} environment variables are set and
	 * the {@link JDTEnvironmentUtils#SOCKET_STREAM_DEBUG socket.stream.debug} is
	 * set to {@code true}, the the server will start with plain socket connection
	 * and will wait until the client connects.
	 */
	public static boolean inSocketStreamDebugMode() {
		return Boolean.parseBoolean(Environment.get(SOCKET_STREAM_DEBUG, "false")) && (Platform.inDebugMode() || Platform.inDevelopmentMode()) && getClientHost() != null && getClientPort() != null;
	}

	public static boolean isSyntaxServer() {
		return Boolean.parseBoolean(Environment.get(SYNTAX_SERVER_ID, "false"));
	}
}
