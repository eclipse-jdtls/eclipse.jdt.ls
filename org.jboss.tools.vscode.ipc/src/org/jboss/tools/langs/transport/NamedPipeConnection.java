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
package org.jboss.tools.langs.transport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.charset.Charset;

import org.jboss.tools.vscode.internal.ipc.IPCPlugin;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

public class NamedPipeConnection extends AbstractConnection {
	
	
	private class ReaderThread extends Thread{
		
		private final InputStream stream;
		
		public ReaderThread(InputStream input ) {
			super("LSP_ReaderThread");
			this.stream = input;
		}
		
		@Override
		public void run() {
			startDispatcherThread();
			try {
				startWriterThread();
			} catch (IOException e1) {
				//TODO: write failed to connect.
			}
			
			while(true){
				TransportMessage message;
				try {
					message = TransportMessage.fromStream(stream, DEFAULT_CHARSET);
					if(message == null ){
						//Stream disconnected exit reader thread
						IPCPlugin.logError("Empty message read");
						break;
					}
					inboundQueue.add(message);
				} catch (IOException e) {
					//continue
				}
			}
		}
	}
	
	private class WriterThread extends Thread{
		
		private final OutputStream stream;
		
		public WriterThread(OutputStream output) {
			super("LSP_WriterThread");
			this.stream = output;
		} 
		
		@Override
		public void run() {
			while (true) {
				try {
					TransportMessage message = outboundQueue.take();
					message.send(stream, DEFAULT_CHARSET);
					stream.flush();
				} catch (InterruptedException e) {
					break;//exit
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	private static String OS = System.getProperty("os.name").toLowerCase();
	private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
	
	private final String readFileName;
	private final String writeFileName;
	
	//used for windows
	private RandomAccessFile writeFile;
	private RandomAccessFile readFile;
	// used on POSIX
	private AFUNIXSocket writeSocket;
	private AFUNIXSocket readSocket;
	
	public NamedPipeConnection(String readFileName, String writeFileName) {
		this.readFileName = readFileName;
		this.writeFileName = writeFileName;
	}

	@Override
	public void send(TransportMessage message) {
		if(message != null)
			outboundQueue.add(message);
	}

	@Override
	public void start() throws IOException {
		InputStream stream = connectReadChannel();
		ReaderThread readerThread = new ReaderThread(stream);
		readerThread.setDaemon(true);
		readerThread.start();
	}
	
	public void startWriterThread() throws IOException{
		OutputStream stream = connectWriteChannel();
		WriterThread writerThread = new WriterThread(stream);
		writerThread.setDaemon(true);
		writerThread.start();
	}
	
	@Override
	public void close() {
		try {
			if (writeFile != null) 
				writeFile.close();
			if (readFile != null) 
				readFile.close();
			if(readSocket != null)
				readSocket.close();
			if(writeSocket != null )
				writeSocket.close();
		} catch (IOException e) {
			// TODO: handle exception
		}
	}
	
	private InputStream connectReadChannel() throws IOException{
		final File rFile = new File(readFileName);
		if(isWindows()){
			readFile = new RandomAccessFile(rFile, "rwd");
			return Channels.newInputStream(readFile.getChannel());
		}else{
			readSocket = AFUNIXSocket.newInstance();
			readSocket.connect(new AFUNIXSocketAddress(rFile));
			return readSocket.getInputStream();
		}
	}
	
	private  OutputStream connectWriteChannel() throws IOException{
		final File wFile = new File(writeFileName);
		
		if(isWindows()){
			writeFile = new RandomAccessFile(wFile, "rwd");
			return Channels.newOutputStream(writeFile.getChannel());
		}else{
			writeSocket = AFUNIXSocket.newInstance();
			writeSocket.connect(new AFUNIXSocketAddress(wFile));
			return writeSocket.getOutputStream();
		}
	}
	
	private boolean isWindows() {
        return (OS.indexOf("win") >= 0);
	}

}
