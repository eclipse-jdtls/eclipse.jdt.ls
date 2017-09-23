/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.junit.Before;
import org.junit.Test;

public class DecompileCommandHandlerTest extends AbstractProjectsManagerBasedTest {

	private IClassFile classFile;
	private PreferenceManager preferenceManager;
	private Preferences preferences;
	private DecompileCommandHandler handler;

	@Before
	public void createClassFile() {
		classFile = mock(IClassFile.class);
	}

	@Before
	public void buildDecompilerHandler() {
		preferences = mock(Preferences.class);
		preferenceManager = new PreferenceManager();
		preferenceManager.update(preferences);
		handler = new DecompileCommandHandler(preferenceManager);
	}

	@Test
	public void testDecompile() {
		when(preferences.getDecompilerId()).thenReturn("testDecompiler");

		String result = handler.decompile(classFile, monitor);
		assertEquals("This is decompiled", result);
	}

	@Test
	public void testDecompileNonexistingDecompiler() {
		when(preferences.getDecompilerId()).thenReturn("placeholderDecompiler");

		String result = handler.decompile(classFile, monitor);
		assertNull(result);
	}

	@Test
	public void testDecompileUnknownDecompiler() {
		when(preferences.getDecompilerId()).thenReturn("noDecompiler");

		String result = handler.decompile(classFile, monitor);
		assertNull(result);
	}

	@Test
	public void testGetTestDecompilerName() {
		when(preferences.getDecompilerId()).thenReturn("testDecompiler");

		assertEquals("Test Decompiler", handler.getDecompilerName());
	}

	@Test
	public void testGetPlaceholderDecompilerIdAsName() {
		when(preferences.getDecompilerId()).thenReturn("placeholderDecompiler");

		assertEquals("placeholderDecompiler", handler.getDecompilerName());
	}
}
