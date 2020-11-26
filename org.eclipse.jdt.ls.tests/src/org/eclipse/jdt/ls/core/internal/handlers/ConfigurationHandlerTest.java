/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.junit.Test;

public class ConfigurationHandlerTest extends AbstractProjectsManagerBasedTest {

    @Test
    public void testGetConfigurationWhenItIsNotSupported() {
        ClientPreferences clientPreferences = mock(ClientPreferences.class);
        when(clientPreferences.isWorkspaceConfigurationSupported()).thenReturn(false);
        when(preferenceManager.getClientPreferences()).thenReturn(clientPreferences);

        assertNull(ConfigurationHandler.getFormattingOptions("fakeUri"));
    }
}
