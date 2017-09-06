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

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.junit.Test;

public class WorkspaceExecuteCommandHandlerTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testExecuteCommand() {
		WorkspaceExecuteCommandHandler handler = new WorkspaceExecuteCommandHandler();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("testcommand1");
		params.setArguments(Arrays.asList("hello", "world"));
		Object result = handler.executeCommand(params, monitor);
		assertEquals("testcommand1: helloworld0", result);

		params.setCommand("testcommand2");
		result = handler.executeCommand(params, monitor);
		assertEquals("testcommand2: helloworld1", result);
	}

	@Test
	public void testExecuteCommandNonexistingCommand() {
		WorkspaceExecuteCommandHandler handler = new WorkspaceExecuteCommandHandler();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("testcommand.not.existing");
		params.setArguments(Arrays.asList("hello", "world"));
		Object result = handler.executeCommand(params, monitor);
		assertEquals("No delegateCommandHandler for testcommand.not.existing", result);
	}

	@Test(expected = IllegalStateException.class)
	public void testExecuteCommandMorethanOneCommand() {
		WorkspaceExecuteCommandHandler handler = new WorkspaceExecuteCommandHandler();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("dup");
		handler.executeCommand(params, monitor);
	}

	@Test
	public void testExecuteCommandThrowsExceptionCommand() {
		WorkspaceExecuteCommandHandler handler = new WorkspaceExecuteCommandHandler();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("testcommand.throwexception");

		ILogListener testListener = new ILogListener() {
			@Override
			public void logging(IStatus status, String plugin) {
				assertTrue(status.getException() instanceof UnsupportedOperationException);
			}
		};

		Platform.addLogListener(testListener);

		Object result = handler.executeCommand(params, monitor);
		assertNull(result);

		Platform.removeLogListener(testListener);
	}

	@Test
	public void testExecuteCommandInvalidParameters() {
		WorkspaceExecuteCommandHandler handler = new WorkspaceExecuteCommandHandler();
		ExecuteCommandParams params = null;
		Object result = handler.executeCommand(params, monitor);
		assertEquals(result, "The workspace/executeCommand has empty params or command");

		params = new ExecuteCommandParams();
		result = handler.executeCommand(params, monitor);
		assertEquals(result, "The workspace/executeCommand has empty params or command");
	}
}
