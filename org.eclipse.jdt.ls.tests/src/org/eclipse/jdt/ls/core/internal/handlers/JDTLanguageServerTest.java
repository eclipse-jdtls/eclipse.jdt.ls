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


import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
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
	public void setUp() {
		when(prefManager.getClientPreferences()).thenReturn(clientPreferences);
		server = new JDTLanguageServer(projManager, prefManager);
		server.connectClient(client);
	}

	@Test
	public void testRegisterDynamicCapabilities() throws Exception {
		setDynamicCapabilities(true);

		Map<String, Object> map = new HashMap<>();
		map.put(Preferences.REFERENCES_CODE_LENS_ENABLED_KEY, true);
		map.put(Preferences.JAVA_FORMAT_ENABLED_KEY, true);
		DidChangeConfigurationParams params = new DidChangeConfigurationParams(map);

		server.didChangeConfiguration(params);
		verify(client, times(3)).registerCapability(any());

		//On 2nd call, no registration calls should be emitted
		reset(client);
		server.didChangeConfiguration(params);
		verify(client, never()).registerCapability(any());

		// unregister capabilities
		reset(client);
		map.put(Preferences.REFERENCES_CODE_LENS_ENABLED_KEY, false);
		map.put(Preferences.JAVA_FORMAT_ENABLED_KEY, false);
		params = new DidChangeConfigurationParams(map);

		server.didChangeConfiguration(params);
		verify(client, times(3)).unregisterCapability(any());

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
		DidChangeConfigurationParams params = new DidChangeConfigurationParams(map);

		server.didChangeConfiguration(params);
		verify(client, never()).registerCapability(any());

		// unregister capabilities
		map.put(Preferences.REFERENCES_CODE_LENS_ENABLED_KEY, false);
		map.put(Preferences.JAVA_FORMAT_ENABLED_KEY, false);
		params = new DidChangeConfigurationParams(map);

		server.didChangeConfiguration(params);
		verify(client, never()).unregisterCapability(any());
	}

	private void setDynamicCapabilities(boolean enable) {
		when(clientPreferences.isCodeLensDynamicRegistrationSupported()).thenReturn(enable);
		when(clientPreferences.isFormattingDynamicRegistrationSupported()).thenReturn(enable);
		when(clientPreferences.isRangeFormattingDynamicRegistrationSupported()).thenReturn(enable);
	}

}
