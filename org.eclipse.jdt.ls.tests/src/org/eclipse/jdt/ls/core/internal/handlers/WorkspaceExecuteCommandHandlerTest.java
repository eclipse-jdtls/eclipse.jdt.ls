/*******************************************************************************
 * Copyright (c) 2017, 2023 Microsoft Corporation and others.
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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class WorkspaceExecuteCommandHandlerTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testExecuteCommand() {
		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("testcommand1");
		params.setArguments(List.of("hello", "world"));
		Object result = handler.executeCommand(params, monitor);
		assertEquals("testcommand1: helloworld0", result);

		params.setCommand("testcommand2");
		result = handler.executeCommand(params, monitor);
		assertEquals("testcommand2: helloworld1", result);
	}

	@Test
	public void testExecuteCommandNonexistingCommand() {
		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("testcommand.not.existing");
		params.setArguments(List.of("hello", "world"));
		ResponseErrorException ex = assertThrows(ResponseErrorException.class, () -> handler.executeCommand(params, monitor));
		assertEquals("No delegateCommandHandler for testcommand.not.existing", ex.getMessage());
	}

	@Test
	public void testExecuteCommandMorethanOneCommand() {
		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("dup");
		ResponseErrorException ex = assertThrows(ResponseErrorException.class, () -> handler.executeCommand(params, monitor));
		assertTrue(ex.getMessage().startsWith("Found multiple delegateCommandHandlers"));
	}

	@Test
	public void testExecuteCommandThrowsExceptionCommand() {
		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setCommand("testcommand.throwexception");
		ResponseErrorException ex = assertThrows(ResponseErrorException.class, () -> handler.executeCommand(params, monitor));
		assertEquals("Unsupported", ex.getMessage());
	}

	@Test
	public void testExecuteCommandInvalidParameters() {
		WorkspaceExecuteCommandHandler handler = WorkspaceExecuteCommandHandler.getInstance();
		ExecuteCommandParams params = null;
		ResponseErrorException ex = assertThrows(ResponseErrorException.class, () -> handler.executeCommand(params, monitor));
		assertEquals("The workspace/executeCommand has empty params or command", ex.getMessage());
	}

	@Test
	public void testRegistryEventListener() throws Exception {
		loadBundles(List.of(getBundle("testresources", "jdt.ls.extension-0.0.1.jar")));
		String bundleLocation = getBundleLocation(getBundle("testresources", "jdt.ls.extension-0.0.1.jar"), true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		try {
			assertNotNull(installedBundle);

			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			installedBundle.loadClass("jdt.ls.extension.Activator");
			assertEquals(Bundle.ACTIVE, installedBundle.getState());

			Set<String> extensionCommands = WorkspaceExecuteCommandHandler.getInstance().getAllCommands();
			assertTrue(extensionCommands.contains("jdt.ls.extension.command1"));
			assertTrue(extensionCommands.contains("jdt.ls.extension.command2"));

			loadBundles(List.of(getBundle("testresources", "jdt.ls.extension-0.0.2.jar")));
			bundleLocation = getBundleLocation(getBundle("testresources", "jdt.ls.extension-0.0.2.jar"), true);

			installedBundle = context.getBundle(bundleLocation);
			assertNotNull(installedBundle);
			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			installedBundle.loadClass("jdt.ls.extension.Activator");
			assertEquals(Bundle.ACTIVE, installedBundle.getState());

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
