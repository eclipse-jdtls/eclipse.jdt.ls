/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Interface for command handler to delegate the workspace/executeCommand
 *
 * A delegate that extends the core language server to execute command
 */
public interface IDelegateCommandHandler {
	/**
	 * Language server to execute commands. One handler can handle multiple
	 * commands.
	 *
	 * @param commandId
	 *            the command ID for the execute command
	 * @param arguments
	 *            list of arguments passed to the delegate command handler
	 * @param monitor
	 *            monitor of the activity progress
	 * @return execute command result
	 * @throws Exception
	 *             the unhandled exception will be wrapped in
	 *             <code>org.eclipse.lsp4j.jsonrpc.ResponseErrorException</code>
	 *             and be wired back to the JSON-RPC protocol caller
	 */
	Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception;
}
