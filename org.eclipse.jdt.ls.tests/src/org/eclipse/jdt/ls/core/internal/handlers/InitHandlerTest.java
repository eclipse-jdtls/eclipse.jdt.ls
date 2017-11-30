/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeConfigurationCapabilities;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.ExecuteCommandCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author snjeza
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class InitHandlerTest extends AbstractProjectsManagerBasedTest {

	protected JDTLanguageServer server;
	protected JDTLanguageServer protocol;

	@Mock
	private JavaLanguageClient client;

	@Before
	public void setup() throws Exception {
		server = new JDTLanguageServer(projectsManager, preferenceManager);
		server.connectClient(client);
		protocol = JavaLanguageServerPlugin.getInstance().getProtocol();
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
	}

	@After
	public void tearDown() {
		server.disconnectClient();
		JavaLanguageServerPlugin.getInstance().setProtocol(protocol);
		try {
			projectsManager.setAutoBuilding(true);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	@Test
	public void testExecuteCommandProvider() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isExecuteCommandDynamicRegistrationSupported()).thenReturn(Boolean.FALSE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		InitializeResult result = initialize(false);
		List<String> commands = result.getCapabilities().getExecuteCommandProvider().getCommands();
		assertFalse(commands.isEmpty());
	}

	@Test
	public void testExecuteCommandProviderDynamicRegistration() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isExecuteCommandDynamicRegistrationSupported()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		InitializeResult result = initialize(true);
		assertNull(result.getCapabilities().getExecuteCommandProvider());
	}

	@Test
	public void testRegisterDelayedCapability() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		when(mockCapabilies.isDocumentSymbolDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isWorkspaceSymbolDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isDocumentSymbolDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isCodeActionDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isDefinitionDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isHoverDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isReferencesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(mockCapabilies.isDocumentHighlightDynamicRegistered()).thenReturn(Boolean.TRUE);
		InitializeResult result = initialize(true);
		assertNull(result.getCapabilities().getDocumentSymbolProvider());
		DidChangeConfigurationParams params = new DidChangeConfigurationParams();
		server.didChangeConfiguration(params);
		verify(client, times(7)).registerCapability(any());
	}

	private InitializeResult initialize(boolean dynamicRegistration) throws InterruptedException, ExecutionException {
		InitializeParams params = new InitializeParams();
		ClientCapabilities capabilities = new ClientCapabilities();
		WorkspaceClientCapabilities workspaceCapabilities = new WorkspaceClientCapabilities();
		workspaceCapabilities.setDidChangeConfiguration(new DidChangeConfigurationCapabilities(dynamicRegistration));
		ExecuteCommandCapabilities executeCommand = new ExecuteCommandCapabilities(dynamicRegistration);
		workspaceCapabilities.setExecuteCommand(executeCommand);
		capabilities.setWorkspace(workspaceCapabilities);
		TextDocumentClientCapabilities textDocument = new TextDocumentClientCapabilities();
		capabilities.setTextDocument(textDocument);
		params.setCapabilities(capabilities);
		CompletableFuture<InitializeResult> result = server.initialize(params);
		return result.get();
	}
}

