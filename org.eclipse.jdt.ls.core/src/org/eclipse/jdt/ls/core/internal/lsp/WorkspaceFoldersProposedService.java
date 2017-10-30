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

import java.util.UUID;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

/**
 * Server APIs for Workspace folders (prop
 * https://github.com/Microsoft/vscode-languageserver-node/blob/master/protocol/src/protocol.workspaceFolders.proposed.md
 *
 */
@JsonSegment("workspace")
public interface WorkspaceFoldersProposedService {

	public static final String CAPABILITY_NAME = "workspace/didChangeWorkspaceFolders";
	public static final String CAPABILITY_ID = UUID.randomUUID().toString();

	/**
	 * The workspace/didChangeWorkspaceFolders notification is sent from the client
	 * to the server to inform the client about workspace folder configuration
	 * changes.
	 *
	 * @param documentUri
	 *            the document from which the project configuration will be updated
	 */
	@JsonNotification
	void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params);



}

