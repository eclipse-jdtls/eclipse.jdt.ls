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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.core.runtime.IProgressMonitor;

public class TestDelegateCommandHandlerFactory implements IExecutableExtensionFactory {

	private static class TestCommandHandler implements IDelegateCommandHandler {

		public static TestCommandHandler getInstance() {
			return new TestCommandHandler();
		}

		private int state = 0;

		@Override
		public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) {
			if ("testcommand.throwexception".equals(commandId)) {
				throw new UnsupportedOperationException("Unsupported");
			}
			return commandId + ": " + arguments.stream().map(arg -> arg.toString()).reduce("", String::concat) + state++;
		}
	}

	@Override
	public Object create() throws CoreException {
		return TestCommandHandler.getInstance();
	}
}
