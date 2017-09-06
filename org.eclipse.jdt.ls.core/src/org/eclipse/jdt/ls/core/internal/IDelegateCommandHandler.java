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

package org.eclipse.jdt.ls.core.internal;

import java.util.List;

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
	 * @return execute command result
	 */
	Object executeCommand(String commandId, List<Object> arguments);
}
