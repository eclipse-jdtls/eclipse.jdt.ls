/*******************************************************************************
 * Copyright (c) 2017-2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class JDTLanguageServerTest {

	private JDTLanguageServer server;

	@Spy
	private PreferenceManager prefManager;

	@Mock
	private ProjectsManager projManager;

	@Mock
	private JavaLanguageClient client;

	@Mock
	private ClientPreferences clientPreferences;

	@Before
	public void setUp() throws Exception {
		when(prefManager.getClientPreferences()).thenReturn(clientPreferences);
		when(clientPreferences.isWorkspaceSymbolDynamicRegistered()).thenReturn(Boolean.FALSE);
		when(clientPreferences.isDocumentSymbolDynamicRegistered()).thenReturn(Boolean.FALSE);
		when(clientPreferences.isCodeActionDynamicRegistered()).thenReturn(Boolean.FALSE);
		when(clientPreferences.isDefinitionDynamicRegistered()).thenReturn(Boolean.FALSE);
		when(clientPreferences.isHoverDynamicRegistered()).thenReturn(Boolean.FALSE);
		when(clientPreferences.isReferencesDynamicRegistered()).thenReturn(Boolean.FALSE);
		when(clientPreferences.isDocumentHighlightDynamicRegistered()).thenReturn(Boolean.FALSE);
		projManager.setAutoBuilding(true);
		server = new JDTLanguageServer(projManager, prefManager);
		server.connectClient(client);
	}

	@After
	public void tearDown() {
		server.disconnectClient();
	}

	@Test
	public void testAutobuilding() throws Exception {
		boolean enabled = isAutoBuilding();
		try {
			assertTrue("Autobuilding is off", isAutoBuilding());
			Map<String, Object> map = new HashMap<>();
			map.put(Preferences.AUTOBUILD_ENABLED_KEY, false);
			DidChangeConfigurationParams params = new DidChangeConfigurationParams(map);
			server.didChangeConfiguration(params);
			assertFalse("Autobuilding is on", isAutoBuilding());
		} finally {
			projManager.setAutoBuilding(enabled);
		}
	}

	private boolean isAutoBuilding() throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();
		return description.isAutoBuilding();
	}

	@Test
	public void testRegisterDynamicCapabilities() throws Exception {
		setDynamicCapabilities(true);

		Map<String, Object> map = new HashMap<>();
		map.put(Preferences.REFERENCES_CODE_LENS_ENABLED_KEY, true);
		map.put(Preferences.JAVA_FORMAT_ENABLED_KEY, true);
		map.put(Preferences.SIGNATURE_HELP_ENABLED_KEY, true);
		map.put(Preferences.EXECUTE_COMMAND_ENABLED_KEY, true);
		map.put(Preferences.JAVA_FORMAT_ON_TYPE_ENABLED_KEY, true);
		DidChangeConfigurationParams params = new DidChangeConfigurationParams(map);

		server.didChangeConfiguration(params);
		verify(client, times(6)).registerCapability(any());

		//On 2nd call, no registration call should be emitted
		reset(client);
		server.didChangeConfiguration(params);
		verify(client, never()).registerCapability(any());

		// unregister capabilities
		reset(client);
		map.put(Preferences.REFERENCES_CODE_LENS_ENABLED_KEY, false);
		map.put(Preferences.JAVA_FORMAT_ENABLED_KEY, false);
		map.put(Preferences.SIGNATURE_HELP_ENABLED_KEY, false);
		map.put(Preferences.EXECUTE_COMMAND_ENABLED_KEY, false);
		map.put(Preferences.JAVA_FORMAT_ON_TYPE_ENABLED_KEY, false);
		params = new DidChangeConfigurationParams(map);

		server.didChangeConfiguration(params);
		verify(client, times(6)).unregisterCapability(any());

		//On 2nd call, no unregistration calls should be emitted
		reset(client);
		server.didChangeConfiguration(params);
		verify(client, never()).unregisterCapability(any());
	}

	@Test
	public void testNoDynamicCapabilities() throws Exception {
		setDynamicCapabilities(false);

		Map<String, Object> map = new HashMap<>();
		map.put(Preferences.REFERENCES_CODE_LENS_ENABLED_KEY, true);
		map.put(Preferences.JAVA_FORMAT_ENABLED_KEY, true);
		map.put(Preferences.SIGNATURE_HELP_ENABLED_KEY, true);
		map.put(Preferences.EXECUTE_COMMAND_ENABLED_KEY, true);
		DidChangeConfigurationParams params = new DidChangeConfigurationParams(map);

		server.didChangeConfiguration(params);
		verify(client, never()).registerCapability(any());

		// unregister capabilities
		map.put(Preferences.REFERENCES_CODE_LENS_ENABLED_KEY, false);
		map.put(Preferences.JAVA_FORMAT_ENABLED_KEY, false);
		map.put(Preferences.SIGNATURE_HELP_ENABLED_KEY, false);
		map.put(Preferences.EXECUTE_COMMAND_ENABLED_KEY, false);
		params = new DidChangeConfigurationParams(map);

		server.didChangeConfiguration(params);
		verify(client, never()).unregisterCapability(any());
	}

	private void setDynamicCapabilities(boolean enable) {
		when(clientPreferences.isCodeLensDynamicRegistrationSupported()).thenReturn(enable);
		when(clientPreferences.isFormattingDynamicRegistrationSupported()).thenReturn(enable);
		when(clientPreferences.isRangeFormattingDynamicRegistrationSupported()).thenReturn(enable);
		when(clientPreferences.isSignatureHelpDynamicRegistrationSupported()).thenReturn(enable);
		when(clientPreferences.isExecuteCommandDynamicRegistrationSupported()).thenReturn(enable);
		when(clientPreferences.isOnTypeFormattingDynamicRegistrationSupported()).thenReturn(enable);
	}

}
