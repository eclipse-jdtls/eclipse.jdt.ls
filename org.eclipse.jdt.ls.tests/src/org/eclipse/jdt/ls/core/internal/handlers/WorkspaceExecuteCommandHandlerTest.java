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

import java.util.Arrays;

import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class WorkspaceExecuteCommandHandlerTest extends AbstractProjectsManagerBasedTest {

	@Rule
	public ExpectedException expectedEx = ExpectedException.none();

	@Test
	public void testExecuteCommand() {
		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
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
		expectedEx.expect(ResponseErrorException.class);
		expectedEx.expectMessage("No delegateCommandHandler for testcommand.not.existing");

		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("testcommand.not.existing");
		params.setArguments(Arrays.asList("hello", "world"));
		Object result = handler.executeCommand(params, monitor);
	}

	@Test
	public void testExecuteCommandMorethanOneCommand() {
		expectedEx.expect(ResponseErrorException.class);
		expectedEx.expectMessage(new Matcher<String>() {

			@Override
			public void describeTo(Description description) {
			}

			@Override
			public boolean matches(Object item) {
				return ((String) item).startsWith("Found multiple delegateCommandHandlers");
			}

			@Override
			public void describeMismatch(Object item, Description mismatchDescription) {

			}

			@Override
			public void _dont_implement_Matcher___instead_extend_BaseMatcher_() {
			}

		});

		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("dup");
		handler.executeCommand(params, monitor);
	}

	@Test
	public void testExecuteCommandThrowsExceptionCommand() {
		expectedEx.expect(ResponseErrorException.class);
		expectedEx.expectMessage("Unsupported");

		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("testcommand.throwexception");
		handler.executeCommand(params, monitor);
	}

	@Test
	public void testExecuteCommandInvalidParameters() {
		expectedEx.expect(ResponseErrorException.class);
		expectedEx.expectMessage("The workspace/executeCommand has empty params or command");

		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
		ExecuteCommandParams params = null;
		handler.executeCommand(params, monitor);
	}
}
