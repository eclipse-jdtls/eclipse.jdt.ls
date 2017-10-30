/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public interface WorkspaceFoldersProposedClient {

	/**
	 * The workspace/workspaceFolders request is sent from the server to the client
	 * to fetch the current open list of workspace folders.
	 *
	 * @return Returns the current open list of workspace folders. Returns null in
	 *         the response if only a single file is open in the tool. Returns an
	 *         empty array if a workspace is open but no folders are configured.
	 */
	@JsonRequest("workspace/workspaceFolders")
	CompletableFuture<WorkspaceFolder[]> getWorkspaceFolders();

}
