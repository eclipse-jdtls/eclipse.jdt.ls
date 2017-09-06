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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;

public class TestDelegateCommandHandlerFactory implements IExecutableExtensionFactory {

	private static class TestCommandHandler implements IDelegateCommandHandler {

		public static TestCommandHandler getInstance() {
			return new TestCommandHandler();
		}

		private int state = 0;

		@Override
		public Object executeCommand(String commandId, List<Object> arguments) {
			if ("testcommand.throwexception".equals(commandId)) {
				throw new UnsupportedOperationException();
			}
			return commandId + ": " + arguments.stream().map(arg -> arg.toString()).reduce("", String::concat) + state++;
		}
	}

	@Override
	public Object create() throws CoreException {
		return TestCommandHandler.getInstance();
	}
}
