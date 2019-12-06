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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableList;

public class ExecuteClientCommandTest {

	private JavaLanguageClient client = mock(JavaLanguageClient.class);
	private JavaClientConnection javaClient = new JavaClientConnection(client);

	@Test
	public void testExecuteClientCommandNoArgs() throws Exception {
		when(client.executeClientCommand(any())).thenAnswer(handler("send.it.back", params -> params.getArguments()));
		Object response = javaClient.executeClientCommand("send.it.back");
		assertEquals(ImmutableList.of(), response);
	}

	@Test
	public void testExecuteClientCommandNoArgsAndLongEnoughTimeout() throws Exception {
		when(client.executeClientCommand(any())).thenAnswer(handler("send.it.back", params -> params.getArguments()));
		Object response = javaClient.executeClientCommand(Duration.ofDays(1), "send.it.back");
		assertEquals(ImmutableList.of(), response);
	}

	@Test
	public void testExecuteClientCommandThrows() throws Exception {
		when(client.executeClientCommand(any())).thenAnswer(handler(params -> {
			throw new IllegalArgumentException("BOOM!");
		}));
		try {
			javaClient.executeClientCommand("whatever");
			fail("Should have thrown");
		} catch (Throwable e) {
			e = getDeepestCause(e);
			assertEquals("BOOM!", e.getMessage());
		}
	}

	@Test
	public void testExecuteClientCommandThrowsAndLongEnoughTimeout() throws Exception {
		when(client.executeClientCommand(any())).thenAnswer(handler(params -> {
			throw new IllegalArgumentException("BOOM!");
		}));
		try {
			javaClient.executeClientCommand(Duration.ofDays(1), "whatever");
			fail("Should have thrown");
		} catch (Throwable e) {
			e = getDeepestCause(e);
			assertEquals("BOOM!", e.getMessage());
		}
	}

	@Test
	public void testExecuteClientCommandSomeArgs() throws Exception {
		when(client.executeClientCommand(any())).thenAnswer(handler("send.it.back", params -> params.getArguments()));
		Object[] params = { "one", 2, ImmutableList.of(3) };
		Object response = javaClient.executeClientCommand("send.it.back", params);
		assertEquals(ImmutableList.copyOf(params), response);
	}

	@Test
	public void testExecuteClientCommandSomeArgsAndLongEnoughTimeout() throws Exception {
		when(client.executeClientCommand(any())).thenAnswer(handler("send.it.back", params -> params.getArguments()));
		Object[] params = { "one", 2, ImmutableList.of(3) };
		Object response = javaClient.executeClientCommand(Duration.ofDays(1), "send.it.back", params);
		assertEquals(ImmutableList.copyOf(params), response);
	}

	@Test
	public void testExecuteClientCommandTimesOut() throws Exception {
		when(client.executeClientCommand(any())).thenReturn(new CompletableFuture<>()); //Future never resolves
		try {
			javaClient.executeClientCommand(Duration.ofMillis(10), "whatever");
			fail("Should have thrown");
		} catch (Throwable e) {
			assertEquals(TimeoutException.class, e.getClass());
		}
	}

	///////////////////// Harness, helper, setup etc. below /////////////////////////////////////

	interface SyncHandler {
		Object executeClientCommand(ExecuteCommandParams params);
	}

	private static <T> Answer<T> handler(String command, SyncHandler h) {
		return handler((ExecuteCommandParams params) -> {
			if (params.getCommand().equals(command)) {
				return h.executeClientCommand(params);
			}
			throw new IllegalArgumentException("Unknown command: " + params.getCommand());
		});
	}

	/**
	 * Convenience method to wrap a 'nice', type-checked SyncHandler into a mockito
	 * {@link Answer}.
	 */
	private static <T> Answer<T> handler(SyncHandler h) {
		return new Answer<T>() {
			@SuppressWarnings("unchecked")
			@Override
			public T answer(InvocationOnMock invocation) throws Throwable {
				try {
					Object[] args = invocation.getArguments();
					assertEquals(1, args.length);
					ExecuteCommandParams params = (ExecuteCommandParams) args[0];
					return (T) CompletableFuture.completedFuture(h.executeClientCommand(params));
				} catch (Throwable e) {
					CompletableFuture<T> fail = new CompletableFuture<>();
					fail.completeExceptionally(e);
					return (T) fail;
				}
			}
		};
	}

	private static Throwable getDeepestCause(Throwable e) {
		Throwable cause = e;
		Throwable parent = e.getCause();
		while (parent != null && parent != e) {
			cause = parent;
			parent = cause.getCause();
		}
		return cause;
	}
}