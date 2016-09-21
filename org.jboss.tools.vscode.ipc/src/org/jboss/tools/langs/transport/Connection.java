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

import java.io.IOException;
/**
 * The connection implementation used by the server.
 *
 * @author Gorkem Ercan
 *
 */
public interface Connection {

	/**
	 * Sends the specified message.
	 *
	 * @param message
	 *            to send
	 */
	void send(TransportMessage message);

	/**
	 * Takes the next available message,
	 * this method blocks until a new message is available.
	 *
	 * @return
	 * @throws InterruptedException
	 */
	TransportMessage take() throws InterruptedException;

	/**
	 * Starts up the transport and acquire all needed resources. Does nothing if
	 * the connection has already been started.
	 *
	 * @throws IOException
	 */
	void start() throws IOException;

	/**
	 * Shuts down the transport freeing all acquired resources. Does nothing if
	 * the connection has already been shut down.
	 */
	void close();

}
