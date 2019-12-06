/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.DisassemblerContentProvider;
import org.eclipse.jdt.ls.core.internal.FakeContentProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

// Note: this test depends on the contentProvider extensions configured in plugin.xml

public class ContentProviderManagerTest extends AbstractProjectsManagerBasedTest {

	private static final String FAKE_DECOMPILED_SOURCE = "This is decompiled";

	private URI sourcelessURI;
	private IClassFile sourcelessClassFile;
	private URI sourceAvailableURI;
	private IClassFile sourceAvailableClassFile;
	private PreferenceManager preferenceManager;
	private Preferences preferences;
	private ContentProviderManager provider;

	@Before
	public void createURIs() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		sourcelessURI = JDTUtils.toURI(ClassFileUtil.getURI(project, "java.math.BigDecimal"));
		sourcelessClassFile = JDTUtils.resolveClassFile(sourcelessURI);
		sourceAvailableURI = JDTUtils.toURI(ClassFileUtil.getURI(project, "org.apache.commons.lang3.text.WordUtils"));
		sourceAvailableClassFile = JDTUtils.resolveClassFile(sourceAvailableURI);
	}

	@Before
	public void buildContentProviderManager() {
		preferenceManager = mock(PreferenceManager.class);
		preferences = mock(Preferences.class);
		when(preferences.getPreferredContentProviderIds()).thenReturn(null);
		when(preferenceManager.getPreferences()).thenReturn(preferences);
		provider = new ContentProviderManager(preferenceManager);
	}

	@Before
	@After
	public void resetFakeContentProvider() {
		FakeContentProvider.preferences = null;
		FakeContentProvider.returnValue = null;
	}

	@Before
	public void resetMonitor() {
		monitor.setCanceled(false);
	}

	// begin tests:

	@Test
	public void testOpenSourceCode() throws Exception {
		String result = provider.getContent(sourceAvailableURI, monitor);
		assertNotNull(result);
		assertTrue("unexpected body content " + result, result.contains("Operations on Strings that contain words."));
	}

	@Test
	public void testDecompileSourceCode() throws Exception {
		String result = provider.getSource(sourceAvailableClassFile, monitor);
		assertNotNull(result);
		assertTrue("unexpected body content " + result, result.contains("Operations on Strings that contain words."));
	}

	@Test
	public void testOpenMissingFile() throws Exception {
		URI noSuchURI = JDTUtils.toURI("file://this/is/Missing.class");

		String result = provider.getContent(noSuchURI, monitor);
		assertNotNull(result);
		assertTrue("not empty: " + result, result.isEmpty());
	}

	@Test
	public void testOpenThingy() throws Exception {
		FakeContentProvider.returnValue = FAKE_DECOMPILED_SOURCE;
		URI noSuchURI = JDTUtils.toURI("file://this/is/Some.thingy");

		assertEquals(FAKE_DECOMPILED_SOURCE, provider.getContent(noSuchURI, monitor));
	}

	@Test
	public void testOpenNothing() throws Exception {
		String result = provider.getContent(null, monitor);
		assertNull(result);
	}

	@Test
	public void testDecompileNothing() throws Exception {
		String result = provider.getSource(null, monitor);
		assertNull(result);
	}

	@Test
	public void testThrowsException() throws Exception {
		FakeContentProvider.returnValue = new Exception("Something bad happened here");

		String result = provider.getContent(sourcelessURI, monitor);

		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.startsWith(DisassemblerContentProvider.DISASSEMBLED_HEADER));
		expectLoggedError("Something bad happened here");
	}

	@Test
	public void testDecompileThrowsException() throws Exception {
		FakeContentProvider.returnValue = new Exception("Something bad happened here");

		String result = provider.getSource(sourcelessClassFile, monitor);

		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.startsWith(DisassemblerContentProvider.DISASSEMBLED_HEADER));
		expectLoggedError("Something bad happened here");
	}

	@Test
	public void testDefaultOrder() throws Exception {
		String result = provider.getContent(sourcelessURI, monitor);
		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.startsWith(DisassemblerContentProvider.DISASSEMBLED_HEADER));
		expectLoggedError("You have more than one content provider installed:");
	}

	@Test
	public void testDecompileDefaultOrder() throws Exception {
		String result = provider.getSource(sourcelessClassFile, monitor);
		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.startsWith(DisassemblerContentProvider.DISASSEMBLED_HEADER));
		expectLoggedError("You have more than one content provider installed:");
	}

	@Test
	public void testPreferExistingProviderClass() {
		FakeContentProvider.returnValue = FAKE_DECOMPILED_SOURCE;
		when(preferences.getPreferredContentProviderIds()).thenReturn(Arrays.asList("fakeContentProvider", "placeholderContentProvider"));

		assertEquals(FAKE_DECOMPILED_SOURCE, provider.getContent(sourcelessURI, monitor));
		assertTrue(logListener.getErrors().toString(), logListener.getErrors().isEmpty());
	}

	@Test
	public void testDecompilePreferExistingProviderClass() {
		FakeContentProvider.returnValue = FAKE_DECOMPILED_SOURCE;
		when(preferences.getPreferredContentProviderIds()).thenReturn(Arrays.asList("fakeContentProvider", "placeholderContentProvider"));

		assertEquals(FAKE_DECOMPILED_SOURCE, provider.getSource(sourcelessClassFile, monitor));
		assertTrue(logListener.getErrors().toString(), logListener.getErrors().isEmpty());
	}

	@Test
	public void testPreferNonexistingProviderClass() {
		when(preferences.getPreferredContentProviderIds()).thenReturn(Arrays.asList("placeholderContentProvider"));

		String result = provider.getContent(sourcelessURI, monitor);
		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.startsWith(DisassemblerContentProvider.DISASSEMBLED_HEADER));
		expectLoggedError("Unable to load IContentProvider class for placeholderContentProvider");
	}

	@Test
	public void testDecompilePreferNonexistingProviderClass() {
		when(preferences.getPreferredContentProviderIds()).thenReturn(Arrays.asList("placeholderContentProvider"));

		String result = provider.getSource(sourcelessClassFile, monitor);
		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.startsWith(DisassemblerContentProvider.DISASSEMBLED_HEADER));
		expectLoggedError("Unable to load IDecompiler class for placeholderContentProvider");
	}

	@Test
	public void testPreferUnknownExtension() {
		when(preferences.getPreferredContentProviderIds()).thenReturn(Arrays.asList("unknownContentProvider"));

		String result = provider.getContent(sourcelessURI, monitor);
		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.startsWith(DisassemblerContentProvider.DISASSEMBLED_HEADER));
	}

	@Test
	public void testPreferDisassembler() throws Exception {
		when(preferences.getPreferredContentProviderIds()).thenReturn(Arrays.asList("disassemblerContentProvider"));

		String result = provider.getContent(sourcelessURI, monitor);
		assertNotNull(result);
		assertTrue("disassembler header is missing from " + result, result.startsWith(DisassemblerContentProvider.DISASSEMBLED_HEADER));
		assertTrue("unexpected body content " + result, result.contains("public class BigDecimal extends java.lang.Number implements java.lang.Comparable {"));
	}

	@Test
	public void testCancelMonitor() {
		FakeContentProvider.returnValue = monitor;

		String result = provider.getContent(sourcelessURI, monitor);
		assertTrue(monitor.isCanceled());
		assertNotNull(result);
		assertTrue("not empty", result.isEmpty());
	}

	@Test
	public void testExpectPreferences() {
		provider.getContent(sourcelessURI, monitor);
		assertEquals("preferences not set", preferences, FakeContentProvider.preferences);
	}

	@Test
	public void testNoCaching() {
		FakeContentProvider.returnValue = "some value";
		assertEquals(FakeContentProvider.returnValue, provider.getContent(sourcelessURI, monitor));

		FakeContentProvider.returnValue = "something else";
		assertEquals(FakeContentProvider.returnValue, provider.getContent(sourcelessURI, monitor));
	}

	private void expectLoggedError(String expected) {
		assertTrue("expected error " + expected, logListener.getErrors().stream().filter(e -> e.contains(expected)).findAny().isPresent());
	}
}
