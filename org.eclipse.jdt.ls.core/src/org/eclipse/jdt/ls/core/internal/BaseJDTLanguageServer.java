/*******************************************************************************
 * Copyright (c) 2016-2020 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     Microsoft Corporation - extract to a base class
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

public class BaseJDTLanguageServer {
	/**
	 * Exit code returned when JDTLanguageServer is forced to exit.
	 */
	public static final int FORCED_EXIT_CODE = 1;
	protected JavaClientConnection client;
	protected boolean shutdownReceived = false;
	private Set<String> registeredCapabilities = new HashSet<>(3);

	public void connectClient(JavaLanguageClient client) {
		this.client = new JavaClientConnection(client);
	}

	public JavaClientConnection getClientConnection() {
		return client;
	}

	public LanguageServerWorkingCopyOwner getWorkingCopyOwner() {
		return null;
	}

	public void sendStatus(ServiceStatus serverStatus, String status) {
		if (client != null) {
			client.sendStatus(serverStatus, status);
		}
	}

	public void unregisterCapability(String id, String method) {
		if (client != null && registeredCapabilities.remove(id)) {
			Unregistration unregistration = new Unregistration(id, method);
			UnregistrationParams unregistrationParams = new UnregistrationParams(Collections.singletonList(unregistration));
			client.unregisterCapability(unregistrationParams);
		}
	}

	public void registerCapability(String id, String method) {
		registerCapability(id, method, null);
	}

	public void registerCapability(String id, String method, Object options) {
		if (client != null && registeredCapabilities.add(id)) {
			Registration registration = new Registration(id, method, options);
			RegistrationParams registrationParams = new RegistrationParams(Collections.singletonList(registration));
			client.registerCapability(registrationParams);
		}
	}

	protected void toggleCapability(boolean enabled, String id, String capability, Object options) {
		if (enabled) {
			registerCapability(id, capability, options);
		} else {
			unregisterCapability(id, capability);
		}
	}

	protected <R> CompletableFuture<R> computeAsync(Function<IProgressMonitor, R> code) {
		return CompletableFutures.computeAsync(cc -> code.apply(toMonitor(cc)));
	}

	protected IProgressMonitor toMonitor(CancelChecker checker) {
		return new CancellableProgressMonitor(checker);
	}
}
