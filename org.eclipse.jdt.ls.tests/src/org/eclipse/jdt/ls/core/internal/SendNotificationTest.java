/*******************************************************************************
 * Copyright (c) 2018 Pivotal Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Pivotal Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.Closeable;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Semaphore;

import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.lsp.ExecuteCommandProposedClient;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.gson.JsonPrimitive;

public class SendNotificationTest {
	private ExecuteCommandProposedClient client;
	private ExecuteCommandProposedClient clientConnection;
	private Closeable[] closeables;

	@Before
	public void setUp() throws IOException {
		this.client = mock(ExecuteCommandProposedClient.class);

		PipedOutputStream clientWritesTo = new PipedOutputStream();
		PipedInputStream clientReadsFrom = new PipedInputStream();
		PipedInputStream serverReadsFrom = new PipedInputStream();
		PipedOutputStream serverWritesTo = new PipedOutputStream();

		serverWritesTo.connect(clientReadsFrom);
		clientWritesTo.connect(serverReadsFrom);

		this.closeables = new Closeable[] { clientWritesTo, clientReadsFrom, serverReadsFrom, serverWritesTo };

		Launcher<JavaLanguageClient> serverLauncher = Launcher.createLauncher(new Object(), JavaLanguageClient.class, serverReadsFrom, serverWritesTo);
		serverLauncher.startListening();
		Launcher<LanguageServer> clientLauncher = Launcher.createLauncher(client, LanguageServer.class, clientReadsFrom, clientWritesTo);
		clientLauncher.startListening();

		this.clientConnection = serverLauncher.getRemoteProxy();
	}

	@After
	public void tearDown() {
		for (Closeable closeable : closeables) {
			try {
				closeable.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Test
	public void testNotifyNoArgs() throws Exception {
		clientConnection.sendNotification(new ExecuteCommandParams("custom", Collections.emptyList()));
		verify(client, timeout(1000)).sendNotification(eq(new ExecuteCommandParams("custom", Collections.emptyList())));
	}

	@Test
	public void testNotify() throws Exception {
		clientConnection.sendNotification(new ExecuteCommandParams("custom", Arrays.asList("foo", "bar")));
		verify(client, timeout(1000)).sendNotification(eq(new ExecuteCommandParams("custom", Arrays.asList(new JsonPrimitive("foo"), new JsonPrimitive("bar")))));
	}

	@Test
	public void testNotifyWithException() throws InterruptedException {
		Semaphore waiter = new Semaphore(1);
		waiter.acquire();
		boolean[] wasThrown = new boolean[1];
		doAnswer(i -> {
			try {
				throw new NullPointerException();
			} finally {
				wasThrown[0] = true;
				waiter.release();
			}
		}).when(client).sendNotification((any()));
		clientConnection.sendNotification(new ExecuteCommandParams("custom", Arrays.asList("foo", "bar")));
		verify(client, timeout(1000)).sendNotification(any());
		waiter.acquire();
		waiter.release();
		assertTrue(wasThrown[0]);
	}

	@Test
	public void testNotifyWithWait() throws Exception {
		Semaphore waiter = new Semaphore(1);
		boolean[] wasCalled = new boolean[1];
		waiter.acquire();
		try {
			doAnswer(new Answer<Void>() {

				@Override
				public Void answer(InvocationOnMock invocation) throws Throwable {
					waiter.acquire();
					wasCalled[0] = true;
					return null;
				}
			}).when(client).sendNotification(any());
			clientConnection.sendNotification(new ExecuteCommandParams("custom", Arrays.asList("foo", "bar")));
			verify(client, timeout(1000)).sendNotification(any());
			assertFalse(wasCalled[0]);
		} finally {
			waiter.release();
		}
	}

}