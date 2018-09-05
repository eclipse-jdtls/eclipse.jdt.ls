/*******************************************************************************
 * Copyright (c) 2018 Pivotal Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public interface ExecuteCommandProposedClient {

	@JsonRequest("workspace/executeClientCommand")
	CompletableFuture<Object> executeClientCommand(ExecuteCommandParams params);

	@JsonNotification("workspace/notify")
	void sendNotification(ExecuteCommandParams params);

}
