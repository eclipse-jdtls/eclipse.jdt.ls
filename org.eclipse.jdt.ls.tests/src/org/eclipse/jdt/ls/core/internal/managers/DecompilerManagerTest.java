/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.FakeDecompiler;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.junit.Before;
import org.junit.Test;

public class DecompilerManagerTest extends AbstractProjectsManagerBasedTest {

	private IClassFile classFile;
	private PreferenceManager preferenceManager;
	private Preferences preferences;
	private DecompilerManager handler;

	@Before
	public void createClassFile() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		String uri = ClassFileUtil.getURI(project, "java.math.BigDecimal");
		classFile = JDTUtils.resolveClassFile(uri);
	}

	@Before
	public void buildDecompilerHandler() {
		preferences = mock(Preferences.class);
		preferenceManager = new PreferenceManager();
		preferenceManager.update(preferences);
		handler = new DecompilerManager(preferenceManager);
	}

	@Test
	public void testDecompile() {
		when(preferences.getDecompilerId()).thenReturn("fakeDecompiler");

		String result = handler.decompile(classFile, monitor);
		assertNotNull(result);
		assertTrue("header is missing from " + result, result.startsWith(String.format(DecompilerManager.DECOMPILED_HEADER, "Fake decompiler")));
		assertTrue("decompiled output is missing from " + result, result.endsWith(FakeDecompiler.DECOMPILED_CODE));
	}

	@Test
	public void testDecompileNonexistingDecompiler() {
		when(preferences.getDecompilerId()).thenReturn("placeholderDecompiler");

		String result = handler.decompile(classFile, monitor);
		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.contains(DecompilerManager.DISASSEMBLER_HEADER_ADDITION));
	}

	@Test
	public void testDecompileUnknownDecompiler() {
		when(preferences.getDecompilerId()).thenReturn("noDecompiler");

		String result = handler.decompile(classFile, monitor);
		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.contains(DecompilerManager.DISASSEMBLER_HEADER_ADDITION));
	}

	@Test
	public void testDisassembler() throws Exception {
		when(preferences.getDecompilerId()).thenReturn(Preferences.DECOMPILER_ID_DEFAULT);

		String result = handler.decompile(classFile, monitor);
		assertNotNull(result);
		assertTrue("header is missing from " + result, result.startsWith(String.format(DecompilerManager.DECOMPILED_HEADER, Preferences.DECOMPILER_ID_DEFAULT)));
		assertTrue("disassembler header is missing from " + result, result.contains(DecompilerManager.DISASSEMBLER_HEADER_ADDITION));
	}
}
