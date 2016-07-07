package org.jboss.tools.langs.transport;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractConnection implements Connection {

	private static final Logger LOGGER = Logger.getLogger(AbstractConnection.class.getName());

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
							listener.messageReceived(message);
						} catch (Exception e) {
							LOGGER.log(Level.SEVERE, "Exception on incoming message dispatcher", e);
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
