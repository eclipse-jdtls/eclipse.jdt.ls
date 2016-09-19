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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.jboss.tools.vscode.internal.ipc.IPCPlugin;

public abstract class AbstractConnection implements Connection {

	private MessageListener listener;

	protected final BlockingQueue<TransportMessage> inboundQueue = new LinkedBlockingQueue<TransportMessage>();
	protected final BlockingQueue<TransportMessage> outboundQueue = new LinkedBlockingQueue<TransportMessage>();

	/**
	 * Dispatches the messages received
	 */
	private class DispatcherThread extends Thread {
		public DispatcherThread() {
			super("LSP_DispatcherThread");
		}
			
		@Override
		public void run() {
			TransportMessage message;
			try {
				while (true) {
					message = inboundQueue.take();
					if (listener != null) {
						try {
							IPCPlugin.logInfo("Dispatch incoming" + message.getContent());
							listener.messageReceived(message);
						} catch (Exception e) {
							IPCPlugin.logException("Exception on incoming message dispatcher", e);
						}
					}
				}
			} catch (InterruptedException e) {
				// stop the dispatcher thread
			}
		}
	}

	@Override
	public void setMessageListener(MessageListener listener) {
		if (this.listener != null && this.listener == listener) {
			throw new IllegalStateException("Can not set listener multiple times");
		}
		this.listener = listener;
	}
	
	
	/**
	 * Must be called by implementers to start dispatching of incoming messages.
	 */
	protected void startDispatcherThread() {
		DispatcherThread dispatcherThread;
		dispatcherThread = new DispatcherThread();
		dispatcherThread.setDaemon(true);
		dispatcherThread.start();
	}
}
