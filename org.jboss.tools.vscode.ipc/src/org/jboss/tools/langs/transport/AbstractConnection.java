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
/**
 * Abstract implementation of a Connection that
 * implements queues.
 * @author Gorkem
 *
 */
public abstract class AbstractConnection implements Connection {

	protected final BlockingQueue<TransportMessage> inboundQueue = new LinkedBlockingQueue<>();
	protected final BlockingQueue<TransportMessage> outboundQueue = new LinkedBlockingQueue<>();


	@Override
	public void send(TransportMessage message) {
		if(message != null)
			outboundQueue.add(message);
	}

	/* (non-Javadoc)
	 * @see org.jboss.tools.langs.transport.Connection#take()
	 */
	@Override
	public TransportMessage take() throws InterruptedException {
		return inboundQueue.take();
	}

}
