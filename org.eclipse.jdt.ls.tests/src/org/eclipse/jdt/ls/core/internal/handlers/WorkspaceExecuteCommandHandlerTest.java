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

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Set;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

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

	@Test
	public void testRegistryEventListener() throws Exception {
		loadBundles(Arrays.asList(getBundle("testresources", "jdt.ls.extension-0.0.1.jar")));
		String bundleLocation = getBundleLocation(getBundle("testresources", "jdt.ls.extension-0.0.1.jar"), true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		try {
			assertNotNull(installedBundle);

			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			installedBundle.loadClass("jdt.ls.extension.Activator");
			assertEquals(installedBundle.getState(), Bundle.ACTIVE);

			Set<String> extensionCommands = WorkspaceExecuteCommandHandler.getInstance().getAllCommands();
			assertTrue(extensionCommands.contains("jdt.ls.extension.command1"));
			assertTrue(extensionCommands.contains("jdt.ls.extension.command2"));

			loadBundles(Arrays.asList(getBundle("testresources", "jdt.ls.extension-0.0.2.jar")));
			bundleLocation = getBundleLocation(getBundle("testresources", "jdt.ls.extension-0.0.2.jar"), true);

			installedBundle = context.getBundle(bundleLocation);
			assertNotNull(installedBundle);
			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			installedBundle.loadClass("jdt.ls.extension.Activator");
			assertEquals(installedBundle.getState(), Bundle.ACTIVE);

			extensionCommands = WorkspaceExecuteCommandHandler.getInstance().getAllCommands();
			assertTrue(extensionCommands.contains("jdt.ls.extension.command2"));
			assertTrue(extensionCommands.contains("jdt.ls.extension.command3"));
			assertFalse(extensionCommands.contains("jdt.ls.extension.command1"));
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			installedBundle.uninstall();
		}
	}
}
