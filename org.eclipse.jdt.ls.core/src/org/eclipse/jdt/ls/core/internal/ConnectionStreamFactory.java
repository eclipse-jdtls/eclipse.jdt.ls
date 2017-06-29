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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.channels.Channels;

import org.eclipse.core.runtime.Platform;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

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


	protected final class NamedPipeStreamProvider implements StreamProvider {

		private final String readFileName;
		private final String writeFileName;

		public NamedPipeStreamProvider(String readFileName, String writeFileName) {
			this.readFileName = readFileName;
			this.writeFileName = writeFileName;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			final File rFile = new File(readFileName);
			if (isWindows()) {
				RandomAccessFile readFile = new RandomAccessFile(rFile, "rwd");
				return Channels.newInputStream(readFile.getChannel());
			} else {
				AFUNIXSocket readSocket = AFUNIXSocket.newInstance();
				readSocket.connect(new AFUNIXSocketAddress(rFile));
				return readSocket.getInputStream();
			}
		}
		@Override
		public OutputStream getOutputStream() throws IOException {
			final File wFile = new File(writeFileName);

			if (isWindows()) {
				RandomAccessFile writeFile = new RandomAccessFile(wFile, "rwd");
				return Channels.newOutputStream(writeFile.getChannel());
			} else {
				AFUNIXSocket writeSocket = AFUNIXSocket.newInstance();
				writeSocket.connect(new AFUNIXSocketAddress(wFile));
				return writeSocket.getOutputStream();
			}
		}
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
			return JavaLanguageServerPlugin.getIn();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.ls.core.internal.ConnectionStreamFactory.StreamProvider#getOutputStream()
		 */
		@Override
		public OutputStream getOutputStream() throws IOException {
			return JavaLanguageServerPlugin.getOut();
		}

	}

	private StreamProvider provider;

	/**
	 *
	 * @return
	 */
	public StreamProvider getSelectedStream() {
		if (provider == null) {
			final String stdInName = Environment.get("STDIN_PIPE_NAME");
			final String stdOutName = Environment.get("STDOUT_PIPE_NAME");
			if (stdInName != null && stdOutName != null) {
				provider = new NamedPipeStreamProvider(stdOutName, stdInName);
			}
			final String host = Environment.get("CLIENT_HOST", "localhost");
			final String port = Environment.get("CLIENT_PORT");
			if (port != null) {
				provider = new SocketStreamProvider(host, Integer.parseInt(port));
			}
			if (provider == null) {//Fall back to std io
				provider = new StdIOStreamProvider();
			}
		}
		return provider;
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
