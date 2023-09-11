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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

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

	protected final class PipeStreamProvider implements StreamProvider {

		private InputStream input;
		private OutputStream output;

		public PipeStreamProvider() {
			initializeNamedPipe();
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return input;
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			return output;
		}

		private void initializeNamedPipe() {
			File pipeFile = getPipeFile();
			if (pipeFile != null) {
				if (isWindows()) {
					try {
						AsynchronousFileChannel channel = AsynchronousFileChannel.open(pipeFile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
						input = new NamedPipeInputStream(channel);
						output = new NamedPipeOutputStream(channel);
					} catch (IOException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
				} else {
					UnixDomainSocketAddress socketAddress = UnixDomainSocketAddress.of(pipeFile.toPath());
					try {
						SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
						channel.connect(socketAddress);
						input = new NamedPipeInputStream(channel);
						output = new NamedPipeOutputStream(channel);
					} catch (IOException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
				}
			}
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

	public class NamedPipeInputStream extends InputStream {

		private ReadableByteChannel unixChannel;
		private AsynchronousFileChannel winChannel;
		private ByteBuffer buffer = ByteBuffer.allocate(1024);
		private int readyBytes = 0;

		public NamedPipeInputStream(ReadableByteChannel channel) {
			this.unixChannel = channel;
		}

		public NamedPipeInputStream(AsynchronousFileChannel channel) {
			this.winChannel = channel;
		}

		@Override
		public int read() throws IOException {
			if (buffer.position() < readyBytes) {
				return buffer.get() & 0xFF;
			}
			try {
				buffer.clear();
				if (winChannel != null) {
					readyBytes = winChannel.read(buffer, 0).get();
				} else {
					readyBytes = unixChannel.read(buffer);
				}
				if (readyBytes == -1) {
					return -1; // EOF
				}
				buffer.flip();
				return buffer.get() & 0xFF;
			} catch (InterruptedException | ExecutionException e) {
				throw new IOException(e);
			}
		}
	}

	public class NamedPipeOutputStream extends OutputStream {

		private WritableByteChannel unixChannel;
		private AsynchronousFileChannel winChannel;
		private ByteBuffer buffer = ByteBuffer.allocate(1);

		public NamedPipeOutputStream(WritableByteChannel channel) {
			this.unixChannel = channel;
		}

		public NamedPipeOutputStream(AsynchronousFileChannel channel) {
			this.winChannel = channel;
		}

		@Override
		public void write(int b) throws IOException {
			buffer.clear();
			buffer.put((byte) b);
			buffer.position(0);
			if (winChannel != null) {
				Future<Integer> result = winChannel.write(buffer, 0);
				try {
					result.get();
				} catch (Exception e) {
					throw new IOException(e);
				}
			} else {
				unixChannel.write(buffer);
			}
		}

		@Override
		public void write(byte[] b) throws IOException {
			final int BUFFER_SIZE = 1024;
			int blocks = b.length / BUFFER_SIZE;
			int writeBytes = 0;
			for (int i = 0; i <= blocks; i++) {
				int offset = i * BUFFER_SIZE;
				int length = Math.min(b.length - writeBytes, BUFFER_SIZE);
				if (length <= 0) {
					break;
				}
				writeBytes += length;
				ByteBuffer buffer = ByteBuffer.wrap(b, offset, length);
				if (winChannel != null) {
					Future<Integer> result = winChannel.write(buffer, 0);
					try {
						result.get();
					} catch (Exception e) {
						throw new IOException(e);
					}
				} else {
					unixChannel.write(buffer);
				}
			}
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
		File pipeFile = getPipeFile();
		if (pipeFile != null) {
			return new PipeStreamProvider();
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

	/**
	 * Interactions (eg. exists()) with the pipe file (eg. named pipes on Windows)
	 * prior to establishing a connection may close it and cause failure
	 *
	 * @return a File representing the named pipe (Windows) / unix socket for
	 *         communication with the client.
	 */
	private static File getPipeFile() {
		Optional<String[]> procArgs = ProcessHandle.current().info().arguments();
		String[] arguments = new String[0];
		if (procArgs.isPresent()) {
			arguments = procArgs.get();
		} else {
			// a new-line separated list of all command-line arguments passed in when launching Eclipse
			String eclipseCommands = System.getProperty("eclipse.commands");
			if (eclipseCommands != null) {
				arguments = eclipseCommands.split("\n");
			}
		}
		Optional<String> pipeArgs = Stream.of(arguments).filter(arg -> arg.contains("--pipe=")).findFirst();
		if (pipeArgs.isPresent()) {
			String pipeArg = pipeArgs.get();
			String pipeFile = pipeArg.substring(pipeArg.indexOf('=') + 1);
			return new File(pipeFile);
		}
		return null;
	}

}
