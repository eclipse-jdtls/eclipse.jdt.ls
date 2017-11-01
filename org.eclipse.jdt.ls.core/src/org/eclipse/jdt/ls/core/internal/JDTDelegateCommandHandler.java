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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.commands.OrganizeImportsCommand;

public class JDTDelegateCommandHandler implements IDelegateCommandHandler {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler#executeCommand(java.lang.String, java.util.List, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
		if (!StringUtils.isBlank(commandId)) {
			switch (commandId) {
				case "java.edit.organizeImports":
					return OrganizeImportsCommand.organizeImports(arguments);
				default:
					break;
			}
		}
		throw new UnsupportedOperationException(String.format("Java language server doesn't support the command '%s'.", commandId));
	}
}
