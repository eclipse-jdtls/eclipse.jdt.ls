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
package org.eclipse.jdt.ls.core.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.eclipse.core.runtime.Platform;

/**
 * A factory for creating the streams for supported transmission methods.
 *
 * @author Gorkem Ercan
 *
 */
public class ConnectionStreamFactory {

	interface StreamProvider {
		InputStream getInputStream() throws IOException;

		OutputStream getOutputStream() throws IOException;
	}

	protected final class SocketStreamProvider implements StreamProvider {
		private final String host;
		private final int port;
		private InputStream fInputStream;
		private OutputStream fOutputStream;

		public SocketStreamProvider(String host, int port) {
			this.host = host;
			this.port = port;
		}

		private void initializeConnection() throws IOException {
			Socket socket = new Socket(host, port);
			fInputStream = socket.getInputStream();
			fOutputStream = socket.getOutputStream();
		}

		@Override
		public InputStream getInputStream() throws IOException {
			if (fInputStream == null) {
				initializeConnection();
			}
			return fInputStream;
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			if (fOutputStream == null) {
				initializeConnection();
			}
			return fOutputStream;
		}
	}

	protected final class StdIOStreamProvider implements StreamProvider {

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.ls.core.internal.ConnectionStreamFactory.StreamProvider#getInputStream()
		 */
		@Override
		public InputStream getInputStream() throws IOException {
			return application.getIn();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.ls.core.internal.ConnectionStreamFactory.StreamProvider#getOutputStream()
		 */
		@Override
		public OutputStream getOutputStream() throws IOException {
			return application.getOut();
		}

	}

	private StreamProvider provider;
	private LanguageServerApplication application;

	public ConnectionStreamFactory(LanguageServerApplication languageServer) {
		this.application = languageServer;
	}

	/**
	 *
	 * @return
	 */
	public StreamProvider getSelectedStream() {
		if (provider == null) {
			provider = createProvider();
		}
		return provider;
	}

	private StreamProvider createProvider() {
		Integer port = JDTEnvironmentUtils.getClientPort();
		if (port != null) {
			return new SocketStreamProvider(JDTEnvironmentUtils.getClientHost(), port);
		}
		return new StdIOStreamProvider();
	}

	public InputStream getInputStream() throws IOException {
		return getSelectedStream().getInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		return getSelectedStream().getOutputStream();
	}

	protected static boolean isWindows() {
		return Platform.OS_WIN32.equals(Platform.getOS());
	}

}
