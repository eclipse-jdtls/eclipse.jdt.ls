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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Fred Bricon
 */
public class ClassfileContentHandlerTest extends AbstractProjectsManagerBasedTest {

	private IProject project;
	private ClassfileContentHandler handler;

	@Before
	public void setup() throws Exception {
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		handler = new ClassfileContentHandler();
	}

	@Test
	public void testOpenClassFile() throws Exception {
		String uri = ClassFileUtil.getURI(project, "org.apache.commons.lang3.text.WordUtils");
		String source = getSource(uri);
		assertNotNull(source);
		assertFalse("header should be missing from " + source, source.startsWith(JDTUtils.MISSING_SOURCES_HEADER));
		assertTrue("unexpected body content "+source, source.contains("Operations on Strings that contain words."));
	}

	@Test
	public void testOpenSourceLessClassFile() throws Exception {
		String uri = ClassFileUtil.getURI(project, "java.math.BigDecimal");
		String source = getSource(uri);
		assertNotNull(source);
		assertTrue("header is missing from " + source, source.startsWith(JDTUtils.MISSING_SOURCES_HEADER));
		assertTrue("unexpected body content " + source, source.contains("package java.math;"));
		assertTrue("unexpected body content " + source, source.contains("public class BigDecimal extends java.lang.Number implements java.lang.Comparable {"));
	}


	@Test
	public void testOpenMissingFile() throws Exception {
		String uri = "file://this/is/Missing.class";
		String source = getSource(uri);
		assertNotNull(source);
		assertTrue(source.isEmpty());
	}

	private String getSource(String uri) throws Exception {
		TextDocumentIdentifier param = new TextDocumentIdentifier(uri);
		return handler.contents(param, monitor);
	}

}
